package net.pingpoint;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.pingpoint.network.PingC2SPayload;
import net.pingpoint.network.PingPayload;
import net.pingpoint.network.PingS2CPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PingPointMod implements ModInitializer {
    public static final String MOD_ID = "pingpoint";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final long COOLDOWN_MS = 500L;
    public static final double MAX_REACH_DISTANCE = 135.0;
    public static final double BROADCAST_RADIUS = 128.0;
    public static final int MAX_PINGS_PER_PLAYER = 2;
    public static final int SERVER_PING_LIFETIME = 120; // 6 seconds

    private record ServerPingEntry(UUID pingId, UUID authorUuid, ServerWorld world, double x, double y, double z, int[] ticksRemaining) {}

    private final Map<UUID, Long> playerLastPingTime = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<UUID>> playerActivePings = new ConcurrentHashMap<>();
    private final Map<UUID, ServerPingEntry> activeServerPings = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        LOGGER.info("[PingPoint] Initializing PingPoint mod...");

        // Register custom networking payloads (C2S and S2C)
        PingPayload.registerAll();

        // Register server C2S packet receiver
        ServerPlayNetworking.registerGlobalReceiver(PingC2SPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> handlePingRequest(player, payload));
        });

        // Server tick: manage ping expiration
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (activeServerPings.isEmpty()) return;

            Iterator<Map.Entry<UUID, ServerPingEntry>> it = activeServerPings.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, ServerPingEntry> entry = it.next();
                ServerPingEntry ping = entry.getValue();
                ping.ticksRemaining()[0]--;

                if (ping.ticksRemaining()[0] <= 0) {
                    it.remove();
                    Deque<UUID> queue = playerActivePings.get(ping.authorUuid());
                    if (queue != null) {
                        queue.remove(ping.pingId());
                        if (queue.isEmpty()) {
                            playerActivePings.remove(ping.authorUuid());
                        }
                    }
                    broadcastRemovePing(ping.world(), ping.pingId(), ping.x(), ping.y(), ping.z());
                }
            }
        });

        // Clean up when a player leaves the server
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            UUID playerUuid = player.getUuid();
            playerLastPingTime.remove(playerUuid);

            Deque<UUID> pings = playerActivePings.remove(playerUuid);
            if (pings != null) {
                for (UUID pingId : pings) {
                    ServerPingEntry ping = activeServerPings.remove(pingId);
                    if (ping != null) {
                        broadcastRemovePing(ping.world(), ping.pingId(), ping.x(), ping.y(), ping.z());
                    }
                }
            }
        });

        LOGGER.info("[PingPoint] PingPoint initialized successfully!");
    }

    private void handlePingRequest(ServerPlayerEntity player, PingC2SPayload payload) {
        long now = System.currentTimeMillis();
        long lastTime = playerLastPingTime.getOrDefault(player.getUuid(), 0L);

        // Anti-spam cooldown check (0.5 seconds)
        if (now - lastTime < COOLDOWN_MS) {
            return;
        }

        // Distance validation to prevent malicious remote packet injection
        double distSq = player.squaredDistanceTo(payload.x(), payload.y(), payload.z());
        if (distSq > MAX_REACH_DISTANCE * MAX_REACH_DISTANCE) {
            return;
        }

        playerLastPingTime.put(player.getUuid(), now);
        ServerWorld world = player.getServerWorld();

        // Limit to max 2 active pings per player (remove oldest if 3rd is added)
        Deque<UUID> pings = playerActivePings.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>());
        while (pings.size() >= MAX_PINGS_PER_PLAYER) {
            UUID oldestId = pings.pollFirst();
            if (oldestId != null) {
                ServerPingEntry removed = activeServerPings.remove(oldestId);
                if (removed != null) {
                    broadcastRemovePing(world, oldestId, removed.x(), removed.y(), removed.z());
                }
            }
        }

        UUID newPingId = UUID.randomUUID();
        pings.addLast(newPingId);

        activeServerPings.put(
            newPingId,
            new ServerPingEntry(newPingId, player.getUuid(), world, payload.x(), payload.y(), payload.z(), new int[]{SERVER_PING_LIFETIME})
        );

        PingS2CPayload addPayload = PingS2CPayload.add(
            newPingId,
            player.getUuid(),
            player.getName().getString(),
            payload.type(),
            payload.x(),
            payload.y(),
            payload.z(),
            payload.entityId(),
            payload.entityUuid()
        );

        // Broadcast ONLY to nearby players with the PingPoint mod installed
        broadcastAddPing(world, payload.x(), payload.y(), payload.z(), addPayload);
    }

    /**
     * Broadcasts an ADD payload to all players within 128 blocks who have PingPoint installed.
     * Vanilla clients are strictly filtered out and never receive custom payloads.
     */
    private void broadcastAddPing(ServerWorld world, double x, double y, double z, PingS2CPayload payload) {
        double maxDistSq = BROADCAST_RADIUS * BROADCAST_RADIUS;
        for (ServerPlayerEntity target : world.getPlayers()) {
            if (target.squaredDistanceTo(x, y, z) <= maxDistSq) {
                // Strict isolation check: vanilla clients do not have this channel registered!
                if (ServerPlayNetworking.canSend(target, PingS2CPayload.ID)) {
                    ServerPlayNetworking.send(target, payload);
                }
            }
        }
    }

    /**
     * Broadcasts a REMOVE payload to all players within 128 blocks who have PingPoint installed.
     */
    private void broadcastRemovePing(ServerWorld world, UUID pingId, double x, double y, double z) {
        PingS2CPayload removePayload = PingS2CPayload.remove(pingId);
        double maxDistSq = BROADCAST_RADIUS * BROADCAST_RADIUS;
        for (ServerPlayerEntity target : world.getPlayers()) {
            if (target.squaredDistanceTo(x, y, z) <= maxDistSq) {
                if (ServerPlayNetworking.canSend(target, PingS2CPayload.ID)) {
                    ServerPlayNetworking.send(target, removePayload);
                }
            }
        }
    }
}

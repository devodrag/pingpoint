package net.pingpoint.manager;

import net.pingpoint.network.PingS2CPayload;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PingManager {
    private static final PingManager INSTANCE = new PingManager();

    public static final int MAX_PINGS_PER_PLAYER = 2;

    private final Map<UUID, Ping> activePings = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<UUID>> authorPings = new ConcurrentHashMap<>();

    private PingManager() {
    }

    public static PingManager getInstance() {
        return INSTANCE;
    }

    /**
     * Adds a new ping from an S2C payload, ensuring a maximum of 2 active pings per player.
     * If a third ping is added, the oldest ping from that player is immediately removed.
     */
    public synchronized void addPing(PingS2CPayload payload) {
        UUID authorUuid = payload.authorUuid();
        Deque<UUID> playerQueue = authorPings.computeIfAbsent(authorUuid, k -> new ArrayDeque<>());

        while (playerQueue.size() >= MAX_PINGS_PER_PLAYER) {
            UUID oldestId = playerQueue.pollFirst();
            if (oldestId != null) {
                activePings.remove(oldestId);
            }
        }

        Ping ping = Ping.fromPayload(payload);
        activePings.put(ping.getId(), ping);
        playerQueue.addLast(ping.getId());
    }

    /**
     * Removes an active ping by its unique ID.
     */
    public synchronized void removePing(UUID pingId) {
        Ping removed = activePings.remove(pingId);
        if (removed != null) {
            Deque<UUID> queue = authorPings.get(removed.getAuthorUuid());
            if (queue != null) {
                queue.remove(pingId);
                if (queue.isEmpty()) {
                    authorPings.remove(removed.getAuthorUuid());
                }
            }
        }
    }

    /**
     * Updates all active pings every client tick and prunes expired ones.
     */
    public synchronized void tick() {
        Iterator<Map.Entry<UUID, Ping>> it = activePings.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Ping> entry = it.next();
            Ping ping = entry.getValue();
            ping.tick();
            if (ping.isExpired()) {
                it.remove();
                Deque<UUID> queue = authorPings.get(ping.getAuthorUuid());
                if (queue != null) {
                    queue.remove(ping.getId());
                    if (queue.isEmpty()) {
                        authorPings.remove(ping.getAuthorUuid());
                    }
                }
            }
        }
    }

    public synchronized List<Ping> getActivePings() {
        return new ArrayList<>(activePings.values());
    }

    public synchronized void clear() {
        activePings.clear();
        authorPings.clear();
    }
}

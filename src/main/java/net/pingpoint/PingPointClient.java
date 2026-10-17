package net.pingpoint;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.pingpoint.gui.PingRadialScreen;
import net.pingpoint.manager.PingManager;
import net.pingpoint.network.PingC2SPayload;
import net.pingpoint.network.PingS2CPayload;
import net.pingpoint.network.PingType;
import net.pingpoint.render.PingRenderer;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class PingPointClient implements ClientModInitializer {
    public static final String KEY_CATEGORY = "category.pingpoint";
    public static final String KEY_PING = "key.pingpoint.ping";

    private static KeyBinding pingKey;

    private static boolean isKeyHeld = false;
    private static long keyPressStartTime = 0L;
    private static long lastKeyReleaseTime = 0L;
    private static boolean radialOpened = false;
    private static boolean doubleClickFired = false;

    private static long lastClientPingTime = 0L;

    @Override
    public void onInitializeClient() {
        // Register keybind: default 'V'
        pingKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            KEY_PING,
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            KEY_CATEGORY
        ));

        // Register S2C packet listener
        ClientPlayNetworking.registerGlobalReceiver(PingS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.action() == PingS2CPayload.Action.ADD) {
                    PingManager.getInstance().addPing(payload);
                    playSpatialPingSound(payload);
                } else if (payload.action() == PingS2CPayload.Action.REMOVE) {
                    PingManager.getInstance().removePing(payload.pingId());
                }
            });
        });

        // Reset pings on disconnect or server leave
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PingManager.getInstance().clear();
            resetKeyStates();
        });

        // Client tick: ping decay and input state machine
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PingManager.getInstance().tick();

            if (client.player == null || client.world == null) {
                resetKeyStates();
                return;
            }

            handleInput(client);
        });

        // World rendering (3D billboard and floor ring)
        WorldRenderEvents.AFTER_ENTITIES.register(PingRenderer::renderWorld);

        // 2D HUD rendering (off-screen indicators)
        HudRenderCallback.EVENT.register(PingRenderer::renderHud);
    }

    private static void resetKeyStates() {
        isKeyHeld = false;
        radialOpened = false;
        doubleClickFired = false;
        keyPressStartTime = 0L;
    }

    private static void handleInput(MinecraftClient client) {
        // If any screen is open, ignore in-game key handling
        if (client.currentScreen != null) {
            isKeyHeld = false;
            return;
        }

        long now = System.currentTimeMillis();

        if (pingKey.isPressed()) {
            if (!isKeyHeld) {
                // Key pressed down this tick
                isKeyHeld = true;
                keyPressStartTime = now;
                radialOpened = false;

                // Double click detection (within 300 ms)
                if (now - lastKeyReleaseTime < 300L) {
                    triggerPing(PingType.DANGER);
                    doubleClickFired = true;
                    lastKeyReleaseTime = 0L;
                } else {
                    doubleClickFired = false;
                }
            } else {
                // Key is held down: check if held for >= 200 ms
                if (!radialOpened && !doubleClickFired && (now - keyPressStartTime >= 200L)) {
                    radialOpened = true;
                    client.setScreen(new PingRadialScreen());
                }
            }
        } else {
            if (isKeyHeld) {
                // Key released
                isKeyHeld = false;
                lastKeyReleaseTime = now;

                if (!radialOpened && !doubleClickFired) {
                    // Quick single click: contextual ping
                    triggerContextualPing();
                }
            }
        }
    }

    /**
     * Contextual ping:
     * - Aiming at a block: ATTENTION (Cyan)
     * - Aiming at an entity: DANGER (Red, bound to entity)
     */
    public static void triggerContextualPing() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        Entity cameraEntity = client.getCameraEntity() != null ? client.getCameraEntity() : client.player;
        RaycastResult result = performRaycast(cameraEntity, 128.0);

        PingType type = (result.entity() != null) ? PingType.DANGER : PingType.ATTENTION;
        sendPing(type, result);
    }

    /**
     * Forced ping type (used by double-click or Radial Menu).
     */
    public static void triggerPing(PingType type) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        Entity cameraEntity = client.getCameraEntity() != null ? client.getCameraEntity() : client.player;
        RaycastResult result = performRaycast(cameraEntity, 128.0);
        sendPing(type, result);
    }

    private static void sendPing(PingType type, RaycastResult hit) {
        long now = System.currentTimeMillis();
        // Client-side 0.5s anti-spam cooldown
        if (now - lastClientPingTime < 500L) {
            return;
        }
        lastClientPingTime = now;

        int entityId = hit.entity() != null ? hit.entity().getId() : -1;
        Optional<UUID> entityUuid = hit.entity() != null ? Optional.of(hit.entity().getUuid()) : Optional.empty();

        PingC2SPayload payload = new PingC2SPayload(
            type,
            hit.pos().x,
            hit.pos().y,
            hit.pos().z,
            entityId,
            entityUuid
        );

        ClientPlayNetworking.send(payload);
    }

    private static void playSpatialPingSound(PingS2CPayload payload) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        boolean isAlert = payload.type() == PingType.DANGER || payload.type() == PingType.CAUTION;
        SoundEvent sound = isAlert
            ? SoundEvents.BLOCK_NOTE_BLOCK_BELL.value()
            : SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value();

        float pitch = switch (payload.type()) {
            case DANGER -> 0.85f;
            case CAUTION -> 1.15f;
            case LOOT -> 1.4f;
            case MINE -> 1.25f;
            default -> 1.0f;
        };

        client.world.playSound(
            payload.x(),
            payload.y(),
            payload.z(),
            sound,
            SoundCategory.PLAYERS,
            1.2f,
            pitch,
            false
        );
    }

    public record RaycastResult(Vec3d pos, Entity entity, BlockHitResult blockHit) {}

    public static RaycastResult performRaycast(Entity cameraEntity, double maxDistance) {
        MinecraftClient client = MinecraftClient.getInstance();
        float tickDelta = 1.0f;
        Vec3d eyePos = cameraEntity.getCameraPosVec(tickDelta);
        Vec3d rot = cameraEntity.getRotationVec(tickDelta);
        Vec3d reachVec = eyePos.add(rot.multiply(maxDistance));

        // 1. Entity raycast up to maxDistance
        Box box = cameraEntity.getBoundingBox().stretch(rot.multiply(maxDistance)).expand(1.0);
        EntityHitResult entityHit = ProjectileUtil.raycast(
            cameraEntity,
            eyePos,
            reachVec,
            box,
            e -> !e.isSpectator() && e.isAlive() && e != client.player,
            maxDistance * maxDistance
        );

        // 2. Block raycast up to maxDistance
        BlockHitResult blockHit = cameraEntity.getWorld().raycast(new RaycastContext(
            eyePos,
            reachVec,
            RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE,
            cameraEntity
        ));

        double entityDistSq = (entityHit != null) ? eyePos.squaredDistanceTo(entityHit.getPos()) : Double.MAX_VALUE;
        double blockDistSq = (blockHit != null && blockHit.getType() != HitResult.Type.MISS)
            ? eyePos.squaredDistanceTo(blockHit.getPos()) : Double.MAX_VALUE;

        // Choose whichever is closer
        if (entityDistSq < blockDistSq && entityHit != null) {
            Entity target = entityHit.getEntity();
            Vec3d targetPos = new Vec3d(target.getX(), target.getY() + target.getHeight() + 0.35, target.getZ());
            return new RaycastResult(targetPos, target, blockHit);
        } else if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
            Vec3d hitPos = blockHit.getPos();
            return new RaycastResult(hitPos, null, blockHit);
        } else {
            return new RaycastResult(reachVec, null, null);
        }
    }

    public static KeyBinding getPingKey() {
        return pingKey;
    }
}

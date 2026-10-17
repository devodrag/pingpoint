package net.pingpoint.manager;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.pingpoint.network.PingS2CPayload;
import net.pingpoint.network.PingType;

import java.util.Optional;
import java.util.UUID;

public class Ping {
    public static final int MAX_AGE_TICKS = 120; // 6 seconds
    public static final int FADE_TICKS = 20;     // 1 second fade-out

    private final UUID id;
    private final UUID authorUuid;
    private final String authorName;
    private final PingType type;
    private double x;
    private double y;
    private double z;
    private final int entityId;
    private final Optional<UUID> entityUuid;

    private int age;

    public Ping(
        UUID id,
        UUID authorUuid,
        String authorName,
        PingType type,
        double x,
        double y,
        double z,
        int entityId,
        Optional<UUID> entityUuid
    ) {
        this.id = id;
        this.authorUuid = authorUuid;
        this.authorName = authorName;
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.entityId = entityId;
        this.entityUuid = entityUuid;
        this.age = 0;
    }

    public static Ping fromPayload(PingS2CPayload payload) {
        return new Ping(
            payload.pingId(),
            payload.authorUuid(),
            payload.authorName(),
            payload.type(),
            payload.x(),
            payload.y(),
            payload.z(),
            payload.entityId(),
            payload.entityUuid()
        );
    }

    public void tick() {
        this.age++;
    }

    public boolean isExpired() {
        return this.age >= MAX_AGE_TICKS;
    }

    public float getRemainingTicks(float tickDelta) {
        return Math.max(0.0f, (float) MAX_AGE_TICKS - ((float) this.age + tickDelta));
    }

    public float getAlpha(float tickDelta) {
        float remaining = getRemainingTicks(tickDelta);
        if (remaining < FADE_TICKS) {
            return MathHelper.clamp(remaining / (float) FADE_TICKS, 0.0f, 1.0f);
        }
        return 1.0f;
    }

    /**
     * Calculates the scale factor combining the pop-in spring animation (0.0 -> 1.25 -> 1.0 in first 4 ticks)
     * and the shrink on fade-out in the last 20 ticks.
     */
    public float getScaleMultiplier(float tickDelta) {
        float currentAge = (float) this.age + tickDelta;
        float popScale = 1.0f;

        if (currentAge < 4.0f) {
            float progress = MathHelper.clamp(currentAge / 4.0f, 0.0f, 1.0f);
            // Elastic spring overshoot formula: reaches ~1.25 before settling back to 1.0
            popScale = progress + 0.25f * (float) Math.sin(progress * Math.PI);
        }

        float remaining = getRemainingTicks(tickDelta);
        float fadeScale = 1.0f;
        if (remaining < FADE_TICKS) {
            float progress = MathHelper.clamp(remaining / (float) FADE_TICKS, 0.0f, 1.0f);
            fadeScale = 0.5f + 0.5f * progress;
        }

        return popScale * fadeScale;
    }

    /**
     * Returns the interpolated 3D position. If attached to a living entity,
     * follows the entity smoothly using its position and height.
     */
    public Vec3d getInterpolatedPos(float tickDelta, World world) {
        if (this.entityId != -1 && world != null) {
            Entity entity = world.getEntityById(this.entityId);
            if (entity != null && entity.isAlive()) {
                double ex = MathHelper.lerp((double) tickDelta, entity.lastRenderX, entity.getX());
                double ey = MathHelper.lerp((double) tickDelta, entity.lastRenderY, entity.getY()) + entity.getHeight() + 0.35;
                double ez = MathHelper.lerp((double) tickDelta, entity.lastRenderZ, entity.getZ());

                this.x = entity.getX();
                this.y = entity.getY() + entity.getHeight() + 0.35;
                this.z = entity.getZ();

                return new Vec3d(ex, ey, ez);
            }
        }
        return new Vec3d(this.x, this.y, this.z);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAuthorUuid() {
        return authorUuid;
    }

    public String getAuthorName() {
        return authorName;
    }

    public PingType getType() {
        return type;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public int getEntityId() {
        return entityId;
    }

    public Optional<UUID> getEntityUuid() {
        return entityUuid;
    }

    public int getAge() {
        return age;
    }
}

package net.pingpoint.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.UUID;

public record PingS2CPayload(
    Action action,
    UUID pingId,
    UUID authorUuid,
    String authorName,
    PingType type,
    double x,
    double y,
    double z,
    int entityId,
    Optional<UUID> entityUuid
) implements CustomPayload {

    public enum Action {
        ADD,
        REMOVE
    }

    public static final CustomPayload.Id<PingS2CPayload> ID = new CustomPayload.Id<>(Identifier.of("pingpoint", "ping_s2c"));

    public static final PacketCodec<RegistryByteBuf, PingS2CPayload> CODEC = new PacketCodec<>() {
        @Override
        public PingS2CPayload decode(RegistryByteBuf buf) {
            return new PingS2CPayload(buf);
        }

        @Override
        public void encode(RegistryByteBuf buf, PingS2CPayload value) {
            value.write(buf);
        }
    };

    public static PingS2CPayload add(
        UUID pingId,
        UUID authorUuid,
        String authorName,
        PingType type,
        double x,
        double y,
        double z,
        int entityId,
        Optional<UUID> entityUuid
    ) {
        return new PingS2CPayload(
            Action.ADD,
            pingId,
            authorUuid,
            authorName,
            type,
            x,
            y,
            z,
            entityId,
            entityUuid
        );
    }

    public static PingS2CPayload remove(UUID pingId) {
        return new PingS2CPayload(
            Action.REMOVE,
            pingId,
            new UUID(0L, 0L),
            "",
            PingType.ATTENTION,
            0.0,
            0.0,
            0.0,
            -1,
            Optional.empty()
        );
    }

    public PingS2CPayload(RegistryByteBuf buf) {
        this(readFromBuf(buf));
    }

    private PingS2CPayload(PingS2CPayload other) {
        this(
            other.action,
            other.pingId,
            other.authorUuid,
            other.authorName,
            other.type,
            other.x,
            other.y,
            other.z,
            other.entityId,
            other.entityUuid
        );
    }

    private static PingS2CPayload readFromBuf(RegistryByteBuf buf) {
        Action action = buf.readEnumConstant(Action.class);
        UUID pingId = buf.readUuid();
        if (action == Action.REMOVE) {
            return remove(pingId);
        }

        UUID authorUuid = buf.readUuid();
        String authorName = buf.readString();
        PingType type = buf.readEnumConstant(PingType.class);
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        int entityId = buf.readInt();
        boolean hasEntityUuid = buf.readBoolean();
        Optional<UUID> entityUuid = hasEntityUuid ? Optional.of(buf.readUuid()) : Optional.empty();

        return new PingS2CPayload(action, pingId, authorUuid, authorName, type, x, y, z, entityId, entityUuid);
    }

    public void write(RegistryByteBuf buf) {
        buf.writeEnumConstant(action);
        buf.writeUuid(pingId);
        if (action == Action.ADD) {
            buf.writeUuid(authorUuid);
            buf.writeString(authorName);
            buf.writeEnumConstant(type);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeInt(entityId);
            buf.writeBoolean(entityUuid.isPresent());
            entityUuid.ifPresent(buf::writeUuid);
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

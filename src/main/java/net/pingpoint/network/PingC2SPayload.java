package net.pingpoint.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.UUID;

public record PingC2SPayload(
    PingType type,
    double x,
    double y,
    double z,
    int entityId,
    Optional<UUID> entityUuid
) implements CustomPayload {

    public static final CustomPayload.Id<PingC2SPayload> ID = new CustomPayload.Id<>(Identifier.of("pingpoint", "ping_c2s"));

    public static final PacketCodec<RegistryByteBuf, PingC2SPayload> CODEC = new PacketCodec<>() {
        @Override
        public PingC2SPayload decode(RegistryByteBuf buf) {
            return new PingC2SPayload(buf);
        }

        @Override
        public void encode(RegistryByteBuf buf, PingC2SPayload value) {
            value.write(buf);
        }
    };

    public PingC2SPayload(RegistryByteBuf buf) {
        this(
            buf.readEnumConstant(PingType.class),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readInt(),
            buf.readBoolean() ? Optional.of(buf.readUuid()) : Optional.empty()
        );
    }

    public void write(RegistryByteBuf buf) {
        buf.writeEnumConstant(type);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeInt(entityId);
        buf.writeBoolean(entityUuid.isPresent());
        entityUuid.ifPresent(buf::writeUuid);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

package net.pingpoint.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Registry and facade for PingPoint CustomPayload networking records.
 */
public final class PingPayload {

    private PingPayload() {
    }

    /**
     * Registers both C2S and S2C CustomPayload codecs to Fabric's PayloadTypeRegistry.
     */
    public static void registerAll() {
        PayloadTypeRegistry.playC2S().register(PingC2SPayload.ID, PingC2SPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PingS2CPayload.ID, PingS2CPayload.CODEC);
    }
}

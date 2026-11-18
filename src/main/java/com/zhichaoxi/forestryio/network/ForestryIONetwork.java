package com.zhichaoxi.forestryio.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Network setup of Forestry IO; registered from the mod constructor. */
public final class ForestryIONetwork {
    /** Bump when the payload layout changes in an incompatible way. */
    private static final String PROTOCOL_VERSION = "1";

    private ForestryIONetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION).playToServer(
                UpdateForestryFilterPayload.TYPE,
                UpdateForestryFilterPayload.STREAM_CODEC,
                UpdateForestryFilterPayload::handle);
    }
}

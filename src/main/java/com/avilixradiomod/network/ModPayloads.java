package com.avilixradiomod.network;

import com.avilixradiomod.AvilixRadioMod;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ModPayloads {
    private ModPayloads() {}

    /**
     * Client helper: send a custom payload to the server.
     * Keeps GUI code simple.
     */
    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(AvilixRadioMod.MOD_ID)
                .playToServer(
                        RadioSettingsPayload.TYPE,
                        RadioSettingsPayload.STREAM_CODEC,
                        ServerPayloadHandler::handleRadioSettings
                );
    }
}

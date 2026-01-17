package com.avilixradiomod.network;

import com.avilixradiomod.AvilixRadioMod;
import com.avilixradiomod.blockentity.RadioBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

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
                        ModPayloads::handleRadioSettings
                );
    }

    private static void handleRadioSettings(RadioSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            Level level = player.level();
            BlockPos pos = payload.pos();

            if (!(level.getBlockEntity(pos) instanceof RadioBlockEntity radio)) return;

            radio.setSettings(payload.url(), payload.playing(), payload.volume());
        });
    }
}

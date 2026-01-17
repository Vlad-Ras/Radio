package com.avilixradiomod.network;

import com.avilixradiomod.AvilixRadioMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RadioSettingsPayload(BlockPos pos, String url, boolean playing, int volume) implements CustomPacketPayload {

    public static final Type<RadioSettingsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AvilixRadioMod.MOD_ID, "radio_settings"));

    public static final StreamCodec<FriendlyByteBuf, RadioSettingsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RadioSettingsPayload decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            String url = buf.readUtf(8192);
            boolean playing = buf.readBoolean();
            int volume = buf.readVarInt();
            return new RadioSettingsPayload(pos, url, playing, volume);
        }

        @Override
        public void encode(FriendlyByteBuf buf, RadioSettingsPayload payload) {
            buf.writeBlockPos(payload.pos());
            buf.writeUtf(payload.url() == null ? "" : payload.url(), 8192);
            buf.writeBoolean(payload.playing());
            buf.writeVarInt(payload.volume());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.duncanjones.apneoforge;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record AudioStopPayload(UUID soundId) implements CustomPacketPayload {

    public static final Type<AudioStopPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AudioPlayer.MODID, "audio_stop"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AudioStopPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, AudioStopPayload::soundId,
            AudioStopPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}

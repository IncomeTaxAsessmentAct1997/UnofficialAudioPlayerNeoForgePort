package com.duncanjones.apneoforge;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record AudioSyncPayload(UUID soundId, String host, int port) implements CustomPacketPayload {

    public static final Type<AudioSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AudioPlayer.MODID, "audio_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AudioSyncPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, AudioSyncPayload::soundId,
            ByteBufCodecs.STRING_UTF8, AudioSyncPayload::host,
            ByteBufCodecs.VAR_INT, AudioSyncPayload::port,
            AudioSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}

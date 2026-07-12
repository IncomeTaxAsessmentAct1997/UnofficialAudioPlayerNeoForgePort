package com.duncanjones.apneoforge;

import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AudioNetworkingClient {

    public static final Map<UUID, CompletableFuture<SoundBuffer>> PENDING_BUFFERS = new ConcurrentHashMap<>();

    public static void handleSync(AudioSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> PENDING_BUFFERS.computeIfAbsent(payload.soundId(), id -> {
            CompletableFuture<SoundBuffer> future = new CompletableFuture<>();
            Thread thread = new Thread(() -> {
                try {
                    future.complete(download(payload));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                    AudioPlayer.LOGGER.warn("[AudioNetworkingClient] Failed to download custom sound {}", payload.soundId(), e);
                }
            }, "AudioPlayerDownloadThread");
            thread.setDaemon(true);
            thread.start();
            return future;
        }));
    }

    public static void handleStop(AudioStopPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ResourceLocation location = CustomSoundLocations.getSoundLocation(payload.soundId());
            Minecraft.getInstance().getSoundManager().stop(location, SoundSource.RECORDS);
            PENDING_BUFFERS.remove(payload.soundId());
        });
    }

    private static SoundBuffer download(AudioSyncPayload payload) throws Exception {
        String host = payload.host().isBlank() ? resolveFallbackHost() : payload.host();
        URI uri = URI.create("http://" + host + ":" + payload.port() + "/customsound/" + payload.soundId());
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);

        byte[] data;
        try (InputStream inputStream = connection.getInputStream()) {
            data = inputStream.readAllBytes();
        }
        connection.disconnect();

        AudioInputStream source = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data));
        AudioFormat format = source.getFormat();
        byte[] pcm = source.readAllBytes();

        ByteBuffer buffer = ByteBuffer.allocateDirect(pcm.length);
        buffer.put(pcm);
        buffer.flip();

        return new SoundBuffer(buffer, format);
    }

    private static String resolveFallbackHost() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCurrentServer() != null && minecraft.getCurrentServer().ip != null) {
            String ip = minecraft.getCurrentServer().ip;
            int colonIndex = ip.lastIndexOf(':');
            return colonIndex > 0 ? ip.substring(0, colonIndex) : ip;
        }
        return "127.0.0.1";
    }

}

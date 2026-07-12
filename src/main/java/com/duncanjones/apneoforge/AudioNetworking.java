package com.duncanjones.apneoforge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

public class AudioNetworking {

    public static void sendSync(ServerLevel level, Vec3 pos, float distance, UUID soundId) {
        AudioSyncPayload payload = buildSyncPayload(soundId);
        for (ServerPlayer player : level.players()) {
            if (player.position().closerThan(pos, distance + 16.0D)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    public static void sendStop(ServerLevel level, Vec3 pos, float distance, UUID soundId) {
        AudioStopPayload payload = new AudioStopPayload(soundId);
        for (ServerPlayer player : level.players()) {
            if (player.position().closerThan(pos, distance)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static AudioSyncPayload buildSyncPayload(UUID soundId) {
        String urlString = AudioPlayer.WEB_SERVER_CONFIG.url.get();
        String host = "";
        int port = AudioPlayer.WEB_SERVER_CONFIG.port.get();
        if (!urlString.isBlank()) {
            try {
                URL url = new URL(urlString);
                host = url.getHost();
                if (url.getPort() != -1) {
                    port = url.getPort();
                }
            } catch (MalformedURLException e) {
                AudioPlayer.LOGGER.error("Invalid web server URL: {}", urlString);
            }
        }
        return new AudioSyncPayload(soundId, host, port);
    }

}

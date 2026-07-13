package com.duncanjones.apneoforge;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import net.minecraft.core.Holder;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.io.IOUtils;

import javax.annotation.Nullable;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.UUID;

public class AudioManager {

    public static LevelResource AUDIO_DATA = new LevelResource("audio_player_data");

    public static short[] getSound(MinecraftServer server, UUID id) throws Exception {
        float volume;
        if (VolumeOverrideManager.instance().isPresent()) {
            volume = VolumeOverrideManager.instance().get().getAudioVolume(id);
        } else {
            volume = 1F;
        }
        return AudioPlayer.AUDIO_CACHE.get(id, () -> AudioConverter.convert(getExistingSoundFile(server, id), volume));
    }

    public static Path getSoundFile(MinecraftServer server, UUID id, String extension) {
        return getAudioDataFolder(server).resolve(id.toString() + "." + extension);
    }

    public static Path getAudioDataFolder(MinecraftServer server) {
        return server.getWorldPath(AUDIO_DATA);
    }

    public static Path getExistingSoundFile(MinecraftServer server, UUID id) throws FileNotFoundException {
        Path file = getSoundFile(server, id, AudioConverter.AudioType.MP3.getExtension());
        if (Files.exists(file)) {
            return file;
        }
        file = getSoundFile(server, id, AudioConverter.AudioType.WAV.getExtension());
        if (Files.exists(file)) {
            return file;
        }
        throw new FileNotFoundException("Audio does not exist");
    }

    public static boolean checkSoundExists(MinecraftServer server, UUID id) {
        Path file = getSoundFile(server, id, AudioConverter.AudioType.MP3.getExtension());
        if (Files.exists(file)) {
            return true;
        }
        file = getSoundFile(server, id, AudioConverter.AudioType.WAV.getExtension());
        return Files.exists(file);
    }

    public static Path getUploadFolder() {
        return FMLPaths.GAMEDIR.get().resolve("audioplayer_uploads");
    }

    public static void saveSound(MinecraftServer server, UUID id, String url) throws UnsupportedAudioFileException, IOException {
        URL parsedUrl;
        try {
            parsedUrl = URI.create(url).toURL();
        } catch (IllegalArgumentException | MalformedURLException e) {
            throw new IOException("Invalid URL: " + url, e);
        }
        byte[] data = download(parsedUrl, AudioPlayer.SERVER_CONFIG.maxUploadSize.get());
        saveSound(server, id, FileNameManager.getFileNameFromUrl(url), data);
    }

    public static void saveSound(MinecraftServer server, UUID id, String fileName, byte[] data) throws UnsupportedAudioFileException, IOException {
        AudioConverter.AudioType audioType = AudioConverter.getAudioType(data);
        checkExtensionAllowed(audioType);

        Path soundFile = getSoundFile(server, id, audioType.getExtension());
        if (Files.exists(soundFile)) {
            throw new FileAlreadyExistsException("This audio already exists");
        }
        Files.createDirectories(soundFile.getParent());

        try (OutputStream outputStream = Files.newOutputStream(soundFile)) {
            IOUtils.write(data, outputStream);
        }

        FileNameManager.instance().ifPresent(mgr -> mgr.addFileName(id, fileName));
    }

    public static void saveSound(MinecraftServer server, UUID id, Path file) throws UnsupportedAudioFileException, IOException {
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            throw new NoSuchFileException("The file %s does not exist".formatted(file.toString()));
        }

        long size = Files.size(file);
        if (size > AudioPlayer.SERVER_CONFIG.maxUploadSize.get()) {
            throw new IOException("Maximum file size exceeded (%sMB>%sMB)".formatted(Math.round((float) size / 1_000_000F), Math.round(AudioPlayer.SERVER_CONFIG.maxUploadSize.get().floatValue() / 1_000_000F)));
        }

        AudioConverter.AudioType audioType = AudioConverter.getAudioType(file);
        checkExtensionAllowed(audioType);

        Path soundFile = getSoundFile(server, id, audioType.getExtension());
        if (Files.exists(soundFile)) {
            throw new FileAlreadyExistsException("This audio already exists");
        }
        Files.createDirectories(soundFile.getParent());

        Files.move(file, soundFile);
        FileNameManager.instance().ifPresent(mgr -> mgr.addFileName(id, FileNameManager.getFileNameFromPath(file)));
    }

    public static void checkExtensionAllowed(@Nullable AudioConverter.AudioType audioType) throws UnsupportedAudioFileException {
        if (audioType == null) {
            throw new UnsupportedAudioFileException("Unsupported audio format");
        }
        if (audioType.equals(AudioConverter.AudioType.MP3)) {
            if (!AudioPlayer.SERVER_CONFIG.allowMp3Upload.get()) {
                throw new UnsupportedAudioFileException("Uploading mp3 files is not allowed on this server");
            }
        }
        if (audioType.equals(AudioConverter.AudioType.WAV)) {
            if (!AudioPlayer.SERVER_CONFIG.allowWavUpload.get()) {
                throw new UnsupportedAudioFileException("Uploading wav files is not allowed on this server");
            }
        }
    }

    private static byte[] download(URL url, long limit) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", Filebin.USER_AGENT);
        connection.connect();

        BufferedInputStream bis = new BufferedInputStream(connection.getInputStream());

        int nRead;
        byte[] data = new byte[32768];

        while ((nRead = bis.read(data, 0, data.length)) != -1) {
            bos.write(data, 0, nRead);
            if (bos.size() > limit) {
                bis.close();
                throw new IOException("Maximum file size of %sMB exceeded".formatted((int) (((float) limit) / 1_000_000F)));
            }
        }
        bis.close();

        return bos.toByteArray();
    }

    @Nullable
    public static UUID play(ServerLevel level, BlockPos pos, PlayerType type, CustomSound sound, @Nullable Player player) {
        float range = sound.getRange(type);

        VoicechatServerApi api = Plugin.voicechatServerApi;
        if (api == null) {
            AudioPlayer.LOGGER.warn("[AudioManager] play: api=NULL -- Simple Voice Chat plugin never registered with this mod at pos={} soundId={}", pos, sound.getSoundId());
            return null;
        }

        @Nullable UUID channelID;
        if (type.equals(PlayerType.GOAT_HORN)) {
            Vec3 playerPos;
            if (player == null) {
                playerPos = new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            } else {
                playerPos = player.position();
            }
            channelID = PlayerManager.instance().playLocational(
                    api,
                    level,
                    playerPos,
                    sound.getSoundId(),
                    (player instanceof ServerPlayer p) ? p : null,
                    range,
                    type.getCategory(),
                    type.getMaxDuration().get()
            );
        } else if (sound.isStaticSound() && AudioPlayer.SERVER_CONFIG.allowStaticAudio.get()) {
            channelID = PlayerManager.instance().playStatic(
                    api,
                    level,
                    new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D),
                    sound.getSoundId(),
                    (player instanceof ServerPlayer p) ? p : null,
                    range,
                    type.getCategory(),
                    type.getMaxDuration().get()
            );
        } else {
            channelID = PlayerManager.instance().playLocational(
                    api,
                    level,
                    new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D),
                    sound.getSoundId(),
                    (player instanceof ServerPlayer p) ? p : null,
                    range,
                    type.getCategory(),
                    type.getMaxDuration().get()
            );
        }

        AudioPlayer.LOGGER.info("[AudioManager] play: resulting channelID={} for pos={} soundId={} type={}", channelID, pos, sound.getSoundId(), type);
        return channelID;
    }

    @Nullable
    public static UUID playVanillaJukebox(ServerLevel level, BlockPos pos, CustomSound sound) {
        UUID soundId = sound.getSoundId();
        if (!checkSoundExists(level.getServer(), soundId)) {
            AudioPlayer.LOGGER.warn("[AudioManager] playVanillaJukebox: sound file missing for soundId={}", soundId);
            return null;
        }

        Vec3 center = new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        AudioNetworking.sendSync(level, center, sound.getRange(PlayerType.MUSIC_DISC), soundId);

        Holder<SoundEvent> soundEvent = CustomSoundLocations.getSoundEventHolder(soundId);
        level.playSound(null, center.x, center.y, center.z, soundEvent, SoundSource.RECORDS, 4.0F, 1.0F);

        AudioPlayer.LOGGER.info("[AudioManager] playVanillaJukebox: playing soundId={} at {}", soundId, pos);
        return soundId;
    }

    public static void stopVanillaJukebox(ServerLevel level, BlockPos pos, UUID soundId) {
        Vec3 center = new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        AudioNetworking.sendStop(level, center, 64.0F, soundId);
    }

    public static float getLengthSeconds(short[] audio) {
        return (float) audio.length / AudioConverter.FORMAT.getSampleRate();
    }

}

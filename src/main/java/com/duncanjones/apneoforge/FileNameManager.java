package com.duncanjones.apneoforge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class FileNameManager {

    private final File file;
    private final Gson gson;
    private Map<UUID, String> fileNames;

    public FileNameManager(File file) {
        this.file = file;
        this.gson = new GsonBuilder().create();
        this.fileNames = new HashMap<>();
        load();
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        try (Reader reader = new FileReader(file)) {
            Type fileNameMapType = new TypeToken<Map<UUID, String>>() {
            }.getType();
            fileNames = gson.fromJson(reader, fileNameMapType);
        } catch (Exception e) {
            AudioPlayer.LOGGER.error("Failed to load file name mappings", e);
        }
        if (fileNames == null) {
            fileNames = new HashMap<>();
        }
        save();
    }

    public void save() {
        file.getParentFile().mkdirs();
        try (Writer writer = new FileWriter(file)) {
            gson.toJson(fileNames, writer);
        } catch (Exception e) {
            AudioPlayer.LOGGER.error("Failed to save file name mappings", e);
        }
    }

    @Nullable
    public String getFileName(UUID audioId) {
        return fileNames.get(audioId);
    }

    @Nullable
    public UUID getAudioId(String fileName) {
        UUID id = null;
        for (Map.Entry<UUID, String> entry : fileNames.entrySet()) {
            if (isNameEqualsWithoutExtension(entry.getValue(), fileName)) {
                if (id == null) {
                    id = entry.getKey();
                } else {
                    return null;
                }
            }
        }
        return id;
    }

    private static boolean isNameEqualsWithoutExtension(String name1, String name2) {
        if (name1 == null || name2 == null) {
            return false;
        }
        return fileNameWithoutExtension(name1).equals(fileNameWithoutExtension(name2));
    }

    private static String fileNameWithoutExtension(String name) {
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0) {
            return name;
        }
        return name.substring(0, dotIndex);
    }

    public void addFileName(UUID audioId, @Nullable String fileName) {
        if (fileName == null) {
            return;
        }
        fileNames.put(audioId, fileName);
        save();
    }

    public void remove(UUID audioId) {
        fileNames.remove(audioId);
        save();
    }

    @Nullable
    public static String getFileNameFromUrl(String url) {
        String name = url.substring(url.lastIndexOf('/') + 1).trim();
        if (name.isEmpty()) {
            return null;
        }
        return name;
    }

    @Nullable
    public static String getFileNameFromPath(Path path) {
        if (Files.isDirectory(path)) {
            return null;
        }
        String name = path.getFileName().toString();
        if (name.isEmpty()) {
            return null;
        }
        return name;
    }

    @Nullable
    private static FileNameManager INSTANCE;

    public static void init() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            AudioPlayer.LOGGER.info("Loading audio file name mappings...");
            Path audioDataFolder = AudioManager.getAudioDataFolder(event.getServer());
            if (Files.exists(audioDataFolder)) {
                try {
                    Files.createDirectories(audioDataFolder);
                } catch (IOException e) {
                    AudioPlayer.LOGGER.error("Failed to create audio data folder", e);
                    return;
                }
            }
            INSTANCE = new FileNameManager(audioDataFolder.resolve("file-name-mappings.json").toFile());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> {
            INSTANCE = null;
        });
    }

    public static Optional<FileNameManager> instance() {
        return Optional.ofNullable(INSTANCE);
    }

}

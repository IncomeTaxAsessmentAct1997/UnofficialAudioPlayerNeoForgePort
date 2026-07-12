package com.duncanjones.apneoforge;

import de.maxhenkel.admiral.MinecraftAdmiral;
import de.maxhenkel.configbuilder.ConfigBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Mod(AudioPlayer.MODID)
public class AudioPlayer {

    public static final String MODID = "apneoforge";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    public static ServerConfig SERVER_CONFIG;
    public static WebServerConfig WEB_SERVER_CONFIG;

    public static AudioCache AUDIO_CACHE;
    public static ScheduledExecutorService SCHEDULED_EXECUTOR = Executors.newScheduledThreadPool(1, r -> {
        Thread thread = new Thread(r, "AudioPlayerExecutor");
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, e) -> {
            AudioPlayer.LOGGER.error("Uncaught exception in thread {}", t.getName(), e);
        });
        return thread;
    });

    public AudioPlayer(IEventBus modEventBus) {
        VolumeOverrideManager.init();
        FileNameManager.init();
        Path configFolder = FMLPaths.CONFIGDIR.get().resolve(MODID);
        SERVER_CONFIG = ConfigBuilder.builder(ServerConfig::new).path(configFolder.resolve("audioplayer-server.properties")).build();
        if (SERVER_CONFIG.runWebServer.get()) {
            WEB_SERVER_CONFIG = ConfigBuilder.builder(WebServerConfig::new).path(configFolder.resolve("webserver.properties")).build();
        } else {
            WEB_SERVER_CONFIG = ConfigBuilder.builder(WebServerConfig::new).build();
        }

        try {
            Files.createDirectories(AudioManager.getUploadFolder());
        } catch (IOException e) {
            LOGGER.warn("Failed to create upload folder", e);
        }

        AUDIO_CACHE = new AudioCache(SERVER_CONFIG.cacheSize.get());

        modEventBus.addListener(this::onRegisterPayloadHandlers);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(AudioSyncPayload.TYPE, AudioSyncPayload.STREAM_CODEC, AudioNetworkingClient::handleSync);
        registrar.playToClient(AudioStopPayload.TYPE, AudioStopPayload.STREAM_CODEC, AudioNetworkingClient::handleStop);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        MinecraftAdmiral.builder(event.getDispatcher(), event.getBuildContext()).addCommandClasses(
                UploadCommands.class,
                ApplyCommands.class,
                UtilityCommands.class,
                VolumeCommands.class,
                PlayCommands.class
        ).setPermissionManager(AudioPlayerPermissionManager.INSTANCE).build();
    }

    private void onServerStarted(ServerStartedEvent event) {
        WebServerEvents.onServerStarted(event.getServer());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        WebServerEvents.onServerStopped(event.getServer());
    }
}

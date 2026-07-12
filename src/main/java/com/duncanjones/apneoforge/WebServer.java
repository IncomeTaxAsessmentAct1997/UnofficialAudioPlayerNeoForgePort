package com.duncanjones.apneoforge;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.microhttp.*;
import javax.annotation.Nullable;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
public class WebServer implements AutoCloseable {
    protected final MinecraftServer minecraftServer;
    protected final TokenManager tokenManager;
    @Nullable
    protected EventLoop eventLoop;
    protected int port;
    @Nullable
    protected StaticFileCache staticFileCache;
    protected WebServer(MinecraftServer minecraftServer) {
        this.minecraftServer = minecraftServer;
        tokenManager = new TokenManager();
    }
    public static WebServer create(MinecraftServer minecraftServer) {
        return new WebServer(minecraftServer);
    }
    public WebServer start() throws Exception {
        port = AudioPlayer.WEB_SERVER_CONFIG.port.get();
        Options options = Options.builder()
                .withPort(port)
                .withHost(null)
                .withRequestTimeout(Duration.ofSeconds(60))
                .withConcurrency(1)
                .withMaxRequestSize(AudioPlayer.SERVER_CONFIG.maxUploadSize.get().intValue())
                .build();
        try {
            staticFileCache = StaticFileCache.of("web");
        } catch (IOException e) {
            AudioPlayer.LOGGER.warn("Bundled web UI resources (\"web\" folder) not found on the classpath; the browser upload page will be unavailable, but the upload/download API (including custom sound streaming) will still start.", e);
            staticFileCache = new StaticFileCache(Map.of());
        }
        eventLoop = new EventLoop(options, NoopLogger.instance(), this::handleRequest);
        eventLoop.start();
        return this;
    }
    private void handleRequest(Request request, Consumer<Response> responseConsumer) {
        if (!handleAuth(request, responseConsumer)) {
            return;
        }
        String path = request.uri().split("\\?")[0];
        if (path.startsWith("/upload")) {
            handleUpload(request, responseConsumer);
        } else if (path.startsWith("/customsound/")) {
            handleServeCustomSound(request, path, responseConsumer);
        } else {
            handleServeStatic(request, responseConsumer);
        }
    }
    private boolean handleAuth(Request request, Consumer<Response> responseConsumer) {
        String username = AudioPlayer.WEB_SERVER_CONFIG.authUsername.get();
        String password = AudioPlayer.WEB_SERVER_CONFIG.authPassword.get();
        if (username.isBlank() || password.isBlank()) {
            return true;
        }
        String authHeader = request.header("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String encodedCredentials = authHeader.substring("Basic ".length()).trim();
            String credentials = new String(Base64.getDecoder().decode(encodedCredentials));
            if (credentials.equals(username + ":" + password)) {
                return true;
            }
        }
        responseConsumer.accept(
                new Response(
                        401,
                        "UNAUTHORIZED",
                        List.of(
                                new Header("Content-Type", "text/plain"),
                                new Header("WWW-Authenticate", "Basic realm=\"Restricted Area\"")
                        ),
                        "Unauthorized\n".getBytes()
                )
        );
        return false;
    }
    private void handleUpload(Request request, Consumer<Response> responseConsumer) {
        List<Header> headers = new ArrayList<>();
        headers.add(new Header("Access-Control-Allow-Origin", "*"));
        headers.add(new Header("Access-Control-Allow-Methods", "*"));
        headers.add(new Header("Access-Control-Allow-Headers", "*"));
        if (request.method().equalsIgnoreCase("OPTIONS")) {
            responseConsumer.accept(
                    new Response(
                            204,
                            "NO CONTENT",
                            headers,
                            "".getBytes()
                    )
            );
            return;
        }
        if (!request.method().equalsIgnoreCase("POST")) {
            responseConsumer.accept(
                    new Response(
                            400,
                            "BAD REQUEST",
                            headers,
                            "Bad request".getBytes()
                    )
            );
            return;
        }
        String tokenValue = request.header("token");
        if (tokenValue == null) {
            responseConsumer.accept(
                    new Response(
                            401,
                            "UNAUTHORIZED",
                            headers,
                            "Unauthorized\n".getBytes()
                    )
            );
            return;
        }
        UUID token;
        try {
            token = UUID.fromString(tokenValue);
        } catch (IllegalArgumentException e) {
            responseConsumer.accept(
                    new Response(
                            400,
                            "BAD REQUEST",
                            headers,
                            "Bad request".getBytes()
                    )
            );
            return;
        }
        UUID playerId = tokenManager.useToken(token);
        if (playerId == null) {
            responseConsumer.accept(
                    new Response(
                            401,
                            "UNAUTHORIZED",
                            headers,
                            "Unauthorized\n".getBytes()
                    )
            );
            return;
        }
        byte[] data = request.body();
        if (data.length > AudioPlayer.SERVER_CONFIG.maxUploadSize.get()) {
            responseConsumer.accept(
                    new Response(
                            414,
                            "TOO LONG",
                            headers,
                            "Too long\n".getBytes()
                    )
            );
            return;
        }
        upload(playerId, token, data);
        responseConsumer.accept(
                new Response(
                        200,
                        "OK",
                        headers,
                        "".getBytes()
                )
        );
    }
    private void handleServeCustomSound(Request request, String path, Consumer<Response> responseConsumer) {
        if (!request.method().equalsIgnoreCase("GET")) {
            responseConsumer.accept(new Response(400, "BAD REQUEST", List.of(), "Bad Request\n".getBytes()));
            return;
        }
        String idPart = path.substring("/customsound/".length());
        UUID soundId;
        try {
            soundId = UUID.fromString(idPart);
        } catch (IllegalArgumentException e) {
            responseConsumer.accept(new Response(400, "BAD REQUEST", List.of(), "Bad Request\n".getBytes()));
            return;
        }
        try {
            byte[] wav = buildWavForSound(soundId);
            responseConsumer.accept(
                    new Response(
                            200,
                            "OK",
                            List.of(new Header("Content-Type", "audio/wav")),
                            wav
                    )
            );
        } catch (Exception e) {
            AudioPlayer.LOGGER.warn("Failed to serve custom sound {}", soundId, e);
            responseConsumer.accept(new Response(404, "NOT FOUND", List.of(), "Not Found\n".getBytes()));
        }
    }
    private byte[] buildWavForSound(UUID soundId) throws Exception {
        short[] audio = AudioManager.getSound(minecraftServer, soundId);
        byte[] pcm = new byte[audio.length * 2];
        for (int i = 0; i < audio.length; i++) {
            short sample = audio[i];
            pcm[i * 2] = (byte) (sample & 0xFF);
            pcm[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        AudioFormat format = AudioConverter.FORMAT;
        AudioInputStream audioInputStream = new AudioInputStream(new ByteArrayInputStream(pcm), format, audio.length);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outputStream);
        return outputStream.toByteArray();
    }
    private void handleServeStatic(Request request, Consumer<Response> responseConsumer) {
        if (!request.method().equalsIgnoreCase("GET")) {
            responseConsumer.accept(
                    new Response(
                            400,
                            "BAD REQUEST",
                            List.of(),
                            "Bad Request\n".getBytes()
                    )
            );
            return;
        }
        String requestedResource = request.uri().split("\\?")[0];
        if (requestedResource.equals("/")) {
            requestedResource = "/index.html";
        }
        byte[] data = staticFileCache.get(requestedResource);
        if (data == null) {
            responseConsumer.accept(
                    new Response(
                            400,
                            "BAD REQUEST",
                            List.of(new Header("Content-Type", "text/plain")),
                            "Bad Request\n".getBytes()
                    )
            );
            return;
        }
        String mimeType = getMimeType(requestedResource);
        responseConsumer.accept(
                new Response(
                        200,
                        "OK",
                        mimeType != null ? List.of(new Header("Content-Type", "%s; charset=UTF-8".formatted(mimeType))) : List.of(),
                        data
                )
        );
    }
    public int getPort() {
        return eventLoop != null ? port : -1;
    }
    public TokenManager getTokenManager() {
        return tokenManager;
    }
    public MinecraftServer getMinecraftServer() {
        return minecraftServer;
    }
    @Override
    public void close() {
        if (eventLoop != null) {
            eventLoop.stop();
            eventLoop = null;
        }
    }
    private static final Map<String, String> MIME_TYPES = Map.of(
            "html", "text/html",
            "css", "text/css",
            "js", "application/javascript",
            "ico", "image/x-icon"
    );
    @Nullable
    private static String getMimeType(String path) {
        int lastSlashIndex = path.lastIndexOf('/');
        if (lastSlashIndex >= 0) {
            path = path.substring(lastSlashIndex + 1);
        }
        int lastDotIndex = path.lastIndexOf('.');
        if (lastDotIndex < 0) {
            return null;
        }
        String extension = path.substring(lastDotIndex + 1);
        return MIME_TYPES.get(extension);
    }
    private void upload(UUID playerId, UUID token, byte[] audioData) {
        ServerPlayer player = minecraftServer.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        new Thread(() -> {
            try {
                AudioManager.saveSound(minecraftServer, token, null, audioData);
                player.sendSystemMessage(UploadCommands.sendUUIDMessage(token, Component.literal("Successfully uploaded sound.")));
            } catch (Exception e) {
                AudioPlayer.LOGGER.warn("{} failed to upload a sound: {}", player.getName().getString(), e.getMessage());
                player.sendSystemMessage(Component.literal("Failed to upload sound: %s".formatted(e.getMessage())));
            }
        }).start();
    }
}

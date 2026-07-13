package com.duncanjones.apneoforge;


import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TokenManager implements AutoCloseable {

    private static final long CLEANUP_INTERVAL_SECONDS = 60L;

    private final Map<UUID, Token> tokens;
    private final ScheduledExecutorService cleanupExecutor;
    private final ScheduledFuture<?> cleanupTask;

    public TokenManager() {
        tokens = new ConcurrentHashMap<>();
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "AudioPlayerTokenCleanup");
            thread.setDaemon(true);
            return thread;
        });
        cleanupTask = cleanupExecutor.scheduleWithFixedDelay(this::cleanInvalidTokens, CLEANUP_INTERVAL_SECONDS, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public UUID generateToken(UUID playerId) {
        UUID token = UUID.randomUUID();
        tokens.put(token, new Token(token, playerId));
        return token;
    }

    /**
     * @param token the token
     * @return the player ID or <code>null</code> if the token is invalid
     */
    @Nullable
    public UUID useToken(UUID token) {
        Token t = tokens.get(token);
        if (t == null) {
            return null;
        }
        tokens.remove(token);
        if (!t.isValid()) {
            return null;
        }
        return t.getPlayerId();
    }

    public boolean isValidToken(UUID token) {
        Token t = tokens.get(token);
        if (t == null) {
            return false;
        }
        return t.isValid();
    }

    public void cleanInvalidTokens() {
        tokens.values().removeIf(token -> !token.isValid());
    }

    @Override
    public void close() {
        cleanupTask.cancel(false);
        cleanupExecutor.shutdown();
    }

    protected static class Token {
        private final UUID token;
        private final UUID playerId;
        private final long time;

        public Token(UUID token, UUID playerId) {
            this.token = token;
            this.playerId = playerId;
            this.time = System.currentTimeMillis();
        }

        public UUID getToken() {
            return token;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public long getTime() {
            return time;
        }

        public boolean isValid() {
            return System.currentTimeMillis() - time <= AudioPlayer.WEB_SERVER_CONFIG.tokenTimeout.get();
        }
    }

}

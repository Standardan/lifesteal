package io.github.standardan.lifesteal.storage;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * SQLite-backed store for each player's heart count and elimination state.
 * All queries run on a single dedicated thread so the main server thread is
 * never blocked on disk I/O.
 */
public final class PlayerDataStore {

    /** Immutable snapshot of a player's persisted lifesteal state. */
    public record PlayerData(int hearts, boolean eliminated) {}

    private final Plugin plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Lifesteal-DB");
        t.setDaemon(true);
        return t;
    });
    private Connection connection;

    public PlayerDataStore(Plugin plugin) {
        this.plugin = plugin;
    }

    public void connect() throws SQLException, ClassNotFoundException {
        plugin.getDataFolder().mkdirs();
        Class.forName("org.sqlite.JDBC");
        File dbFile = new File(plugin.getDataFolder(), "players.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS players (
                        uuid       TEXT PRIMARY KEY,
                        hearts     INTEGER NOT NULL,
                        eliminated INTEGER NOT NULL DEFAULT 0
                    )
                    """);
        }
    }

    public CompletableFuture<Optional<PlayerData>> load(UUID uuid) {
        return supply(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT hearts, eliminated FROM players WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new PlayerData(rs.getInt("hearts"), rs.getInt("eliminated") == 1));
                    }
                    return Optional.<PlayerData>empty();
                }
            }
        });
    }

    public CompletableFuture<Void> save(UUID uuid, int hearts, boolean eliminated) {
        return supply(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO players(uuid, hearts, eliminated) VALUES(?, ?, ?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET hearts = excluded.hearts, "
                            + "eliminated = excluded.eliminated")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, hearts);
                ps.setInt(3, eliminated ? 1 : 0);
                ps.executeUpdate();
            }
            return null;
        });
    }

    private <T> CompletableFuture<T> supply(SqlSupplier<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                future.complete(work.get());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Error closing database: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws Exception;
    }
}

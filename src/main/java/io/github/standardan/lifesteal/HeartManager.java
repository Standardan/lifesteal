package io.github.standardan.lifesteal;

import io.github.standardan.lifesteal.storage.PlayerDataStore;
import io.github.standardan.lifesteal.storage.PlayerDataStore.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The brain of the plugin: tracks every online player's heart count in memory
 * (so combat math is instant) and mirrors changes to the database asynchronously.
 */
public final class HeartManager {

    private final LifestealPlugin plugin;
    private final PlayerDataStore store;

    private int startingHearts;
    private int maxHearts;
    private int minHearts;
    private boolean banOnEliminate;

    private final Map<UUID, Integer> heartCache = new ConcurrentHashMap<>();
    private final Set<UUID> eliminated = ConcurrentHashMap.newKeySet();

    public HeartManager(LifestealPlugin plugin, PlayerDataStore store) {
        this.plugin = plugin;
        this.store = store;
        reloadConfig();
    }

    public void reloadConfig() {
        var cfg = plugin.getConfig();
        this.startingHearts = cfg.getInt("starting-hearts", 10);
        this.maxHearts = cfg.getInt("max-hearts", 20);
        this.minHearts = cfg.getInt("min-hearts", 1);
        this.banOnEliminate = "ban".equalsIgnoreCase(cfg.getString("elimination", "ban"));
    }

    /** Load (or initialise) a player's hearts when they join. */
    public void loadPlayer(Player player) {
        UUID id = player.getUniqueId();
        store.load(id).thenAccept(opt -> plugin.sync(() -> {
            int hearts = opt.map(PlayerData::hearts).orElse(startingHearts);
            boolean elim = opt.map(PlayerData::eliminated).orElse(false);
            heartCache.put(id, hearts);
            if (elim) {
                eliminated.add(id);
                if (banOnEliminate) {
                    player.kick(Component.text("You are eliminated and cannot rejoin.", NamedTextColor.RED));
                    return;
                }
                player.setGameMode(GameMode.SPECTATOR);
            }
            applyHealth(player, hearts);
            if (opt.isEmpty()) {
                store.save(id, hearts, false); // persist first-join starting hearts
            }
        }));
    }

    public void unloadPlayer(UUID id) {
        heartCache.remove(id);
        eliminated.remove(id);
    }

    public int getHearts(UUID id) {
        return heartCache.getOrDefault(id, startingHearts);
    }

    public boolean isEliminated(UUID id) {
        return eliminated.contains(id);
    }

    public int maxHearts() {
        return maxHearts;
    }

    public int minHearts() {
        return minHearts;
    }

    /** Set an exact heart count (clamped) and persist. */
    public void setHearts(Player player, int hearts) {
        int clamped = Math.max(0, Math.min(maxHearts, hearts));
        heartCache.put(player.getUniqueId(), clamped);
        applyHealth(player, clamped);
        store.save(player.getUniqueId(), clamped, eliminated.contains(player.getUniqueId()));
    }

    /** Add one heart; false if already at the cap. */
    public boolean addHeart(Player player) {
        int current = getHearts(player.getUniqueId());
        if (current >= maxHearts) {
            return false;
        }
        setHearts(player, current + 1);
        return true;
    }

    /**
     * Remove one heart. Returns true if that drop should ELIMINATE the player
     * (i.e. it would take them below the minimum) - the caller then decides
     * exactly when to run elimination (usually a tick later).
     */
    public boolean removeHeartOrEliminate(Player player) {
        int current = getHearts(player.getUniqueId());
        if (current - 1 < minHearts) {
            return true;
        }
        setHearts(player, current - 1);
        return false;
    }

    public void eliminate(Player player) {
        UUID id = player.getUniqueId();
        eliminated.add(id);
        heartCache.put(id, Math.max(0, minHearts - 1));
        store.save(id, getHearts(id), true);
        if (banOnEliminate) {
            player.kick(Component.text("You have been eliminated. You can no longer respawn.",
                    NamedTextColor.RED));
        } else {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    public void revive(UUID id) {
        eliminated.remove(id);
        heartCache.put(id, startingHearts);
        store.save(id, startingHearts, false);
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            online.setGameMode(GameMode.SURVIVAL);
            applyHealth(online, startingHearts);
        }
    }

    /** Re-apply the player's max-health attribute (used on join and respawn). */
    public void applyHealth(Player player, int hearts) {
        double max = Math.max(2.0, hearts * 2.0);
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(max);
            if (player.getHealth() > max) {
                player.setHealth(max);
            }
        }
    }
}

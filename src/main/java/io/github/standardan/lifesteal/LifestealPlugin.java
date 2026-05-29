package io.github.standardan.lifesteal;

import io.github.standardan.lifesteal.command.LifestealCommand;
import io.github.standardan.lifesteal.item.HeartItem;
import io.github.standardan.lifesteal.listener.CombatListener;
import io.github.standardan.lifesteal.storage.PlayerDataStore;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;

public final class LifestealPlugin extends JavaPlugin {

    private PlayerDataStore store;
    private HeartItem heartItem;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        store = new PlayerDataStore(this);
        try {
            store.connect();
        } catch (Exception e) {
            getLogger().severe("Failed to open database, disabling: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        heartItem = new HeartItem(this);
        HeartManager hearts = new HeartManager(this, store);

        // Register the Heart crafting recipe (ignore if a reload re-adds it).
        try {
            getServer().addRecipe(heartItem.recipe());
        } catch (IllegalStateException ignored) {
            // recipe already registered
        }

        getServer().getPluginManager().registerEvents(new CombatListener(this, hearts, heartItem), this);

        LifestealCommand handler = new LifestealCommand(this, hearts, heartItem);
        for (String name : List.of("lifesteal", "withdraw", "hearts")) {
            PluginCommand command = Objects.requireNonNull(getCommand(name), name + " missing from plugin.yml");
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        // Handle /reload or late enable: load anyone already online.
        getServer().getOnlinePlayers().forEach(hearts::loadPlayer);

        getLogger().info("Lifesteal enabled.");
    }

    @Override
    public void onDisable() {
        if (heartItem != null) {
            getServer().removeRecipe(heartItem.recipeKey());
        }
        if (store != null) {
            store.close();
        }
    }

    /** Run a task on the main server thread (used by async DB callbacks). */
    public void sync(Runnable task) {
        if (isEnabled()) {
            getServer().getScheduler().runTask(this, task);
        }
    }
}

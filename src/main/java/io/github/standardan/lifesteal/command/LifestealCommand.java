package io.github.standardan.lifesteal.command;

import io.github.standardan.lifesteal.HeartManager;
import io.github.standardan.lifesteal.LifestealPlugin;
import io.github.standardan.lifesteal.item.HeartItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Handles /hearts, /withdraw and the admin /lifesteal command.
 */
public final class LifestealCommand implements CommandExecutor, TabCompleter {

    private final LifestealPlugin plugin;
    private final HeartManager hearts;
    private final HeartItem heartItem;

    public LifestealCommand(LifestealPlugin plugin, HeartManager hearts, HeartItem heartItem) {
        this.plugin = plugin;
        this.hearts = hearts;
        this.heartItem = heartItem;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "hearts" -> showHearts(sender);
            case "withdraw" -> withdraw(sender);
            case "lifesteal" -> admin(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void showHearts(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players have hearts.");
            return;
        }
        player.sendMessage(Component.text("You have " + hearts.getHearts(player.getUniqueId())
                + "/" + hearts.maxHearts() + " hearts.", NamedTextColor.RED));
    }

    private void withdraw(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can withdraw hearts.");
            return;
        }
        int current = hearts.getHearts(player.getUniqueId());
        if (current - 1 < hearts.minHearts()) {
            player.sendMessage(Component.text("You can't withdraw a heart - you'd risk elimination.",
                    NamedTextColor.RED));
            return;
        }
        hearts.setHearts(player, current - 1);
        // Give the heart item, dropping it if the inventory is full.
        ItemStack heart = heartItem.create();
        player.getInventory().addItem(heart).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.sendMessage(Component.text("Withdrew a heart into an item. Hearts: "
                + hearts.getHearts(player.getUniqueId()), NamedTextColor.YELLOW));
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lifesteal.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        if (args.length == 0) {
            sender.sendMessage(Component.text("/lifesteal <reload|sethearts <player> <n>|revive <player>>",
                    NamedTextColor.GRAY));
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadConfig();
                hearts.reloadConfig();
                sender.sendMessage(Component.text("Lifesteal config reloaded.", NamedTextColor.GREEN));
            }
            case "sethearts" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /lifesteal sethearts <player> <n>", NamedTextColor.RED));
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not online.", NamedTextColor.RED));
                    return;
                }
                try {
                    hearts.setHearts(target, Integer.parseInt(args[2]));
                    sender.sendMessage(Component.text("Set " + target.getName() + "'s hearts to "
                            + hearts.getHearts(target.getUniqueId()) + ".", NamedTextColor.GREEN));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("That's not a number.", NamedTextColor.RED));
                }
            }
            case "revive" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /lifesteal revive <player>", NamedTextColor.RED));
                    return;
                }
                revive(sender, args[1]);
            }
            default -> sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
        }
    }

    private void revive(CommandSender sender, String name) {
        // Eliminated players are usually offline, so resolve the UUID off the
        // main thread (name->UUID lookups can touch the network), then apply
        // the revive back on the main thread.
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
            UUID id = offline.getUniqueId();
            plugin.sync(() -> {
                hearts.revive(id);
                sender.sendMessage(Component.text("Revived " + name + ".", NamedTextColor.GREEN));
            });
        });
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("lifesteal") && args.length == 1) {
            return List.of("reload", "sethearts", "revive").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}

package io.github.standardan.lifesteal.listener;

import io.github.standardan.lifesteal.HeartManager;
import io.github.standardan.lifesteal.LifestealPlugin;
import io.github.standardan.lifesteal.item.HeartItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Drives the core lifesteal loop: hearts transfer on kill, the dead get
 * eliminated at zero, and the Heart item can be consumed to gain a heart.
 */
public final class CombatListener implements Listener {

    private final LifestealPlugin plugin;
    private final HeartManager hearts;
    private final HeartItem heartItem;

    public CombatListener(LifestealPlugin plugin, HeartManager hearts, HeartItem heartItem) {
        this.plugin = plugin;
        this.hearts = hearts;
        this.heartItem = heartItem;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        hearts.loadPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hearts.unloadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        // The max-health attribute can reset on respawn; re-apply it.
        Player player = event.getPlayer();
        hearts.applyHealth(player, hearts.getHearts(player.getUniqueId()));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Killer steals a heart (player kills only - no farming mobs/self).
        if (killer != null && !killer.equals(victim)) {
            if (hearts.addHeart(killer)) {
                killer.sendMessage(Component.text("+1 heart for slaying " + victim.getName() + "!",
                        NamedTextColor.GREEN));
            }
        }

        // Victim loses a heart, or is eliminated if that takes them too low.
        boolean eliminate = hearts.removeHeartOrEliminate(victim);
        if (eliminate) {
            if (plugin.getConfig().getBoolean("drop-heart-on-elimination", true)) {
                victim.getWorld().dropItemNaturally(victim.getLocation(), heartItem.create());
            }
            // Run elimination a tick later - the player is mid-death right now.
            plugin.getServer().getScheduler().runTask(plugin, () -> hearts.eliminate(victim));
        } else {
            victim.sendMessage(Component.text("You lost a heart. Hearts: "
                    + hearts.getHearts(victim.getUniqueId()), NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onConsumeHeart(PlayerInteractEvent event) {
        // Only the main-hand right-click, to avoid firing twice.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack inHand = event.getItem();
        if (!heartItem.isHeart(inHand)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();

        if (!hearts.addHeart(player)) {
            player.sendMessage(Component.text("You're already at the maximum hearts.", NamedTextColor.RED));
            return;
        }
        inHand.setAmount(inHand.getAmount() - 1); // consume one
        player.sendMessage(Component.text("You gained a heart! Hearts: "
                + hearts.getHearts(player.getUniqueId()), NamedTextColor.GREEN));
    }
}

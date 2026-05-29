package io.github.standardan.lifesteal.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Factory + detector for the consumable "Heart" item. We tag the item in its
 * PersistentDataContainer so we can recognise it later no matter how it was
 * renamed or moved around - never by display name, which players can fake.
 */
public final class HeartItem {

    private final NamespacedKey tagKey;
    private final NamespacedKey recipeKey;

    public HeartItem(Plugin plugin) {
        this.tagKey = new NamespacedKey(plugin, "heart_item");
        this.recipeKey = new NamespacedKey(plugin, "heart_recipe");
    }

    /** Build a single Heart item. */
    public ItemStack create() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Heart", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Right-click to gain a heart.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** True if the given stack is one of our Heart items. */
    public boolean isHeart(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(tagKey, PersistentDataType.BYTE);
    }

    /** A crafting recipe so hearts can be made, not just looted. */
    public ShapedRecipe recipe() {
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, create());
        recipe.shape("DDD", "DND", "DDD");
        recipe.setIngredient('D', Material.DIAMOND_BLOCK);
        recipe.setIngredient('N', Material.NETHER_STAR);
        return recipe;
    }

    public NamespacedKey recipeKey() {
        return recipeKey;
    }
}

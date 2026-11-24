package com.yuan.yuancode.gui;

import com.yuan.yuancode.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

final class GuiItems {

    private GuiItems() {
    }

    static ItemStack make(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    static ItemStack filler() {
        return make(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
    }

    static ItemStack named(Material material, String nameKey, String fallback) {
        return make(material, Text.mm(nameKey == null ? fallback : nameKey), List.of());
    }
}

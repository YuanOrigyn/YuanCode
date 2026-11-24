package com.yuan.yuancode.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class CodeBrowserHolder implements InventoryHolder {

    private final int page;
    private final int maxPage;
    private final Map<Integer, String> codeSlots = new HashMap<>();
    private Inventory inventory;

    public CodeBrowserHolder(int page, int maxPage) {
        this.page = page;
        this.maxPage = maxPage;
    }

    public int page() {
        return page;
    }

    public int maxPage() {
        return maxPage;
    }

    public void codeAt(int slot, String codeId) {
        codeSlots.put(slot, codeId);
    }

    public String codeAt(int slot) {
        return codeSlots.get(slot);
    }

    public Map<Integer, String> codeSlots() {
        return Map.copyOf(codeSlots);
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

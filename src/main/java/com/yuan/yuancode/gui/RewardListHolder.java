package com.yuan.yuancode.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class RewardListHolder implements InventoryHolder {

    private final String codeId;
    private final Map<Integer, Integer> rewardSlots = new HashMap<>();
    private Inventory inventory;

    public RewardListHolder(String codeId) {
        this.codeId = codeId;
    }

    public String codeId() {
        return codeId;
    }

    public void rewardAt(int slot, int oneBasedIndex) {
        rewardSlots.put(slot, oneBasedIndex);
    }

    public Integer rewardAt(int slot) {
        return rewardSlots.get(slot);
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

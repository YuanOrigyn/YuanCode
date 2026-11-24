package com.yuan.yuancode.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CodeEditorHolder implements InventoryHolder {

    private final String codeId;
    private Inventory inventory;

    public CodeEditorHolder(String codeId) {
        this.codeId = codeId;
    }

    public String codeId() {
        return codeId;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

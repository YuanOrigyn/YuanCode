package com.yuan.yuancode.hook;

import com.yuan.yuancode.YuanCodePlugin;
import com.yuan.yuancode.config.ConfigManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Soft Vault economy hook. Money rewards are skipped when Vault is absent. */
public final class VaultHook {

    private final YuanCodePlugin plugin;
    private final ConfigManager config;
    private Economy economy;
    private boolean warned;

    public VaultHook(YuanCodePlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void setup() {
        economy = null;
        if (!config.get().hooks().vault()) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Economy> registration =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            plugin.getLogger().warning("Vault is installed but no Economy provider is registered.");
            return;
        }
        economy = registration.getProvider();
        plugin.getLogger().info("Hooked into Vault economy: " + economy.getName());
    }

    public boolean available() {
        return economy != null;
    }

    public boolean deposit(Player player, double amount) {
        if (economy == null) {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("Money reward skipped: no Vault economy provider is available.");
            }
            return false;
        }
        try {
            return economy.depositPlayer(player, amount).transactionSuccess();
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Vault deposit failed for " + player.getName() + ": " + ex.getMessage());
            return false;
        }
    }
}

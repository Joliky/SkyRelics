package me.jolikki.skyrelics;

import me.jolikki.skyrelics.command.SkyRelicsCommand;
import me.jolikki.skyrelics.listener.RelicListener;
import me.jolikki.skyrelics.manager.ConfigManager;
import me.jolikki.skyrelics.manager.CooldownManager;
import me.jolikki.skyrelics.manager.PermissionManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class SkyRelics extends JavaPlugin {

    private ConfigManager configManager;
    private PermissionManager permissionManager;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        permissionManager = new PermissionManager(this);

        registerCommands();
        registerListeners();

        getLogger().info("SkyRelics has been enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("SkyRelics has been disabled.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    private void registerCommands() {
        PluginCommand command = getCommand("skyrelics");
        if (command == null) {
            getLogger().severe("Command /skyrelics is missing in plugin.yml");
            return;
        }

        SkyRelicsCommand skyRelicsCommand = new SkyRelicsCommand(this, configManager);
        command.setExecutor(skyRelicsCommand);
        command.setTabCompleter(skyRelicsCommand);
    }

    private void registerListeners() {
        CooldownManager cooldownManager = new CooldownManager();
        getServer().getPluginManager().registerEvents(
                new RelicListener(this, cooldownManager, configManager, permissionManager),
                this
        );
    }
}

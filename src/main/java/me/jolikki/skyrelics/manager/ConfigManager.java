package me.jolikki.skyrelics.manager;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.stream.Collectors;

public class ConfigManager {

    private final JavaPlugin plugin;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
    }

    public String getString(String path) {
        String value = plugin.getConfig().getString(path);

        if (value == null) {
            return "";
        }

        return ChatColor.translateAlternateColorCodes('&', value);
    }

    public List<String> getStringList(String path) {
        return plugin.getConfig().getStringList(path).stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .collect(Collectors.toList());
    }

    public int getInt(String path) {
        return plugin.getConfig().getInt(path);
    }

    public long getLong(String path) {
        return plugin.getConfig().getLong(path);
    }

    public float getFloat(String path) {
        return (float) plugin.getConfig().getDouble(path);
    }

    public double getDouble(String path) {
        return plugin.getConfig().getDouble(path);
    }

    public boolean getBoolean(String path) {
        return plugin.getConfig().getBoolean(path);
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
    }

    public void save() {
        plugin.saveConfig();
    }
}


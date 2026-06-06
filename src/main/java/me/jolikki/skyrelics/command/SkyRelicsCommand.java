package me.jolikki.skyrelics.command;

import me.jolikki.skyrelics.manager.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SkyRelicsCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final ConfigManager config;

    public SkyRelicsCommand(JavaPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendUsage(sender, label);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("skyrelics.command.reload")) {
                sender.sendMessage(config.getString("messages.no-permission"));
                return true;
            }

            config.reload();
            sender.sendMessage(config.getString("messages.config-reloaded"));
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            if (!sender.hasPermission("skyrelics.command.give")) {
                sender.sendMessage(config.getString("messages.no-permission"));
                return true;
            }

            sendItemList(sender);
            return true;
        }

        if (!args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(config.getString("messages.unknown-command"));
            return true;
        }

        if (!sender.hasPermission("skyrelics.command.give")) {
            sender.sendMessage(config.getString("messages.no-permission"));
            return true;
        }

        if (args.length < 3) {
            sendUsage(sender, label);
            return true;
        }

        Player player = Bukkit.getPlayer(args[1]);
        if (player == null) {
            sender.sendMessage(config.getString("messages.player-not-found"));
            return true;
        }

        String itemId = args[2].toLowerCase();
        ConfigurationSection itemSection = config.getConfig().getConfigurationSection("items." + itemId);
        if (itemSection == null) {
            sender.sendMessage(config.getString("messages.item-not-found")
                    .replace("{item}", itemId));
            return true;
        }

        int amount = parseAmount(args);
        ItemStack item = createItem(sender, itemId, itemSection, amount);
        if (item == null) {
            return true;
        }

        player.getInventory().addItem(item);
        player.sendMessage(config.getString("messages.item-given")
                .replace("{item}", itemId)
                .replace("{amount}", String.valueOf(amount)));
        sender.sendMessage(config.getString("messages.item-given-sender")
                .replace("{player}", player.getName())
                .replace("{item}", itemId)
                .replace("{amount}", String.valueOf(amount)));

        return true;
    }

    private ItemStack createItem(CommandSender sender, String itemId, ConfigurationSection itemSection, int amount) {
        Material material = Material.matchMaterial(itemSection.getString("material", "DIAMOND"));
        if (material == null) {
            material = Material.DIAMOND;
        }

        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            sender.sendMessage(config.getString("messages.item-create-error"));
            return null;
        }

        meta.setDisplayName(config.getString("items." + itemId + ".name"));
        meta.setLore(config.getStringList("items." + itemId + ".lore"));
        meta.setUnbreakable(itemSection.getBoolean("unbreakable", false));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "sky_item_id"),
                PersistentDataType.STRING,
                itemId
        );
        item.setItemMeta(meta);

        applyEnchantments(item, itemSection.getConfigurationSection("enchantments"));
        return item;
    }

    private int parseAmount(String[] args) {
        if (args.length < 4) {
            return 1;
        }

        try {
            return Math.max(1, Integer.parseInt(args[3]));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(config.getString("messages.usage"));
        sender.sendMessage(ChatColor.GRAY + "/" + label + " give <player> <item_id> [amount]");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " list");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " reload");
    }

    private void sendItemList(CommandSender sender) {
        ConfigurationSection items = config.getConfig().getConfigurationSection("items");
        if (items == null || items.getKeys(false).isEmpty()) {
            sender.sendMessage(config.getString("messages.item-list-empty"));
            return;
        }

        Set<String> itemIds = items.getKeys(false);
        sender.sendMessage(config.getString("messages.item-list")
                .replace("{items}", String.join(", ", itemIds)));
    }

    private void applyEnchantments(ItemStack item, ConfigurationSection enchantments) {
        if (enchantments == null) {
            return;
        }

        for (String enchantmentName : enchantments.getKeys(false)) {
            Enchantment enchantment = Enchantment.getByName(enchantmentName.toUpperCase());
            if (enchantment == null) {
                continue;
            }

            int level = Math.max(1, enchantments.getInt(enchantmentName));
            item.addUnsafeEnchantment(enchantment, level);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], Arrays.asList("help", "reload", "list", "give"));
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                return filter(args[1], Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList()));
            }

            if (args.length == 3) {
                ConfigurationSection items = config.getConfig().getConfigurationSection("items");
                if (items == null) {
                    return Collections.emptyList();
                }

                return filter(args[2], new ArrayList<>(items.getKeys(false)));
            }

            if (args.length == 4) {
                return filter(args[3], Arrays.asList("1", "8", "16", "32", "64"));
            }
        }

        return Collections.emptyList();
    }

    private List<String> filter(String prefix, List<String> values) {
        String lowerPrefix = prefix.toLowerCase();
        return values.stream()
                .filter(value -> value.toLowerCase().startsWith(lowerPrefix))
                .collect(Collectors.toList());
    }
}

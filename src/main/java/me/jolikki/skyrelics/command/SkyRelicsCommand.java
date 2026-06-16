package me.jolikki.skyrelics.command;

import me.jolikki.skyrelics.manager.ConfigManager;
import me.jolikki.skyrelics.manager.CooldownManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class SkyRelicsCommand implements CommandExecutor, TabCompleter {

    private static final int ITEMS_PER_PAGE = 8;
    private static final Set<String> ABILITY_TYPES = Set.of(
            "TOGGLE_ENCHANT", "POTION_EFFECTS", "TELEPORT_TARGET", "DASH", "HEAL", "LIGHTNING_TARGET",
            "PARTICLE", "SOUND", "MESSAGE", "VEIN_MINE", "AUTO_SMELT", "ORE_MAGNET", "RESOURCE_SWEEP",
            "MINING_BURST", "TUNNEL_BREAK", "DIG_3X3", "AREA_BREAK", "TREASURE_CHANCE", "GRAVE_DIGGER",
            "FARMER_LUCK", "COMPOST_TOUCH", "LOG_SPLINTER", "EXPLOSIVE_MINE", "FORTUNE_SURGE",
            "REPAIR_ON_ORE", "STONE_SKIN", "MUD_ARMOR", "BEE_FRIEND", "TREE_FELLER", "BLEEDING_STRIKE",
            "ARMOR_CRACK", "EXECUTE", "BATTLE_ROAR", "RAGE_STACKS", "SHIELD_BREAKER", "WILD_CHARGE",
            "CRITICAL_DASH", "LIFESTEAL_CHOP", "VAMPIRIC_HIT", "BURROW", "QUICKSAND_TRAP",
            "SOIL_BLESSING", "SAND_WAVE", "PATH_MAKER", "EARTH_WALL", "HARVEST_RADIUS", "REPLANT",
            "GROWTH_AURA", "NATURE_HEAL", "POISON_THORNS", "VINE_ROOT", "LIFE_BLOOM", "FIRE_SLASH",
            "ICE_SLASH", "CHAIN_LIGHTNING", "SOUL_STEAL", "AOE_SWEEP", "FEAR", "MARK_TARGET",
            "COMBO_STRIKE"
    );
    private static final Set<String> ABILITY_TRIGGERS = Set.of(
            "right-click", "shift-right-click", "break-block", "attack-entity", "kill-entity", "receive-damage"
    );

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final CooldownManager cooldownManager;
    private final NamespacedKey itemIdKey;
    private final NamespacedKey legacyItemIdKey;
    private final NamespacedKey ownerKey;

    public SkyRelicsCommand(JavaPlugin plugin, ConfigManager config, CooldownManager cooldownManager) {
        this.plugin = plugin;
        this.config = config;
        this.cooldownManager = cooldownManager;
        this.itemIdKey = new NamespacedKey(plugin, "sky_item_id");
        this.legacyItemIdKey = new NamespacedKey(plugin, "chrono_item_id");
        this.ownerKey = new NamespacedKey(plugin, "sky_owner");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendUsage(sender, label);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender, args);
            case "give" -> handleGive(sender, label, args, false);
            case "giveall" -> handleGive(sender, label, args, true);
            case "info" -> handleInfo(sender, args);
            case "inspect" -> handleInspect(sender);
            case "validate" -> handleValidate(sender);
            case "abilities" -> handleAbilities(sender, args);
            case "cooldown" -> handleCooldown(sender, label, args);
            default -> sender.sendMessage(config.getString("messages.unknown-command"));
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("skyrelics.command.reload")) {
            sender.sendMessage(config.getString("messages.no-permission"));
            return;
        }

        config.reload();
        sender.sendMessage(config.getString("messages.config-reloaded"));
    }

    private void handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("skyrelics.command.list")) {
            sender.sendMessage(config.getString("messages.no-permission"));
            return;
        }

        List<String> itemIds = getItemIds();
        if (itemIds.isEmpty()) {
            sender.sendMessage(config.getString("messages.item-list-empty"));
            return;
        }

        int maxPage = Math.max(1, (int) Math.ceil(itemIds.size() / (double) ITEMS_PER_PAGE));
        int page = args.length >= 2 ? parseInt(args[1], 1, 1, maxPage) : 1;
        int from = (page - 1) * ITEMS_PER_PAGE;
        int to = Math.min(itemIds.size(), from + ITEMS_PER_PAGE);

        sender.sendMessage(color("&6SkyRelics items &7(" + page + "/" + maxPage + "):"));
        for (String itemId : itemIds.subList(from, to)) {
            ConfigurationSection section = getItemSection(itemId);
            String material = section == null ? "UNKNOWN" : section.getString("material", "DIAMOND");
            String name = config.getString("items." + itemId + ".name");
            sender.sendMessage(color("&e" + itemId + " &7- &f" + material + " &8| " + name));
        }
    }

    private void handleGive(CommandSender sender, String label, String[] args, boolean allPlayers) {
        String permission = allPlayers ? "skyrelics.command.giveall" : "skyrelics.command.give";
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(config.getString("messages.no-permission"));
            return;
        }

        int itemArgIndex = allPlayers ? 1 : 2;
        if ((!allPlayers && args.length < 3) || (allPlayers && args.length < 2)) {
            sender.sendMessage(color("&eUsage: /" + label + (allPlayers ? " giveall <item_id> [amount]" : " give <player> <item_id> [amount]")));
            return;
        }

        String itemId = args[itemArgIndex].toLowerCase();
        ConfigurationSection itemSection = getItemSection(itemId);
        if (itemSection == null) {
            sender.sendMessage(config.getString("messages.item-not-found").replace("{item}", itemId));
            return;
        }

        int amountArgIndex = allPlayers ? 2 : 3;
        int amount = args.length > amountArgIndex ? parseInt(args[amountArgIndex], 1, 1, 64) : 1;
        List<Player> targets = new ArrayList<>();
        if (allPlayers) {
            targets.addAll(Bukkit.getOnlinePlayers());
        } else {
            Player player = Bukkit.getPlayer(args[1]);
            if (player == null) {
                sender.sendMessage(config.getString("messages.player-not-found"));
                return;
            }
            targets.add(player);
        }

        if (targets.isEmpty()) {
            sender.sendMessage(color("&cThere are no online players."));
            return;
        }

        for (Player target : targets) {
            ItemStack item = createItem(sender, itemId, itemSection, amount);
            if (item == null) {
                return;
            }

            if (itemSection.getBoolean("bind-on-give", false)) {
                bindItemTo(item, target.getUniqueId());
            }

            target.getInventory().addItem(item).values()
                    .forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            target.sendMessage(config.getString("messages.item-given")
                    .replace("{item}", itemId)
                    .replace("{amount}", String.valueOf(amount)));
        }

        if (allPlayers) {
            sender.sendMessage(color("&aIssued &e" + itemId + " x" + amount + " &ato &e" + targets.size() + " &aonline players."));
        } else {
            Player target = targets.get(0);
            sender.sendMessage(config.getString("messages.item-given-sender")
                    .replace("{player}", target.getName())
                    .replace("{item}", itemId)
                    .replace("{amount}", String.valueOf(amount)));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("skyrelics.command.info")) {
            sender.sendMessage(config.getString("messages.no-permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(color("&eUsage: /sr info <item_id>"));
            return;
        }

        String itemId = args[1].toLowerCase();
        ConfigurationSection itemSection = getItemSection(itemId);
        if (itemSection == null) {
            sender.sendMessage(config.getString("messages.item-not-found").replace("{item}", itemId));
            return;
        }

        sender.sendMessage(color("&6SkyRelics item: &e" + itemId));
        sender.sendMessage(color("&7Material: &f" + itemSection.getString("material", "DIAMOND")));
        sender.sendMessage(color("&7Name: &f" + config.getString("items." + itemId + ".name")));

        ConfigurationSection enchantments = itemSection.getConfigurationSection("enchantments");
        if (enchantments != null && !enchantments.getKeys(false).isEmpty()) {
            sender.sendMessage(color("&7Enchantments: &f" + enchantments.getKeys(false).stream()
                    .map(key -> key + " " + enchantments.getInt(key))
                    .collect(Collectors.joining(", "))));
        }

        ConfigurationSection abilities = itemSection.getConfigurationSection("abilities");
        if (abilities == null || abilities.getKeys(false).isEmpty()) {
            sender.sendMessage(color("&7Abilities: &8none"));
            return;
        }

        sender.sendMessage(color("&7Abilities:"));
        for (String trigger : abilities.getKeys(false)) {
            List<Map<?, ?>> triggerAbilities = config.getConfig().getMapList("items." + itemId + ".abilities." + trigger);
            String types = triggerAbilities.stream()
                    .map(ability -> {
                        Object type = ability.get("type");
                        return type == null ? "UNKNOWN" : String.valueOf(type);
                    })
                    .collect(Collectors.joining(", "));
            sender.sendMessage(color("&8- &e" + trigger + "&7: &f" + types));
        }
    }

    private void handleInspect(CommandSender sender) {
        if (!sender.hasPermission("skyrelics.command.inspect")) {
            sender.sendMessage(config.getString("messages.no-permission"));
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cOnly players can inspect held items."));
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        String itemId = getCustomItemId(item);
        if (itemId.isEmpty()) {
            sender.sendMessage(color("&cThis item is not a SkyRelics item."));
            return;
        }

        sender.sendMessage(color("&aHeld SkyRelics item: &e" + itemId));
        String owner = item.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (owner != null) {
            sender.sendMessage(color("&7Owner UUID: &f" + owner));
        }
    }

    private void handleValidate(CommandSender sender) {
        if (!sender.hasPermission("skyrelics.command.validate")) {
            sender.sendMessage(config.getString("messages.no-permission"));
            return;
        }

        List<String> problems = validateConfig();
        if (problems.isEmpty()) {
            sender.sendMessage(color("&aSkyRelics config validation passed."));
            return;
        }

        sender.sendMessage(color("&cSkyRelics config has " + problems.size() + " issue(s):"));
        problems.stream().limit(12).forEach(problem -> sender.sendMessage(color("&7- &c" + problem)));
        if (problems.size() > 12) {
            sender.sendMessage(color("&7...and " + (problems.size() - 12) + " more."));
        }
    }

    private void handleAbilities(CommandSender sender, String[] args) {
        if (!sender.hasPermission("skyrelics.command.abilities")) {
            sender.sendMessage(config.getString("messages.no-permission"));
            return;
        }

        List<String> values = ABILITY_TYPES.stream()
                .sorted(String::compareToIgnoreCase)
                .collect(Collectors.toList());
        int maxPage = Math.max(1, (int) Math.ceil(values.size() / 10.0));
        int page = args.length >= 2 ? parseInt(args[1], 1, 1, maxPage) : 1;
        int from = (page - 1) * 10;
        int to = Math.min(values.size(), from + 10);

        sender.sendMessage(color("&6SkyRelics abilities &7(" + page + "/" + maxPage + "):"));
        for (String ability : values.subList(from, to)) {
            sender.sendMessage(color("&e" + ability + " &7- trigger: &f" + recommendedTrigger(ability)));
        }
    }

    private void handleCooldown(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("skyrelics.command.cooldown")) {
            sender.sendMessage(config.getString("messages.no-permission"));
            return;
        }

        if (args.length < 3 || !args[1].equalsIgnoreCase("clear")) {
            sender.sendMessage(color("&eUsage: /" + label + " cooldown clear <player> [key|all]"));
            return;
        }

        Player player = Bukkit.getPlayer(args[2]);
        if (player == null) {
            sender.sendMessage(config.getString("messages.player-not-found"));
            return;
        }

        String key = args.length >= 4 ? args[3] : "all";
        if (key.equalsIgnoreCase("all")) {
            int removed = cooldownManager.resetAll(player.getUniqueId());
            sender.sendMessage(color("&aCleared &e" + removed + " &acooldown(s) for &e" + player.getName() + "&a."));
            return;
        }

        cooldownManager.reset(player.getUniqueId(), key);
        sender.sendMessage(color("&aCleared cooldown &e" + key + " &afor &e" + player.getName() + "&a."));
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
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, itemId);
        item.setItemMeta(meta);

        applyEnchantments(item, itemSection.getConfigurationSection("enchantments"));
        return item;
    }

    private void bindItemTo(ItemStack item, UUID uuid) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta() == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, uuid.toString());
        item.setItemMeta(meta);
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(color("&6SkyRelics commands:"));
        sender.sendMessage(color("&7/" + label + " give <player> <item_id> [amount]"));
        sender.sendMessage(color("&7/" + label + " giveall <item_id> [amount]"));
        sender.sendMessage(color("&7/" + label + " list [page]"));
        sender.sendMessage(color("&7/" + label + " info <item_id>"));
        sender.sendMessage(color("&7/" + label + " inspect"));
        sender.sendMessage(color("&7/" + label + " validate"));
        sender.sendMessage(color("&7/" + label + " abilities [page]"));
        sender.sendMessage(color("&7/" + label + " cooldown clear <player> [key|all]"));
        sender.sendMessage(color("&7/" + label + " reload"));
    }

    private List<String> validateConfig() {
        List<String> problems = new ArrayList<>();
        ConfigurationSection items = config.getConfig().getConfigurationSection("items");
        if (items == null) {
            problems.add("Missing items section.");
            return problems;
        }

        Set<String> displayNames = new HashSet<>();
        for (String itemId : items.getKeys(false)) {
            String path = "items." + itemId;
            ConfigurationSection itemSection = items.getConfigurationSection(itemId);
            if (itemSection == null) {
                problems.add(path + " is not a section.");
                continue;
            }

            String materialName = itemSection.getString("material", "");
            Material material = Material.matchMaterial(materialName);
            if (material == null || !material.isItem()) {
                problems.add(path + ".material is invalid: " + materialName);
            }

            String name = config.getString(path + ".name");
            if (name.isEmpty()) {
                problems.add(path + ".name is empty.");
            } else if (!displayNames.add(name)) {
                problems.add(path + ".name duplicates another item display name.");
            }

            validateEnchantments(path, itemSection.getConfigurationSection("enchantments"), problems);
            validateAbilities(path, itemSection.getConfigurationSection("abilities"), problems);
            validateRestrictions(path, itemSection, problems);
        }

        return problems;
    }

    private void validateRestrictions(String itemPath, ConfigurationSection itemSection, List<String> problems) {
        for (String mode : itemSection.getStringList("gamemodes")) {
            try {
                org.bukkit.GameMode.valueOf(mode.toUpperCase());
            } catch (IllegalArgumentException exception) {
                problems.add(itemPath + ".gamemodes contains unknown game mode: " + mode);
            }
        }

        for (String biome : itemSection.getStringList("biomes")) {
            try {
                org.bukkit.block.Biome.valueOf(biome.toUpperCase());
            } catch (IllegalArgumentException exception) {
                problems.add(itemPath + ".biomes contains unknown biome: " + biome);
            }
        }

        if (itemSection.contains("min-y") && itemSection.contains("max-y")
                && itemSection.getInt("min-y") > itemSection.getInt("max-y")) {
            problems.add(itemPath + ".min-y is greater than max-y.");
        }
    }

    private void validateEnchantments(String itemPath, ConfigurationSection enchantments, List<String> problems) {
        if (enchantments == null) {
            return;
        }

        for (String enchantmentName : enchantments.getKeys(false)) {
            if (Enchantment.getByName(enchantmentName.toUpperCase()) == null) {
                problems.add(itemPath + ".enchantments." + enchantmentName + " is unknown.");
            }
        }
    }

    private void validateAbilities(String itemPath, ConfigurationSection abilities, List<String> problems) {
        if (abilities == null) {
            return;
        }

        for (String trigger : abilities.getKeys(false)) {
            if (!ABILITY_TRIGGERS.contains(trigger)) {
                problems.add(itemPath + ".abilities." + trigger + " is not a known trigger.");
            }

            List<Map<?, ?>> entries = config.getConfig().getMapList(itemPath + ".abilities." + trigger);
            if (entries.isEmpty()) {
                problems.add(itemPath + ".abilities." + trigger + " has no ability entries.");
                continue;
            }

            for (int i = 0; i < entries.size(); i++) {
                Object typeValue = entries.get(i).get("type");
                String type = (typeValue == null ? "" : String.valueOf(typeValue)).toUpperCase();
                if (type.isEmpty()) {
                    problems.add(itemPath + ".abilities." + trigger + "[" + i + "] has no type.");
                } else if (!ABILITY_TYPES.contains(type)) {
                    problems.add(itemPath + ".abilities." + trigger + "[" + i + "] has unknown type: " + type);
                }
            }
        }
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

    private String getCustomItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta() == null) {
            return "";
        }

        ItemMeta meta = item.getItemMeta();
        String itemId = meta.getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
        if (itemId != null && getItemSection(itemId) != null) {
            return itemId;
        }

        String legacyItemId = meta.getPersistentDataContainer().get(legacyItemIdKey, PersistentDataType.STRING);
        if (legacyItemId != null && getItemSection(legacyItemId) != null) {
            return legacyItemId;
        }

        return "";
    }

    private ConfigurationSection getItemSection(String itemId) {
        return config.getConfig().getConfigurationSection("items." + itemId);
    }

    private List<String> getItemIds() {
        ConfigurationSection items = config.getConfig().getConfigurationSection("items");
        if (items == null) {
            return Collections.emptyList();
        }

        return items.getKeys(false).stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    private int parseInt(String value, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> commands = new ArrayList<>();
            commands.add("help");
            if (sender.hasPermission("skyrelics.command.list")) commands.add("list");
            if (sender.hasPermission("skyrelics.command.give")) commands.add("give");
            if (sender.hasPermission("skyrelics.command.giveall")) commands.add("giveall");
            if (sender.hasPermission("skyrelics.command.info")) commands.add("info");
            if (sender.hasPermission("skyrelics.command.inspect")) commands.add("inspect");
            if (sender.hasPermission("skyrelics.command.validate")) commands.add("validate");
            if (sender.hasPermission("skyrelics.command.abilities")) commands.add("abilities");
            if (sender.hasPermission("skyrelics.command.cooldown")) commands.add("cooldown");
            if (sender.hasPermission("skyrelics.command.reload")) commands.add("reload");
            return filter(args[0], commands);
        }

        String subCommand = args[0].toLowerCase();
        if (subCommand.equals("give") && sender.hasPermission("skyrelics.command.give")) {
            return completeGive(args, false);
        }

        if (subCommand.equals("giveall") && sender.hasPermission("skyrelics.command.giveall")) {
            return completeGive(args, true);
        }

        if (subCommand.equals("info") && sender.hasPermission("skyrelics.command.info")) {
            return args.length == 2 ? filter(args[1], getItemIds()) : Collections.emptyList();
        }

        if (subCommand.equals("list") && sender.hasPermission("skyrelics.command.list")) {
            return args.length == 2 ? filter(args[1], Arrays.asList("1", "2", "3", "4", "5")) : Collections.emptyList();
        }

        if (subCommand.equals("cooldown") && sender.hasPermission("skyrelics.command.cooldown")) {
            return completeCooldown(args);
        }

        if (subCommand.equals("abilities") && sender.hasPermission("skyrelics.command.abilities")) {
            return args.length == 2 ? filter(args[1], Arrays.asList("1", "2", "3", "4", "5", "6")) : Collections.emptyList();
        }

        return Collections.emptyList();
    }

    private String recommendedTrigger(String ability) {
        return switch (ability) {
            case "VEIN_MINE", "AUTO_SMELT", "ORE_MAGNET", "RESOURCE_SWEEP", "MINING_BURST",
                    "TUNNEL_BREAK", "DIG_3X3", "AREA_BREAK", "TREASURE_CHANCE", "GRAVE_DIGGER",
                    "FARMER_LUCK", "COMPOST_TOUCH", "LOG_SPLINTER", "EXPLOSIVE_MINE", "FORTUNE_SURGE",
                    "REPAIR_ON_ORE", "STONE_SKIN", "TREE_FELLER", "SOIL_BLESSING", "HARVEST_RADIUS",
                    "REPLANT", "MUD_ARMOR" -> "break-block";
            case "BLEEDING_STRIKE", "ARMOR_CRACK", "EXECUTE", "RAGE_STACKS", "SHIELD_BREAKER",
                    "WILD_CHARGE", "CRITICAL_DASH", "LIFESTEAL_CHOP", "VAMPIRIC_HIT", "FIRE_SLASH",
                    "ICE_SLASH", "CHAIN_LIGHTNING", "AOE_SWEEP", "FEAR", "MARK_TARGET",
                    "COMBO_STRIKE" -> "attack-entity";
            case "SOUL_STEAL" -> "kill-entity";
            case "POISON_THORNS" -> "receive-damage";
            default -> "right-click";
        };
    }

    private List<String> completeGive(String[] args, boolean allPlayers) {
        if (!allPlayers && args.length == 2) {
            return filter(args[1], onlinePlayerNames());
        }

        int itemArgIndex = allPlayers ? 2 : 3;
        if (args.length == itemArgIndex) {
            return filter(args[itemArgIndex - 1], getItemIds());
        }

        int amountArgIndex = allPlayers ? 3 : 4;
        if (args.length == amountArgIndex) {
            return filter(args[amountArgIndex - 1], Arrays.asList("1", "8", "16", "32", "64"));
        }

        return Collections.emptyList();
    }

    private List<String> completeCooldown(String[] args) {
        if (args.length == 2) {
            return filter(args[1], Collections.singletonList("clear"));
        }

        if (args.length == 3 && args[1].equalsIgnoreCase("clear")) {
            return filter(args[2], onlinePlayerNames());
        }

        if (args.length == 4 && args[1].equalsIgnoreCase("clear")) {
            List<String> values = new ArrayList<>();
            values.add("all");
            values.add("relic");
            values.add("herobrine");
            values.add("item:<item_id>:<trigger>:<index>");
            return filter(args[3], values);
        }

        return Collections.emptyList();
    }

    private List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .sorted(String::compareToIgnoreCase)
                .collect(Collectors.toList());
    }

    private List<String> filter(String prefix, List<String> values) {
        String lowerPrefix = prefix.toLowerCase();
        return values.stream()
                .filter(value -> value.toLowerCase().startsWith(lowerPrefix))
                .collect(Collectors.toList());
    }
}

package me.jolikki.skyrelics.listener;

import me.jolikki.skyrelics.manager.ConfigManager;
import me.jolikki.skyrelics.manager.CooldownManager;
import me.jolikki.skyrelics.manager.PermissionManager;
import me.jolikki.skyrelics.relic.RelicMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RelicListener implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, RelicMode> playerModes = new HashMap<>();
    private final CooldownManager cooldownManager;
    private final ConfigManager config;
    private final PermissionManager permissionManager;

    public RelicListener(JavaPlugin plugin, CooldownManager cooldownManager, ConfigManager config, PermissionManager permissionManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.config = config;
        this.permissionManager = permissionManager;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.WITHER_SKELETON_SKULL) {
            return;
        }

        Block block = event.getBlockPlaced();
        World world = block.getWorld();
        Block blockBelow = world.getBlockAt(block.getX(), block.getY() - 1, block.getZ());

        if (blockBelow.getType() == Material.DIAMOND_BLOCK) {
            event.getPlayer().sendMessage(config.getString("messages.ritual-lightning"));
            event.getPlayer().getWorld().strikeLightning(event.getPlayer().getLocation());
        }
    }

    @EventHandler
    public void onHero(BlockIgniteEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        if (!isHerobrineStructure(event.getBlock())) {
            return;
        }

        String permission = config.getString("permissions.herobrine");
        if (permissionManager.hasPermission(player, permission)) {
            player.sendMessage(config.getString("messages.hero_already_summoned"));
            return;
        }

        if (cooldownManager.hasCooldown(player.getUniqueId(), "herobrine")) {
            long remaining = cooldownManager.getRemaining(player.getUniqueId(), "herobrine") / 1000;
            player.sendMessage(config.getString("messages.hero_spawn") + remaining + " СЃРµРє.");
            return;
        }

        long cooldown = config.getLong("cooldowns.hero_cooldown");
        if (!cooldownManager.tryUse(player.getUniqueId(), "herobrine", cooldown)) {
            return;
        }

        player.getWorld().strikeLightning(player.getLocation());
        event.getBlock().getWorld().strikeLightning(event.getBlock().getLocation());

        player.sendMessage(config.getString("messages.hero_spawn_chat"));
        player.sendTitle(
                config.getString("titles.summon.title"),
                config.getString("titles.summon.subtitle"),
                config.getInt("titles.summon.fadeIn"),
                config.getInt("titles.summon.stay"),
                config.getInt("titles.summon.fadeOut")
        );

        player.playSound(
                player.getLocation(),
                config.getString("sounds.summon.sound"),
                config.getFloat("sounds.summon.volume"),
                config.getFloat("sounds.summon.pitch")
        );

        permissionManager.addPermission(player.getUniqueId(), permission);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (!event.getAction().toString().contains("RIGHT_CLICK")) {
            return;
        }

        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        String itemId = getCustomItemId(item);
        if (!itemId.isEmpty()) {
            String action = player.isSneaking() ? "shift-right-click" : "right-click";
            if (executeConfiguredAbilities(player, item, itemId, action)) {
                event.setCancelled(true);
            }
            return;
        }

        useLegacyRelic(event, player, item);
    }

    private boolean isHerobrineStructure(Block fireBlock) {
        return fireBlock.getRelative(0, -1, 0).getType() == Material.NETHERRACK
                && fireBlock.getRelative(0, -2, 0).getType() == Material.GOLD_BLOCK
                && fireBlock.getRelative(1, -2, 0).getType() == Material.GOLD_BLOCK
                && fireBlock.getRelative(-1, -2, 0).getType() == Material.GOLD_BLOCK
                && fireBlock.getRelative(1, -2, 1).getType() == Material.GOLD_BLOCK
                && fireBlock.getRelative(-1, -2, 1).getType() == Material.GOLD_BLOCK
                && fireBlock.getRelative(0, -2, 1).getType() == Material.GOLD_BLOCK
                && fireBlock.getRelative(0, -2, -1).getType() == Material.GOLD_BLOCK
                && fireBlock.getRelative(-1, -2, -1).getType() == Material.GOLD_BLOCK
                && fireBlock.getRelative(1, -2, -1).getType() == Material.GOLD_BLOCK
                && fireBlock.getRelative(1, -1, -1).getType() == Material.REDSTONE_TORCH
                && fireBlock.getRelative(1, -1, 1).getType() == Material.REDSTONE_TORCH
                && fireBlock.getRelative(-1, -1, -1).getType() == Material.REDSTONE_TORCH
                && fireBlock.getRelative(-1, -1, 1).getType() == Material.REDSTONE_TORCH;
    }

    private boolean isRelic(ItemStack item) {
        if (item == null) {
            return false;
        }

        Material material = Material.matchMaterial(config.getString("relic.material"));
        if (material == null) {
            material = Material.DIAMOND;
        }

        if (item.getType() != material) {
            return false;
        }

        if (!item.hasItemMeta() || item.getItemMeta() == null || !item.getItemMeta().hasDisplayName()) {
            return false;
        }

        return item.getItemMeta().getDisplayName().equals(config.getString("relic.name"));
    }

    private String getCustomItemId(ItemStack item) {
        if (item == null) {
            return "";
        }

        if (!item.hasItemMeta() || item.getItemMeta() == null) {
            return "";
        }

        ItemMeta meta = item.getItemMeta();
        NamespacedKey itemIdKey = new NamespacedKey(plugin, "sky_item_id");
        String itemId = meta.getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
        if (itemId != null && config.getConfig().isConfigurationSection("items." + itemId)) {
            return itemId;
        }

        NamespacedKey legacyItemIdKey = new NamespacedKey(plugin, "chrono_item_id");
        String legacyItemId = meta.getPersistentDataContainer().get(legacyItemIdKey, PersistentDataType.STRING);
        if (legacyItemId != null && config.getConfig().isConfigurationSection("items." + legacyItemId)) {
            return legacyItemId;
        }

        NamespacedKey oldPickaxeKey = new NamespacedKey(plugin, "chrono_pickaxe");
        if (meta.getPersistentDataContainer().has(oldPickaxeKey, PersistentDataType.BYTE)
                && config.getConfig().isConfigurationSection("items.chrono_pickaxe")) {
            return "chrono_pickaxe";
        }

        ConfigurationSection items = config.getConfig().getConfigurationSection("items");
        if (items == null || !meta.hasDisplayName()) {
            return "";
        }

        for (String configuredId : items.getKeys(false)) {
            String name = config.getString("items." + configuredId + ".name");
            if (meta.getDisplayName().equals(name)) {
                return configuredId;
            }
        }

        return "";
    }

    private boolean executeConfiguredAbilities(Player player, ItemStack item, String itemId, String action) {
        ConfigurationSection itemSection = config.getConfig().getConfigurationSection("items." + itemId);
        if (itemSection == null) {
            return false;
        }

        List<Map<?, ?>> abilities = config.getConfig().getMapList("items." + itemId + ".abilities." + action);
        if (abilities.isEmpty()) {
            return false;
        }

        for (int i = 0; i < abilities.size(); i++) {
            Map<?, ?> ability = abilities.get(i);
            long cooldown = getLong(ability, "cooldown", 0);
            String cooldownKey = "item:" + itemId + ":" + action + ":" + i;
            if (cooldown > 0 && !cooldownManager.tryUse(player.getUniqueId(), cooldownKey, cooldown)) {
                long remaining = cooldownManager.getRemaining(player.getUniqueId(), cooldownKey) / 1000;
                player.sendMessage(config.getString("messages.item-cooldown")
                        .replace("{seconds}", String.valueOf(remaining)));
                return true;
            }

            executeAbility(player, item, ability);
        }

        if (itemSection.getBoolean("consume-on-use", false)) {
            consumeOneRelic(player, item);
        }

        return true;
    }

    private void executeAbility(Player player, ItemStack item, Map<?, ?> ability) {
        String type = getString(ability, "type", "").toUpperCase();

        switch (type) {
            case "TOGGLE_ENCHANT" -> toggleEnchant(player, item, ability);
            case "POTION_EFFECTS" -> applyPotionEffects(player, ability);
            case "TELEPORT_TARGET" -> teleportToTargetBlock(player, getInt(ability, "distance", 50));
            case "DASH" -> dash(player, ability);
            case "HEAL" -> heal(player, ability);
            case "LIGHTNING_TARGET" -> lightningTarget(player, ability);
            case "PARTICLE" -> spawnParticle(player, ability);
            case "SOUND" -> playSound(player, ability);
            case "MESSAGE" -> sendConfiguredMessage(player, ability);
            default -> {
            }
        }
    }

    private void toggleEnchant(Player player, ItemStack item, Map<?, ?> ability) {
        Enchantment first = Enchantment.getByName(getString(ability, "first-enchant", "LOOT_BONUS_BLOCKS").toUpperCase());
        Enchantment second = Enchantment.getByName(getString(ability, "second-enchant", "SILK_TOUCH").toUpperCase());
        if (first == null || second == null) {
            return;
        }

        int firstLevel = Math.max(1, getInt(ability, "first-level", 3));
        int secondLevel = Math.max(1, getInt(ability, "second-level", 1));

        if (item.containsEnchantment(second)) {
            item.removeEnchantment(second);
            item.addUnsafeEnchantment(first, firstLevel);
            sendMessage(player, getString(ability, "first-message", ""));
            return;
        }

        item.removeEnchantment(first);
        item.addUnsafeEnchantment(second, secondLevel);
        sendMessage(player, getString(ability, "second-message", ""));
    }

    @SuppressWarnings("unchecked")
    private void applyPotionEffects(Player player, Map<?, ?> ability) {
        Object effectsValue = ability.get("effects");
        if (!(effectsValue instanceof List<?> effects)) {
            return;
        }

        for (Object effectValue : effects) {
            if (!(effectValue instanceof Map<?, ?> effect)) {
                continue;
            }

            PotionEffectType type = PotionEffectType.getByName(getString(effect, "type", "SPEED").toUpperCase());
            if (type == null) {
                continue;
            }

            int duration = Math.max(1, getInt(effect, "duration", 100));
            int amplifier = Math.max(0, getInt(effect, "amplifier", 0));
            player.addPotionEffect(new PotionEffect(type, duration, amplifier));
        }

        sendMessage(player, getString(ability, "message", ""));
    }

    private void dash(Player player, Map<?, ?> ability) {
        double power = getDouble(ability, "power", 1.2);
        double y = getDouble(ability, "y", 0.25);
        Vector velocity = player.getLocation().getDirection().normalize().multiply(power);
        velocity.setY(y);
        player.setVelocity(velocity);
        sendMessage(player, getString(ability, "message", ""));
    }

    private void heal(Player player, Map<?, ?> ability) {
        double health = getDouble(ability, "health", 0);
        int food = getInt(ability, "food", 0);

        if (health > 0) {
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + health));
        }

        if (food > 0) {
            player.setFoodLevel(Math.min(20, player.getFoodLevel() + food));
        }

        sendMessage(player, getString(ability, "message", ""));
    }

    private void lightningTarget(Player player, Map<?, ?> ability) {
        var result = player.rayTraceBlocks(getInt(ability, "distance", 40));
        if (result == null || result.getHitBlock() == null) {
            player.sendMessage(config.getString("messages.teleport-no-target"));
            return;
        }

        if (getBoolean(ability, "damage", false)) {
            player.getWorld().strikeLightning(result.getHitBlock().getLocation());
        } else {
            player.getWorld().strikeLightningEffect(result.getHitBlock().getLocation());
        }

        sendMessage(player, getString(ability, "message", ""));
    }

    private void spawnParticle(Player player, Map<?, ?> ability) {
        try {
            Particle particle = Particle.valueOf(getString(ability, "particle", "PORTAL").toUpperCase());
            player.getWorld().spawnParticle(
                    particle,
                    player.getLocation().add(0, getDouble(ability, "y-offset", 1), 0),
                    getInt(ability, "count", 30),
                    getDouble(ability, "offset-x", 0.4),
                    getDouble(ability, "offset-y", 0.4),
                    getDouble(ability, "offset-z", 0.4),
                    getDouble(ability, "speed", 0.05)
            );
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void playSound(Player player, Map<?, ?> ability) {
        try {
            Sound sound = Sound.valueOf(getString(ability, "sound", "ENTITY_EXPERIENCE_ORB_PICKUP").toUpperCase());
            player.playSound(
                    player.getLocation(),
                    sound,
                    (float) getDouble(ability, "volume", 1),
                    (float) getDouble(ability, "pitch", 1)
            );
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void sendConfiguredMessage(Player player, Map<?, ?> ability) {
        sendMessage(player, getString(ability, "message", ""));
    }

    private void sendMessage(Player player, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', message));
    }

    private String getString(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private int getInt(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long getLong(Map<?, ?> map, String key, long fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }

        try {
            return value == null ? fallback : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private double getDouble(Map<?, ?> map, String key, double fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }

        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean getBoolean(Map<?, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }

        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private void useLegacyRelic(PlayerInteractEvent event, Player player, ItemStack item) {
        if (!isRelic(item)) {
            return;
        }

        event.setCancelled(true);

        if (player.isSneaking()) {
            RelicMode next = getNextMode(player.getUniqueId());
            playerModes.put(player.getUniqueId(), next);
            player.sendMessage(config.getString("messages.relic-mode").replace("{mode}", next.name()));
            return;
        }

        long cooldown = plugin.getConfig().getLong("cooldowns.relic_cooldown");
        if (!cooldownManager.tryUse(player.getUniqueId(), "relic", cooldown)) {
            long remaining = cooldownManager.getRemaining(player.getUniqueId(), "relic") / 1000;
            player.sendMessage(config.getString("messages.relic-cooldown")
                    .replace("{seconds}", String.valueOf(remaining)));
            return;
        }

        player.sendMessage(config.getString("messages.relic-used"));
        RelicMode mode = playerModes.getOrDefault(player.getUniqueId(), RelicMode.JUMP);

        switch (mode) {
            case JUMP -> player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 100, 10));
            case SPEED -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2));
            case TIME_BLINK -> teleportToTargetBlock(player);
        }

        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);

        if (config.getBoolean("relic.consume-on-use")) {
            consumeOneRelic(player, item);
        }
    }

    private RelicMode getNextMode(UUID uuid) {
        RelicMode current = playerModes.getOrDefault(uuid, RelicMode.JUMP);

        return switch (current) {
            case JUMP -> RelicMode.SPEED;
            case SPEED -> RelicMode.TIME_BLINK;
            case TIME_BLINK -> RelicMode.JUMP;
        };
    }

    private void teleportToTargetBlock(Player player) {
        teleportToTargetBlock(player, 50);
    }

    private void teleportToTargetBlock(Player player, int distance) {
        var result = player.rayTraceBlocks(distance);

        if (result == null || result.getHitBlock() == null) {
            player.sendMessage(config.getString("messages.teleport-no-target"));
            return;
        }

        var location = result.getHitBlock().getLocation().add(0.5, 1, 0.5);
        player.teleport(location);
    }

    private void consumeOneRelic(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            return;
        }

        player.getInventory().setItemInMainHand(null);
    }
}


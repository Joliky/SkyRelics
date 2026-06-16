package me.jolikki.skyrelics.listener;

import me.jolikki.skyrelics.manager.ConfigManager;
import me.jolikki.skyrelics.manager.CooldownManager;
import me.jolikki.skyrelics.manager.PermissionManager;
import me.jolikki.skyrelics.relic.RelicMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Biome;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class RelicListener implements Listener {

    private static final Set<Material> ORES = Set.of(
            Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE, Material.REDSTONE_ORE,
            Material.LAPIS_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE, Material.NETHER_QUARTZ_ORE
    );
    private static final Set<Material> LOGS = Set.of(
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
            Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.STRIPPED_OAK_LOG,
            Material.STRIPPED_SPRUCE_LOG, Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_JUNGLE_LOG,
            Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_DARK_OAK_LOG
    );
    private static final Set<Material> CROPS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART
    );
    private static final Set<Material> SOILS = Set.of(
            Material.DIRT, Material.GRASS_BLOCK, Material.COARSE_DIRT, Material.PODZOL
    );
    private static final Set<Material> PATH_BLOCKS = Set.of(
            Material.DIRT, Material.GRASS_BLOCK, Material.COARSE_DIRT, Material.PODZOL
    );
    private static final int MAX_AREA_RADIUS = 4;
    private static final int MAX_SCAN_RADIUS = 12;
    private static final int MAX_EXTRA_BLOCKS = 128;
    private static final int MAX_CHAIN_TARGETS = 8;
    private static final int MAX_TEMP_BLOCKS = 64;
    private static final long CACHE_REFRESH_INTERVAL_MS = 1000L;

    private final JavaPlugin plugin;
    private final NamespacedKey itemIdKey;
    private final NamespacedKey legacyItemIdKey;
    private final NamespacedKey oldPickaxeKey;
    private final NamespacedKey ownerKey;
    private final Map<UUID, RelicMode> playerModes = new HashMap<>();
    private final Map<UUID, Integer> rageStacks = new HashMap<>();
    private final Map<UUID, Long> rageUntil = new HashMap<>();
    private final Map<UUID, Integer> comboStacks = new HashMap<>();
    private final Map<UUID, Long> comboUntil = new HashMap<>();
    private final Map<UUID, MarkData> markedTargets = new HashMap<>();
    private final Map<String, String> itemNameCache = new HashMap<>();
    private final CooldownManager cooldownManager;
    private final ConfigManager config;
    private final PermissionManager permissionManager;
    private final Random random = new Random();
    private long itemNameCacheRefreshAt;

    public RelicListener(JavaPlugin plugin, CooldownManager cooldownManager, ConfigManager config, PermissionManager permissionManager) {
        this.plugin = plugin;
        this.itemIdKey = new NamespacedKey(plugin, "sky_item_id");
        this.legacyItemIdKey = new NamespacedKey(plugin, "chrono_item_id");
        this.oldPickaxeKey = new NamespacedKey(plugin, "chrono_pickaxe");
        this.ownerKey = new NamespacedKey(plugin, "sky_owner");
        this.cooldownManager = cooldownManager;
        this.config = config;
        this.permissionManager = permissionManager;
        startCleanupTask();
    }

    @EventHandler(ignoreCancelled = true)
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

    @EventHandler(ignoreCancelled = true)
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
            player.sendMessage(config.getString("messages.hero_spawn") + remaining + " sec.");
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

    @EventHandler(ignoreCancelled = true)
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
            if (executeConfiguredAbilities(player, item, itemId, action, AbilityContext.empty())) {
                event.setCancelled(true);
            }
            return;
        }

        useLegacyRelic(event, player, item);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        String itemId = getCustomItemId(item);
        if (itemId.isEmpty()) {
            return;
        }

        executeConfiguredAbilities(event.getPlayer(), item, itemId, "break-block", AbilityContext.forBlock(event.getBlock(), event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getEntity().getUniqueId() != null) {
            MarkData mark = markedTargets.get(event.getEntity().getUniqueId());
            if (mark != null && mark.expiresAt > System.currentTimeMillis()) {
                event.setDamage(event.getDamage() * mark.multiplier);
            } else if (mark != null) {
                markedTargets.remove(event.getEntity().getUniqueId());
            }
        }

        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        String itemId = getCustomItemId(item);
        if (itemId.isEmpty()) {
            return;
        }

        executeConfiguredAbilities(player, item, itemId, "attack-entity", AbilityContext.forTarget(target, event));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        ItemStack item = killer.getInventory().getItemInMainHand();
        String itemId = getCustomItemId(item);
        if (itemId.isEmpty()) {
            return;
        }

        executeConfiguredAbilities(killer, item, itemId, "kill-entity", AbilityContext.forTarget(event.getEntity(), null));
    }

    @EventHandler(ignoreCancelled = true)
    public void onReceiveDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        String itemId = getCustomItemId(item);
        if (itemId.isEmpty()) {
            return;
        }

        LivingEntity attacker = null;
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof LivingEntity living) {
            attacker = living;
        }

        executeConfiguredAbilities(player, item, itemId, "receive-damage", AbilityContext.forTarget(attacker, event));
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
        String itemId = meta.getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
        if (itemId != null && config.getConfig().isConfigurationSection("items." + itemId)) {
            return itemId;
        }

        String legacyItemId = meta.getPersistentDataContainer().get(legacyItemIdKey, PersistentDataType.STRING);
        if (legacyItemId != null && config.getConfig().isConfigurationSection("items." + legacyItemId)) {
            return legacyItemId;
        }

        if (meta.getPersistentDataContainer().has(oldPickaxeKey, PersistentDataType.BYTE)
                && config.getConfig().isConfigurationSection("items.chrono_pickaxe")) {
            return "chrono_pickaxe";
        }

        if (!meta.hasDisplayName()) {
            return "";
        }

        refreshItemNameCacheIfNeeded();
        return itemNameCache.getOrDefault(meta.getDisplayName(), "");
    }

    private void refreshItemNameCacheIfNeeded() {
        long now = System.currentTimeMillis();
        if (now < itemNameCacheRefreshAt) {
            return;
        }

        itemNameCacheRefreshAt = now + CACHE_REFRESH_INTERVAL_MS;
        itemNameCache.clear();

        ConfigurationSection items = config.getConfig().getConfigurationSection("items");
        if (items == null) {
            return;
        }

        for (String configuredId : items.getKeys(false)) {
            String name = config.getString("items." + configuredId + ".name");
            if (!name.isEmpty()) {
                itemNameCache.put(name, configuredId);
            }
        }
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                cooldownManager.cleanupExpired();
                markedTargets.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
                rageUntil.entrySet().removeIf(entry -> {
                    boolean expired = entry.getValue() <= now;
                    if (expired) {
                        rageStacks.remove(entry.getKey());
                    }
                    return expired;
                });
                comboUntil.entrySet().removeIf(entry -> {
                    boolean expired = entry.getValue() <= now;
                    if (expired) {
                        comboStacks.remove(entry.getKey());
                    }
                    return expired;
                });
            }
        }.runTaskTimer(plugin, 20L * 60L, 20L * 60L);
    }

    private boolean executeConfiguredAbilities(Player player, ItemStack item, String itemId, String action, AbilityContext context) {
        ConfigurationSection itemSection = config.getConfig().getConfigurationSection("items." + itemId);
        if (itemSection == null) {
            return false;
        }

        List<Map<?, ?>> abilities = config.getConfig().getMapList("items." + itemId + ".abilities." + action);
        if (abilities.isEmpty()) {
            return false;
        }

        if (!canUseItemHere(player, item, itemSection)) {
            return true;
        }

        boolean handled = false;
        for (int i = 0; i < abilities.size(); i++) {
            Map<?, ?> ability = abilities.get(i);
            if (!rollChance(ability)) {
                continue;
            }

            long cooldown = getLong(ability, "cooldown", 0);
            String cooldownKey = "item:" + itemId + ":" + action + ":" + i;
            if (cooldown > 0 && !cooldownManager.tryUse(player.getUniqueId(), cooldownKey, cooldown)) {
                if (getBoolean(ability, "show-cooldown", true)) {
                    long remaining = cooldownManager.getRemaining(player.getUniqueId(), cooldownKey) / 1000;
                    player.sendMessage(config.getString("messages.item-cooldown")
                            .replace("{seconds}", String.valueOf(remaining)));
                }
                handled = true;
                continue;
            }

            executeAbility(player, item, ability, context);
            handled = true;
        }

        if (handled && (action.equals("right-click") || action.equals("shift-right-click")) && itemSection.getBoolean("consume-on-use", false)) {
            consumeOneRelic(player, item);
        }

        return handled || !abilities.isEmpty();
    }

    private boolean canUseItemHere(Player player, ItemStack item, ConfigurationSection itemSection) {
        if (!checkAndApplyOwner(player, item, itemSection)) {
            return false;
        }

        String permission = itemSection.getString("permission", "");
        if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
            sendMessage(player, getRestrictionMessage("item-restricted-permission", "&cYou cannot use this relic."));
            return false;
        }

        List<String> worlds = itemSection.getStringList("worlds");
        String worldName = player.getWorld().getName();
        if (!worlds.isEmpty() && worlds.stream().noneMatch(world -> world.equalsIgnoreCase(worldName))) {
            sendMessage(player, getRestrictionMessage("item-restricted-world", "&cThis relic cannot be used in this world."));
            return false;
        }

        List<String> blockedWorlds = itemSection.getStringList("blocked-worlds");
        if (blockedWorlds.stream().anyMatch(world -> world.equalsIgnoreCase(worldName))) {
            sendMessage(player, getRestrictionMessage("item-restricted-world", "&cThis relic cannot be used in this world."));
            return false;
        }

        List<String> gamemodes = itemSection.getStringList("gamemodes");
        if (!gamemodes.isEmpty() && gamemodes.stream().noneMatch(mode -> mode.equalsIgnoreCase(player.getGameMode().name()))) {
            sendMessage(player, getRestrictionMessage("item-restricted-gamemode", "&cThis relic cannot be used in your game mode."));
            return false;
        }

        List<String> biomes = itemSection.getStringList("biomes");
        if (!biomes.isEmpty()) {
            Biome biome = player.getLocation().getBlock().getBiome();
            if (biomes.stream().noneMatch(value -> value.equalsIgnoreCase(biome.name()))) {
                sendMessage(player, getRestrictionMessage("item-restricted-biome", "&cThis relic cannot be used in this biome."));
                return false;
            }
        }

        int y = player.getLocation().getBlockY();
        if (itemSection.contains("min-y") && y < itemSection.getInt("min-y")) {
            sendMessage(player, getRestrictionMessage("item-restricted-height", "&cThis relic cannot be used at this height."));
            return false;
        }

        if (itemSection.contains("max-y") && y > itemSection.getInt("max-y")) {
            sendMessage(player, getRestrictionMessage("item-restricted-height", "&cThis relic cannot be used at this height."));
            return false;
        }

        return true;
    }

    private boolean checkAndApplyOwner(Player player, ItemStack item, ConfigurationSection itemSection) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta() == null) {
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        String owner = meta.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (owner != null && itemSection.getBoolean("owner-only", false)
                && !owner.equals(player.getUniqueId().toString())) {
            sendMessage(player, getRestrictionMessage("item-restricted-owner", "&cThis relic is bound to another player."));
            return false;
        }

        if (owner == null && itemSection.getBoolean("bind-on-first-use", false)) {
            meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            item.setItemMeta(meta);
            sendMessage(player, getRestrictionMessage("item-bound", "&aThis relic is now bound to you."));
        }

        return true;
    }

    private String getRestrictionMessage(String key, String fallback) {
        String value = config.getString("messages." + key);
        return value.isEmpty() ? fallback : value;
    }

    private void executeAbility(Player player, ItemStack item, Map<?, ?> ability, AbilityContext context) {
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

            case "VEIN_MINE" -> veinMine(player, item, context.block, ability);
            case "AUTO_SMELT" -> autoSmelt(context.block, context.blockBreakEvent, ability);
            case "ORE_MAGNET", "RESOURCE_SWEEP" -> magnetDrops(player, ability);
            case "MINING_BURST" -> addEffect(player, PotionEffectType.FAST_DIGGING, ability, 100, 1);
            case "TUNNEL_BREAK", "DIG_3X3", "AREA_BREAK" -> areaBreak(player, item, context.block, ability);
            case "TREASURE_CHANCE", "GRAVE_DIGGER", "FARMER_LUCK", "COMPOST_TOUCH", "LOG_SPLINTER" -> bonusDrop(player, context.block, ability);
            case "EXPLOSIVE_MINE" -> explosiveMine(player, context.block, ability);
            case "FORTUNE_SURGE" -> fortuneSurge(player, context.block, ability);
            case "REPAIR_ON_ORE" -> repairOnBlock(item, context.block, ability, ORES);
            case "STONE_SKIN", "MUD_ARMOR", "BEE_FRIEND" -> addEffect(player, PotionEffectType.DAMAGE_RESISTANCE, ability, 100, 0);

            case "TREE_FELLER" -> treeFeller(player, item, context.block, ability);
            case "BLEEDING_STRIKE" -> bleedingStrike(player, context.target, ability);
            case "ARMOR_CRACK" -> armorCrack(context.target, ability);
            case "EXECUTE" -> executeStrike(context.damageByEntityEvent, context.target, ability);
            case "BATTLE_ROAR" -> battleRoar(player, ability);
            case "RAGE_STACKS" -> rageStacks(player, context.damageByEntityEvent, ability);
            case "SHIELD_BREAKER" -> shieldBreaker(context.damageByEntityEvent, context.target, ability);
            case "WILD_CHARGE", "CRITICAL_DASH" -> chargeToTarget(player, context.target, context.damageByEntityEvent, ability);
            case "LIFESTEAL_CHOP", "VAMPIRIC_HIT" -> lifesteal(player, context.damageByEntityEvent, ability);

            case "BURROW" -> burrow(player, ability);
            case "QUICKSAND_TRAP" -> areaEffect(player, context.blockLocationOrPlayer(player), ability, PotionEffectType.SLOW, Particle.FALLING_DUST);
            case "SOIL_BLESSING" -> soilBlessing(player, context.block, ability);
            case "SAND_WAVE" -> sandWave(player, ability);
            case "PATH_MAKER" -> pathMaker(player, ability);
            case "EARTH_WALL" -> earthWall(player, ability);

            case "HARVEST_RADIUS" -> harvestRadius(player, item, context.block, context.blockBreakEvent, ability);
            case "REPLANT" -> replant(player, context.block, context.blockBreakEvent);
            case "GROWTH_AURA" -> growthAura(player, ability);
            case "NATURE_HEAL" -> natureHeal(player, ability);
            case "POISON_THORNS" -> poisonThorns(context.target, ability);
            case "VINE_ROOT" -> vineRoot(resolveTarget(player, context, ability), ability);
            case "LIFE_BLOOM" -> lifeBloom(player, ability);

            case "FIRE_SLASH" -> fireSlash(context.target, ability);
            case "ICE_SLASH" -> iceSlash(context.target, ability);
            case "CHAIN_LIGHTNING" -> chainLightning(player, context.target, ability);
            case "SOUL_STEAL" -> soulSteal(player, ability);
            case "AOE_SWEEP" -> aoeSweep(player, context.target, context.damageByEntityEvent, ability);
            case "FEAR" -> fear(player, context.target, ability);
            case "MARK_TARGET" -> markTarget(context.target, ability);
            case "COMBO_STRIKE" -> comboStrike(player, context.damageByEntityEvent, ability);
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
        spawnParticle(player.getLocation().add(0, getDouble(ability, "y-offset", 1), 0), ability);
    }

    private void spawnParticle(Location location, Map<?, ?> ability) {
        try {
            Particle particle = Particle.valueOf(getString(ability, "particle", "PORTAL").toUpperCase());
            location.getWorld().spawnParticle(
                    particle,
                    location,
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
        playSound(player.getLocation(), ability);
    }

    private void playSound(Location location, Map<?, ?> ability) {
        try {
            Sound sound = Sound.valueOf(getString(ability, "sound", "ENTITY_EXPERIENCE_ORB_PICKUP").toUpperCase());
            location.getWorld().playSound(
                    location,
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

    private void veinMine(Player player, ItemStack item, Block origin, Map<?, ?> ability) {
        if (origin == null || !ORES.contains(origin.getType())) {
            return;
        }

        int maxBlocks = limitInt(getInt(ability, "max-blocks", 16), 1, MAX_EXTRA_BLOCKS);
        Material targetType = origin.getType();
        Set<Block> visited = new HashSet<>();
        List<Block> queue = new ArrayList<>();
        queue.add(origin);

        while (!queue.isEmpty() && visited.size() < maxBlocks) {
            Block block = queue.remove(0);
            if (!visited.add(block) || block.getType() != targetType) {
                continue;
            }

            if (!block.equals(origin)) {
                breakExtraBlock(player, item, block);
            }

            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x != 0 || y != 0 || z != 0) {
                            queue.add(block.getRelative(x, y, z));
                        }
                    }
                }
            }
        }

        player.getWorld().spawnParticle(Particle.CRIT, origin.getLocation().add(0.5, 0.5, 0.5), 20, 0.5, 0.5, 0.5, 0.05);
    }

    private void autoSmelt(Block block, BlockBreakEvent event, Map<?, ?> ability) {
        if (block == null) {
            return;
        }

        Material result = smeltedMaterial(block.getType());
        if (result == null) {
            return;
        }

        if (event != null) {
            event.setDropItems(false);
        }
        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(result, getInt(ability, "amount", 1)));
    }

    private void magnetDrops(Player player, Map<?, ?> ability) {
        double radius = limitDouble(getDouble(ability, "radius", 6), 1, MAX_SCAN_RADIUS);
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity.getType() == EntityType.DROPPED_ITEM) {
                entity.teleport(player.getLocation());
            }
        }
    }

    private void areaBreak(Player player, ItemStack item, Block origin, Map<?, ?> ability) {
        if (origin == null) {
            return;
        }

        int radius = limitInt(getInt(ability, "radius", 1), 1, MAX_AREA_RADIUS);
        int maxBlocks = limitInt(getInt(ability, "max-blocks", 9), 1, MAX_EXTRA_BLOCKS);
        Material originType = origin.getType();
        int broken = 0;

        for (int x = -radius; x <= radius && broken < maxBlocks; x++) {
            for (int y = -radius; y <= radius && broken < maxBlocks; y++) {
                for (int z = -radius; z <= radius && broken < maxBlocks; z++) {
                    Block block = origin.getRelative(x, y, z);
                    if (block.equals(origin) || block.getType() == Material.AIR || block.getType().getHardness() < 0) {
                        continue;
                    }
                    if (getBoolean(ability, "same-type-only", false) && block.getType() != originType) {
                        continue;
                    }

                    if (breakExtraBlock(player, item, block)) {
                        broken++;
                    }
                }
            }
        }

        player.getWorld().spawnParticle(Particle.BLOCK_CRACK, origin.getLocation().add(0.5, 0.5, 0.5), 30, 0.7, 0.7, 0.7, origin.getBlockData());
    }

    private void bonusDrop(Player player, Block block, Map<?, ?> ability) {
        if (block == null) {
            return;
        }

        Material material = Material.matchMaterial(getString(ability, "drop", "EXPERIENCE_BOTTLE"));
        if (material == null) {
            material = Material.EXPERIENCE_BOTTLE;
        }

        int amount = Math.max(1, getInt(ability, "amount", 1));
        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(material, amount));
        sendMessage(player, getString(ability, "message", ""));
    }

    private void explosiveMine(Player player, Block block, Map<?, ?> ability) {
        if (block == null) {
            return;
        }

        float power = (float) getDouble(ability, "power", 1.5);
        boolean breakBlocks = getBoolean(ability, "break-blocks", false);
        block.getWorld().createExplosion(block.getLocation().add(0.5, 0.5, 0.5), power, false, breakBlocks, player);
    }

    private void fortuneSurge(Player player, Block block, Map<?, ?> ability) {
        if (block == null || !ORES.contains(block.getType())) {
            return;
        }

        Material drop = oreDrop(block.getType());
        if (drop == null) {
            return;
        }

        int amount = Math.max(1, getInt(ability, "bonus", 1));
        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(drop, amount));
        player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, block.getLocation().add(0.5, 0.5, 0.5), 12, 0.4, 0.4, 0.4, 0.03);
    }

    private void repairOnBlock(ItemStack item, Block block, Map<?, ?> ability, Set<Material> allowedBlocks) {
        if (item == null || block == null || !allowedBlocks.contains(block.getType()) || !(item.getItemMeta() instanceof Damageable damageable)) {
            return;
        }

        int repair = Math.max(1, getInt(ability, "repair", 2));
        damageable.setDamage(Math.max(0, damageable.getDamage() - repair));
        item.setItemMeta((ItemMeta) damageable);
    }

    private void treeFeller(Player player, ItemStack item, Block origin, Map<?, ?> ability) {
        if (origin == null || !LOGS.contains(origin.getType())) {
            return;
        }

        int maxBlocks = limitInt(getInt(ability, "max-blocks", 64), 1, MAX_EXTRA_BLOCKS);
        Set<Block> visited = new HashSet<>();
        List<Block> queue = new ArrayList<>();
        queue.add(origin);

        while (!queue.isEmpty() && visited.size() < maxBlocks) {
            Block block = queue.remove(0);
            if (!visited.add(block) || !LOGS.contains(block.getType())) {
                continue;
            }

            if (!block.equals(origin)) {
                breakExtraBlock(player, item, block);
            }

            for (int x = -1; x <= 1; x++) {
                for (int y = 0; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        queue.add(block.getRelative(x, y, z));
                    }
                }
            }
        }

        player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, origin.getLocation().add(0.5, 1, 0.5), 20, 0.5, 1, 0.5, 0.05);
    }

    private void bleedingStrike(Player player, LivingEntity target, Map<?, ?> ability) {
        if (target == null || target.isDead()) {
            return;
        }

        int ticks = Math.max(1, getInt(ability, "ticks", 4));
        double damage = Math.max(0.1, getDouble(ability, "damage", 1.0));
        new BukkitRunnable() {
            private int left = ticks;

            @Override
            public void run() {
                if (left-- <= 0 || target.isDead()) {
                    cancel();
                    return;
                }
                target.damage(damage, player);
                target.getWorld().spawnParticle(Particle.REDSTONE, target.getLocation().add(0, 1, 0), 8, 0.25, 0.25, 0.25, 0.01);
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void armorCrack(LivingEntity target, Map<?, ?> ability) {
        if (target == null) {
            return;
        }

        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, getInt(ability, "duration", 100), getInt(ability, "amplifier", 0)));
        target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.04);
    }

    private void executeStrike(EntityDamageByEntityEvent event, LivingEntity target, Map<?, ?> ability) {
        if (event == null || target == null) {
            return;
        }

        double threshold = getDouble(ability, "health-percent", 0.25);
        if (target.getHealth() / target.getMaxHealth() <= threshold) {
            event.setDamage(event.getDamage() * getDouble(ability, "multiplier", 1.8));
        }
    }

    private void battleRoar(Player player, Map<?, ?> ability) {
        double radius = limitDouble(getDouble(ability, "radius", 5), 1, MAX_SCAN_RADIUS);
        double power = getDouble(ability, "power", 1.1);
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }

            Vector away = entity.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(power);
            away.setY(0.35);
            living.setVelocity(away);
            living.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, getInt(ability, "duration", 80), 0));
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, getInt(ability, "duration", 80), getInt(ability, "amplifier", 0)));
        playSound(player.getLocation(), Map.of("sound", "ENTITY_ENDER_DRAGON_GROWL", "volume", 0.7, "pitch", 1.4));
    }

    private void rageStacks(Player player, EntityDamageByEntityEvent event, Map<?, ?> ability) {
        if (event == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long keepMs = getLong(ability, "keep-ms", 6000);
        int maxStacks = Math.max(1, getInt(ability, "max-stacks", 5));
        int stacks = rageUntil.getOrDefault(player.getUniqueId(), 0L) < now ? 0 : rageStacks.getOrDefault(player.getUniqueId(), 0);
        stacks = Math.min(maxStacks, stacks + 1);
        rageStacks.put(player.getUniqueId(), stacks);
        rageUntil.put(player.getUniqueId(), now + keepMs);
        event.setDamage(event.getDamage() + stacks * getDouble(ability, "damage-per-stack", 0.3));
    }

    private void shieldBreaker(EntityDamageByEntityEvent event, LivingEntity target, Map<?, ?> ability) {
        if (event == null || !(target instanceof Player victim) || !victim.isBlocking()) {
            return;
        }

        event.setDamage(event.getDamage() * getDouble(ability, "multiplier", 1.4));
        victim.setCooldown(Material.SHIELD, getInt(ability, "shield-cooldown", 80));
        victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0f, 0.8f);
    }

    private void chargeToTarget(Player player, LivingEntity target, EntityDamageByEntityEvent event, Map<?, ?> ability) {
        if (target == null) {
            dash(player, ability);
            return;
        }

        Vector direction = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
        direction.multiply(getDouble(ability, "power", 1.4));
        direction.setY(getDouble(ability, "y", 0.2));
        player.setVelocity(direction);
        if (event != null) {
            event.setDamage(event.getDamage() * getDouble(ability, "damage-multiplier", 1.2));
        }
    }

    private void lifesteal(Player player, EntityDamageByEntityEvent event, Map<?, ?> ability) {
        if (event == null) {
            return;
        }

        double amount = event.getDamage() * getDouble(ability, "percent", 0.25);
        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + amount));
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0.02);
    }

    private void burrow(Player player, Map<?, ?> ability) {
        int distance = Math.max(1, getInt(ability, "distance", 5));
        Location destination = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(distance));
        destination.setY(player.getWorld().getHighestBlockYAt(destination) + 1);
        player.teleport(destination);
        player.getWorld().spawnParticle(Particle.BLOCK_CRACK, player.getLocation(), 35, 0.4, 0.4, 0.4, Material.DIRT.createBlockData());
    }

    private void areaEffect(Player player, Location center, Map<?, ?> ability, PotionEffectType effect, Particle particle) {
        double radius = limitDouble(getDouble(ability, "radius", 4), 1, MAX_SCAN_RADIUS);
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                living.addPotionEffect(new PotionEffect(effect, getInt(ability, "duration", 100), getInt(ability, "amplifier", 1)));
            }
        }
        center.getWorld().spawnParticle(particle, center, 40, radius / 2, 0.2, radius / 2, 0.02);
    }

    private void soilBlessing(Player player, Block block, Map<?, ?> ability) {
        if (block == null) {
            return;
        }

        int radius = limitInt(getInt(ability, "radius", 2), 1, MAX_AREA_RADIUS);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Block target = block.getRelative(x, 0, z);
                if (SOILS.contains(target.getType()) && canChangeBlock(player, target, Material.FARMLAND)) {
                    target.setType(Material.FARMLAND);
                }
            }
        }
    }

    private void sandWave(Player player, Map<?, ?> ability) {
        double radius = limitDouble(getDouble(ability, "radius", 5), 1, MAX_SCAN_RADIUS);
        double power = getDouble(ability, "power", 1.0);
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity living && !entity.equals(player)) {
                Vector away = entity.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(power);
                away.setY(0.25);
                living.setVelocity(away);
                living.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, getInt(ability, "duration", 40), 0));
            }
        }
        player.getWorld().spawnParticle(Particle.FALLING_DUST, player.getLocation(), 60, radius / 2, 0.2, radius / 2, Material.SAND.createBlockData());
    }

    private void pathMaker(Player player, Map<?, ?> ability) {
        int length = Math.max(1, getInt(ability, "length", 6));
        Vector direction = player.getLocation().getDirection().setY(0).normalize();
        Location cursor = player.getLocation();
        for (int i = 0; i < length; i++) {
            cursor = cursor.add(direction);
            Block block = cursor.getBlock().getRelative(BlockFace.DOWN);
            if (PATH_BLOCKS.contains(block.getType()) && canChangeBlock(player, block, Material.GRASS_PATH)) {
                block.setType(Material.GRASS_PATH);
            }
        }
    }

    private void earthWall(Player player, Map<?, ?> ability) {
        int width = limitInt(getInt(ability, "width", 3), 1, 9);
        int height = limitInt(getInt(ability, "height", 2), 1, 6);
        int ticks = limitInt(getInt(ability, "duration", 100), 20, 20 * 30);
        Material material = Material.matchMaterial(getString(ability, "material", "COBBLESTONE"));
        if (material == null) {
            material = Material.COBBLESTONE;
        }

        Location base = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(3));
        Vector side = player.getLocation().getDirection().crossProduct(new Vector(0, 1, 0)).normalize();
        Map<Block, Material> previous = new HashMap<>();
        for (int x = -width / 2; x <= width / 2; x++) {
            for (int y = 0; y < height; y++) {
                Block block = base.clone().add(side.clone().multiply(x)).add(0, y, 0).getBlock();
                if (previous.size() < MAX_TEMP_BLOCKS && block.getType() == Material.AIR && canChangeBlock(player, block, material)) {
                    previous.put(block, block.getType());
                    block.setType(material);
                }
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                previous.forEach(Block::setType);
            }
        }.runTaskLater(plugin, ticks);
    }

    private void harvestRadius(Player player, ItemStack item, Block origin, BlockBreakEvent event, Map<?, ?> ability) {
        if (origin == null) {
            return;
        }

        int radius = limitInt(getInt(ability, "radius", 3), 1, MAX_AREA_RADIUS);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Block block = origin.getRelative(x, 0, z);
                harvestCrop(player, item, block, block.equals(origin) ? event : null);
            }
        }
    }

    private void replant(Player player, Block block, BlockBreakEvent event) {
        if (block == null || !CROPS.contains(block.getType())) {
            return;
        }

        harvestCrop(player, player.getInventory().getItemInMainHand(), block, event);
    }

    private void harvestCrop(Player player, ItemStack item, Block block, BlockBreakEvent originalEvent) {
        if (!isMatureCrop(block)) {
            return;
        }

        if (originalEvent == null && !canBreakExtraBlock(player, block)) {
            return;
        }

        if (originalEvent != null) {
            originalEvent.setCancelled(true);
            originalEvent.setDropItems(false);
        }

        Collection<ItemStack> drops = block.getDrops(item);
        drops.forEach(drop -> block.getWorld().dropItemNaturally(block.getLocation(), drop));
        resetCrop(block);
    }

    private void growthAura(Player player, Map<?, ?> ability) {
        int radius = limitInt(getInt(ability, "radius", 4), 1, MAX_AREA_RADIUS);
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = player.getLocation().getBlock().getRelative(x, y, z);
                    if (block.getBlockData() instanceof Ageable ageable && ageable.getAge() < ageable.getMaximumAge()) {
                        ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + getInt(ability, "growth", 1)));
                        block.setBlockData(ageable);
                    }
                }
            }
        }
        player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation(), 30, radius / 2.0, 0.5, radius / 2.0, 0.02);
    }

    private void natureHeal(Player player, Map<?, ?> ability) {
        int radius = limitInt(getInt(ability, "radius", 4), 1, MAX_SCAN_RADIUS);
        int plants = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Material type = player.getLocation().getBlock().getRelative(x, 0, z).getType();
                if (CROPS.contains(type) || type.name().contains("LEAVES") || type.name().contains("FLOWER")) {
                    plants++;
                }
            }
        }
        if (plants > 0) {
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + getDouble(ability, "health", 2)));
        }
    }

    private void poisonThorns(LivingEntity attacker, Map<?, ?> ability) {
        if (attacker == null) {
            return;
        }

        attacker.addPotionEffect(new PotionEffect(PotionEffectType.POISON, getInt(ability, "duration", 80), getInt(ability, "amplifier", 0)));
    }

    private void vineRoot(LivingEntity target, Map<?, ?> ability) {
        if (target == null) {
            return;
        }

        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, getInt(ability, "duration", 80), getInt(ability, "amplifier", 4)));
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, getInt(ability, "duration", 80), 128));
        target.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, target.getLocation(), 20, 0.5, 0.1, 0.5, 0.02);
    }

    private LivingEntity resolveTarget(Player player, AbilityContext context, Map<?, ?> ability) {
        if (context.target != null) {
            return context.target;
        }

        double distance = limitDouble(getDouble(ability, "distance", 12), 1, MAX_SCAN_RADIUS);
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        LivingEntity best = null;
        double bestDistance = distance;

        for (Entity entity : player.getNearbyEntities(distance, distance, distance)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) {
                continue;
            }

            Vector toEntity = entity.getLocation().add(0, entity.getHeight() / 2.0, 0).toVector().subtract(eye.toVector());
            double projection = toEntity.dot(direction);
            if (projection < 0 || projection > distance) {
                continue;
            }

            double missDistance = toEntity.subtract(direction.clone().multiply(projection)).length();
            if (missDistance <= 1.2 && projection < bestDistance) {
                best = living;
                bestDistance = projection;
            }
        }

        return best;
    }

    private void lifeBloom(Player player, Map<?, ?> ability) {
        int pulses = limitInt(getInt(ability, "pulses", 5), 1, 10);
        int radius = limitInt(getInt(ability, "radius", 4), 1, MAX_SCAN_RADIUS);
        double health = getDouble(ability, "health", 1.0);
        new BukkitRunnable() {
            private int left = pulses;

            @Override
            public void run() {
                if (left-- <= 0 || !player.isOnline()) {
                    cancel();
                    return;
                }
                for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius)) {
                    if (entity instanceof Player ally) {
                        ally.setHealth(Math.min(ally.getMaxHealth(), ally.getHealth() + health));
                    }
                }
                player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation(), 35, radius / 2.0, 0.4, radius / 2.0, 0.02);
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void fireSlash(LivingEntity target, Map<?, ?> ability) {
        if (target != null) {
            target.setFireTicks(getInt(ability, "fire-ticks", 100));
        }
    }

    private void iceSlash(LivingEntity target, Map<?, ?> ability) {
        if (target != null) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, getInt(ability, "duration", 100), getInt(ability, "amplifier", 1)));
            target.getWorld().spawnParticle(Particle.SNOW_SHOVEL, target.getLocation().add(0, 1, 0), 25, 0.4, 0.4, 0.4, 0.02);
        }
    }

    private void chainLightning(Player player, LivingEntity target, Map<?, ?> ability) {
        if (target == null) {
            return;
        }

        int chains = limitInt(getInt(ability, "chains", 3), 1, MAX_CHAIN_TARGETS);
        double radius = limitDouble(getDouble(ability, "radius", 6), 1, MAX_SCAN_RADIUS);
        double damage = getDouble(ability, "damage", 3);
        LivingEntity current = target;
        Set<UUID> hit = new HashSet<>();
        for (int i = 0; i < chains && current != null; i++) {
            hit.add(current.getUniqueId());
            current.getWorld().strikeLightningEffect(current.getLocation());
            current.damage(damage, player);
            LivingEntity next = null;
            for (Entity entity : current.getNearbyEntities(radius, radius, radius)) {
                if (entity instanceof LivingEntity living && !living.equals(player) && !hit.contains(living.getUniqueId())) {
                    next = living;
                    break;
                }
            }
            current = next;
        }
    }

    private void soulSteal(Player player, Map<?, ?> ability) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, getInt(ability, "duration", 120), getInt(ability, "amplifier", 0)));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, getInt(ability, "duration", 120), 0));
        player.getWorld().spawnParticle(Particle.SOUL, player.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.04);
    }

    private void aoeSweep(Player player, LivingEntity target, EntityDamageByEntityEvent event, Map<?, ?> ability) {
        Location center = target == null ? player.getLocation() : target.getLocation();
        double radius = limitDouble(getDouble(ability, "radius", 3), 1, MAX_SCAN_RADIUS);
        double damage = event == null ? getDouble(ability, "damage", 3) : event.getDamage() * getDouble(ability, "percent", 0.5);
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof LivingEntity living && !living.equals(player) && !living.equals(target)) {
                living.damage(damage, player);
            }
        }
        center.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center.add(0, 1, 0), 8, radius / 2, 0.2, radius / 2, 0.01);
    }

    private void fear(Player player, LivingEntity target, Map<?, ?> ability) {
        if (target == null) {
            return;
        }

        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, getInt(ability, "duration", 80), 0));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, getInt(ability, "duration", 80), 1));
        Vector away = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(getDouble(ability, "power", 0.8));
        away.setY(0.2);
        target.setVelocity(away);
        if (target instanceof Creature creature) {
            creature.setTarget(null);
        }
    }

    private void markTarget(LivingEntity target, Map<?, ?> ability) {
        if (target == null) {
            return;
        }

        markedTargets.put(target.getUniqueId(), new MarkData(
                getDouble(ability, "multiplier", 1.25),
                System.currentTimeMillis() + getLong(ability, "duration-ms", 6000)
        ));
        target.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, target.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.05);
    }

    private void comboStrike(Player player, EntityDamageByEntityEvent event, Map<?, ?> ability) {
        if (event == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long keepMs = getLong(ability, "keep-ms", 5000);
        int combo = comboUntil.getOrDefault(player.getUniqueId(), 0L) < now ? 0 : comboStacks.getOrDefault(player.getUniqueId(), 0);
        combo++;
        int trigger = Math.max(2, getInt(ability, "trigger", 3));
        if (combo >= trigger) {
            event.setDamage(event.getDamage() * getDouble(ability, "multiplier", 1.8));
            combo = 0;
            player.getWorld().spawnParticle(Particle.CRIT_MAGIC, player.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.05);
        }
        comboStacks.put(player.getUniqueId(), combo);
        comboUntil.put(player.getUniqueId(), now + keepMs);
    }

    private void addEffect(Player player, PotionEffectType type, Map<?, ?> ability, int defaultDuration, int defaultAmplifier) {
        player.addPotionEffect(new PotionEffect(
                type,
                getInt(ability, "duration", defaultDuration),
                getInt(ability, "amplifier", defaultAmplifier)
        ));
    }

    private boolean breakExtraBlock(Player player, ItemStack item, Block block) {
        if (!isSafeExtraBreakBlock(block) || !canBreakExtraBlock(player, block)) {
            return false;
        }

        return block.breakNaturally(item);
    }

    private boolean canBreakExtraBlock(Player player, Block block) {
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        plugin.getServer().getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    private boolean canChangeBlock(Player player, Block block, Material newMaterial) {
        if (block == null || player == null || newMaterial == null || !newMaterial.isBlock()) {
            return false;
        }

        BlockState replacedState = block.getState();
        BlockPlaceEvent event = new BlockPlaceEvent(
                block,
                replacedState,
                block.getRelative(BlockFace.DOWN),
                player.getInventory().getItemInMainHand(),
                player,
                true,
                EquipmentSlot.HAND
        );
        plugin.getServer().getPluginManager().callEvent(event);
        return !event.isCancelled() && event.canBuild();
    }

    private boolean isSafeExtraBreakBlock(Block block) {
        if (block == null || block.getType() == Material.AIR || block.getType().getHardness() < 0) {
            return false;
        }

        if (block.getState() instanceof InventoryHolder) {
            return false;
        }

        String name = block.getType().name();
        return !name.contains("COMMAND_BLOCK")
                && block.getType() != Material.BEDROCK
                && block.getType() != Material.BARRIER
                && block.getType() != Material.END_PORTAL_FRAME;
    }

    private int limitInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double limitDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean isMatureCrop(Block block) {
        return CROPS.contains(block.getType())
                && block.getBlockData() instanceof Ageable ageable
                && ageable.getAge() >= ageable.getMaximumAge();
    }

    private void resetCrop(Block block) {
        if (block.getBlockData() instanceof Ageable ageable) {
            ageable.setAge(0);
            block.setBlockData(ageable);
        }
    }

    private Material smeltedMaterial(Material material) {
        return switch (material) {
            case IRON_ORE -> Material.IRON_INGOT;
            case GOLD_ORE -> Material.GOLD_INGOT;
            case SAND -> Material.GLASS;
            case COBBLESTONE -> Material.STONE;
            default -> null;
        };
    }

    private Material oreDrop(Material material) {
        return switch (material) {
            case COAL_ORE -> Material.COAL;
            case DIAMOND_ORE -> Material.DIAMOND;
            case EMERALD_ORE -> Material.EMERALD;
            case LAPIS_ORE -> Material.LAPIS_LAZULI;
            case REDSTONE_ORE -> Material.REDSTONE;
            case NETHER_QUARTZ_ORE -> Material.QUARTZ;
            case IRON_ORE -> Material.IRON_ORE;
            case GOLD_ORE -> Material.GOLD_ORE;
            default -> null;
        };
    }

    private boolean rollChance(Map<?, ?> ability) {
        double chance = getDouble(ability, "chance", 1.0);
        if (chance > 1) {
            chance = chance / 100.0;
        }
        return chance >= 1.0 || random.nextDouble() <= chance;
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

    private record MarkData(double multiplier, long expiresAt) {
    }

    private static final class AbilityContext {
        private final Block block;
        private final LivingEntity target;
        private final BlockBreakEvent blockBreakEvent;
        private final EntityDamageByEntityEvent damageByEntityEvent;
        private final EntityDamageEvent damageEvent;

        private AbilityContext(Block block, LivingEntity target, BlockBreakEvent blockBreakEvent,
                               EntityDamageByEntityEvent damageByEntityEvent, EntityDamageEvent damageEvent) {
            this.block = block;
            this.target = target;
            this.blockBreakEvent = blockBreakEvent;
            this.damageByEntityEvent = damageByEntityEvent;
            this.damageEvent = damageEvent;
        }

        private static AbilityContext empty() {
            return new AbilityContext(null, null, null, null, null);
        }

        private static AbilityContext forBlock(Block block, BlockBreakEvent event) {
            return new AbilityContext(block, null, event, null, null);
        }

        private static AbilityContext forTarget(LivingEntity target, EntityDamageEvent event) {
            EntityDamageByEntityEvent byEntity = event instanceof EntityDamageByEntityEvent value ? value : null;
            return new AbilityContext(null, target, null, byEntity, event);
        }

        private Location blockLocationOrPlayer(Player player) {
            if (block != null) {
                return block.getLocation().add(0.5, 0.5, 0.5);
            }
            return player.getLocation();
        }
    }
}

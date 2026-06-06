package me.jolikki.skyrelics.manager;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class PermissionManager {

    private final LuckPerms luckPerms;
    private final JavaPlugin plugin;

    public PermissionManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.luckPerms = loadLuckPerms();
    }

    public boolean hasPermission(Player player, String permission) {
        if (luckPerms == null) {
            return player.hasPermission(permission);
        }

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return player.hasPermission(permission);
        }

        return user.getCachedData()
                .getPermissionData()
                .checkPermission(permission)
                .asBoolean();
    }

    public CompletableFuture<Void> addPermission(Player player, String permission) {
        return addPermission(player.getUniqueId(), permission);
    }

    public CompletableFuture<Void> addPermission(UUID uuid, String permission) {
        if (luckPerms == null) {
            plugin.getLogger().warning("LuckPerms is not installed, permission was not saved: " + permission);
            return CompletableFuture.completedFuture(null);
        }

        UserManager userManager = luckPerms.getUserManager();
        CompletableFuture<User> userFuture = userManager.loadUser(uuid);

        return userFuture.thenCompose(user -> {
            PermissionNode node = PermissionNode.builder(permission).build();
            user.data().add(node);
            return userManager.saveUser(user);
        }).orTimeout(5, TimeUnit.SECONDS).exceptionally(throwable -> {
            plugin.getLogger().warning("Failed to add permission '" + permission
                    + "' to player " + uuid + ": " + throwable.getMessage());
            return null;
        });
    }

    public CompletableFuture<Void> addTemporaryPermission(Player player, String permission, long durationSeconds) {
        return addTemporaryPermission(player.getUniqueId(), permission, durationSeconds);
    }

    public CompletableFuture<Void> addTemporaryPermission(UUID uuid, String permission, long durationSeconds) {
        if (luckPerms == null) {
            plugin.getLogger().warning("LuckPerms is not installed, temporary permission was not saved: " + permission);
            return CompletableFuture.completedFuture(null);
        }

        UserManager userManager = luckPerms.getUserManager();
        CompletableFuture<User> userFuture = userManager.loadUser(uuid);

        return userFuture.thenCompose(user -> {
            PermissionNode node = PermissionNode.builder(permission)
                    .expiry(durationSeconds, TimeUnit.SECONDS)
                    .build();

            user.data().add(node);
            return userManager.saveUser(user);
        }).orTimeout(5, TimeUnit.SECONDS).exceptionally(throwable -> {
            plugin.getLogger().warning("Failed to add temporary permission '" + permission
                    + "' to player " + uuid + ": " + throwable.getMessage());
            return null;
        });
    }

    public CompletableFuture<Void> removePermission(Player player, String permission) {
        return removePermission(player.getUniqueId(), permission);
    }

    public CompletableFuture<Void> removePermission(UUID uuid, String permission) {
        if (luckPerms == null) {
            plugin.getLogger().warning("LuckPerms is not installed, permission was not removed: " + permission);
            return CompletableFuture.completedFuture(null);
        }

        UserManager userManager = luckPerms.getUserManager();
        CompletableFuture<User> userFuture = userManager.loadUser(uuid);

        return userFuture.thenCompose(user -> {
            user.data().toCollection().stream()
                    .filter(node -> node instanceof PermissionNode)
                    .map(node -> (PermissionNode) node)
                    .filter(node -> node.getPermission().equals(permission))
                    .forEach(user.data()::remove);

            return userManager.saveUser(user);
        }).orTimeout(5, TimeUnit.SECONDS).exceptionally(throwable -> {
            plugin.getLogger().warning("Failed to remove permission '" + permission
                    + "' from player " + uuid + ": " + throwable.getMessage());
            return null;
        });
    }

    public CompletableFuture<User> getUser(UUID uuid) {
        if (luckPerms == null) {
            return CompletableFuture.completedFuture(null);
        }

        return luckPerms.getUserManager().loadUser(uuid);
    }

    public CompletableFuture<User> getUser(Player player) {
        return getUser(player.getUniqueId());
    }

    public CompletableFuture<Boolean> hasLuckPermsPermission(UUID uuid, String permission) {
        if (luckPerms == null) {
            return CompletableFuture.completedFuture(false);
        }

        return getUser(uuid).thenApply(user -> user != null && user.getCachedData()
                .getPermissionData()
                .checkPermission(permission)
                .asBoolean()).exceptionally(throwable -> {
            plugin.getLogger().warning("Failed to check permission '" + permission
                    + "' for player " + uuid + ": " + throwable.getMessage());
            return false;
        });
    }

    private LuckPerms loadLuckPerms() {
        try {
            return LuckPermsProvider.get();
        } catch (IllegalStateException exception) {
            plugin.getLogger().warning("LuckPerms not found. Some permission features will be disabled.");
            return null;
        }
    }
}


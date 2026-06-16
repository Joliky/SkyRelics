package me.jolikki.skyrelics.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    private final Map<String, Long> cooldowns = new HashMap<>();

    public boolean tryUse(UUID uuid, String type, long cooldownMillis) {
        long now = System.currentTimeMillis();
        cleanupExpired(now);
        String key = makeKey(uuid, type);

        if (cooldowns.containsKey(key)) {
            long cooldownEnd = cooldowns.get(key);
            if (now < cooldownEnd) {
                return false;
            }
        }

        cooldowns.put(key, now + cooldownMillis);
        return true;
    }

    public long getRemaining(UUID uuid, String type) {
        long now = System.currentTimeMillis();
        String key = makeKey(uuid, type);

        if (!cooldowns.containsKey(key)) {
            return 0;
        }

        long cooldownEnd = cooldowns.get(key);
        return Math.max(0, cooldownEnd - now);
    }

    public void reset(UUID uuid, String type) {
        cooldowns.remove(makeKey(uuid, type));
    }

    public int resetAll(UUID uuid) {
        String prefix = uuid + ":";
        int before = cooldowns.size();
        cooldowns.keySet().removeIf(key -> key.startsWith(prefix));
        return before - cooldowns.size();
    }

    public boolean hasCooldown(UUID uuid, String type) {
        return getRemaining(uuid, type) > 0;
    }

    public void cleanupExpired() {
        cleanupExpired(System.currentTimeMillis());
    }

    private void cleanupExpired(long now) {
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private String makeKey(UUID uuid, String type) {
        return uuid + ":" + type;
    }
}


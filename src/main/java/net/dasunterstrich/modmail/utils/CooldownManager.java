package net.dasunterstrich.modmail.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager<T> {
    private final Map<T, Long> cooldowns = new ConcurrentHashMap<>();
    private final long cooldownTimeMillis;

    public CooldownManager(long cooldownTimeMillis) {
        this.cooldownTimeMillis = cooldownTimeMillis;
    }

    public boolean isOnCooldown(T t) {
        if (!cooldowns.containsKey(t)) return false;
        return cooldowns.get(t) > System.currentTimeMillis();
    }

    public void applyCooldown(T t) {
        cooldowns.put(t, System.currentTimeMillis() + cooldownTimeMillis);
    }
}

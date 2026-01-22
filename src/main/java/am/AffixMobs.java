package am;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class AffixMobs extends JavaPlugin {

    // =====================
    // PDC keys
    // =====================
    public NamespacedKey KEY_AFFIXED;
    public NamespacedKey KEY_TIER;
    public NamespacedKey KEY_LAST_COMBAT;
    public NamespacedKey KEY_LAST_SEEN;

    // =====================
    // Config-driven settings
    // =====================
    public final Random rng = new Random();

    public final Set<CreatureSpawnEvent.SpawnReason> allowedSpawnReasons =
            EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class);

    public final List<Integer> tierOrder = new ArrayList<>();
    public final Map<Integer, Integer> tierMaxDistance = new HashMap<>();
    public final Map<Integer, Double> affixChanceByTier = new HashMap<>();
    public final Map<Integer, Double> hpMultByTier = new HashMap<>();

    public int capMaxPerWorld = 25;
    public int capMaxPerChunk = 2;

    // Debug
    public boolean debugForceAffix = false;
    public int debugForceTier = -1;
    public boolean debugLogSpawns = false;

    // =====================
    // Lifecycle
    // =====================
    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Init PDC keys
        KEY_AFFIXED = new NamespacedKey(this, "affixed");
        KEY_TIER = new NamespacedKey(this, "tier");
        KEY_LAST_COMBAT = new NamespacedKey(this, "last_combat");
        KEY_LAST_SEEN = new NamespacedKey(this, "last_seen");

        // Load config
        loadSettings();

        // Register listeners
        getServer().getPluginManager().registerEvents(
                new AffixMobListener(this), this
        );
        getServer().getPluginManager().registerEvents(
                new AffixCombatListener(this), this
        );

        // Start cleanup task
        int intervalSeconds = getConfig().getInt("cleanup.check-interval-seconds", 30);
        long intervalTicks = 20L * intervalSeconds;

        getServer().getScheduler().runTaskTimer(
                this,
                new AffixCleanupTask(this),
                intervalTicks,
                intervalTicks
        );

        getLogger().info("AffixMobs enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("AffixMobs disabled");
    }

    // =====================
    // Helpers
    // =====================
    public boolean isAffixed(org.bukkit.entity.LivingEntity e) {
        return e.getPersistentDataContainer().has(
                KEY_AFFIXED,
                org.bukkit.persistence.PersistentDataType.BYTE
        );
    }

    public void setAffixed(org.bukkit.entity.LivingEntity e) {
        e.getPersistentDataContainer().set(
                KEY_AFFIXED,
                org.bukkit.persistence.PersistentDataType.BYTE,
                (byte) 1
        );
    }

    // =====================
    // Config loading
    // =====================
    private void loadSettings() {
        FileConfiguration c = getConfig();

        // Spawn reasons
        allowedSpawnReasons.clear();
        for (String s : c.getStringList("allow-spawn-reasons")) {
            try {
                allowedSpawnReasons.add(
                        CreatureSpawnEvent.SpawnReason.valueOf(s.toUpperCase(Locale.ROOT))
                );
            } catch (IllegalArgumentException ignored) {
                getLogger().warning("Unknown spawn reason in config: " + s);
            }
        }

        // Caps
        capMaxPerWorld = c.getInt("caps.max-affixed-per-world", 25);
        capMaxPerChunk = c.getInt("caps.max-affixed-per-chunk", 2);

        // Debug
        debugForceAffix = c.getBoolean("debug.force-affix", false);
        debugForceTier = c.getInt("debug.force-tier", -1);
        debugLogSpawns = c.getBoolean("debug.log-spawns", false);

        // Tiers
        tierOrder.clear();
        tierMaxDistance.clear();
        affixChanceByTier.clear();
        hpMultByTier.clear();

        List<Map<?, ?>> tiers = c.getMapList("tiers");
        for (Map<?, ?> raw : tiers) {
            Object idObj = raw.get("id");
            if (!(idObj instanceof Number)) continue;

            int id = ((Number) idObj).intValue();
            int maxDist = raw.get("max-distance") instanceof Number n ? n.intValue() : -1;
            double chance = raw.get("affix-chance") instanceof Number n ? n.doubleValue() : 0.0;
            double hp = raw.get("hp-mult") instanceof Number n ? n.doubleValue() : 1.0;

            tierOrder.add(id);
            tierMaxDistance.put(id, maxDist);
            affixChanceByTier.put(id, chance);
            hpMultByTier.put(id, hp);
        }

        tierOrder.sort(Integer::compareTo);

        getLogger().info(
                "Loaded tiers=" + tierOrder +
                ", caps(world=" + capMaxPerWorld +
                ", chunk=" + capMaxPerChunk + ")" +
                (debugForceAffix ? " [DEBUG force-affix]" : "") +
                (debugForceTier > 0 ? " [DEBUG force-tier=" + debugForceTier + "]" : "") +
                (debugLogSpawns ? " [DEBUG log-spawns]" : "")
        );
    }
}

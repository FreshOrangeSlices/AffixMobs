package am;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class AffixCleanupTask implements Runnable {

    private final AffixMobs plugin;

    public AffixCleanupTask(JavaPlugin plugin) {
        this.plugin = (AffixMobs) plugin;
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("cleanup.enabled", true)) return;

        long now = System.currentTimeMillis();

        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity mob : world.getLivingEntities()) {

                if (!plugin.isAffixed(mob)) continue;

                Integer tier = mob.getPersistentDataContainer()
                        .get(plugin.KEY_TIER, PersistentDataType.INTEGER);
                if (tier == null) continue;

                // ----- Config values -----
                boolean isTier4 = tier == 4;

                int maxDist = plugin.getConfig().getInt(
                        isTier4 ? "cleanup.tier-4.max-distance"
                                : "cleanup.default.max-distance",
                        isTier4 ? 160 : 128
                );

                int idleSeconds = plugin.getConfig().getInt(
                        isTier4 ? "cleanup.tier-4.idle-seconds"
                                : "cleanup.default.idle-seconds",
                        isTier4 ? 300 : 120
                );

                int combatGrace = plugin.getConfig().getInt(
                        "cleanup.default.combat-grace-seconds", 30
                );

                // ----- Distance check -----
                Player nearest = mob.getWorld().getNearbyPlayers(
                        mob.getLocation(), maxDist
                ).stream().findFirst().orElse(null);

                if (nearest != null) {
                    // Player nearby → refresh last seen
                    mob.getPersistentDataContainer().set(
                            plugin.KEY_LAST_SEEN,
                            PersistentDataType.LONG,
                            now
                    );
                    continue;
                }

                // ----- Idle time check -----
                Long lastSeen = mob.getPersistentDataContainer()
                        .get(plugin.KEY_LAST_SEEN, PersistentDataType.LONG);

                if (lastSeen != null && (now - lastSeen) < idleSeconds * 1000L) {
                    continue;
                }

                // ----- Combat grace -----
                Long lastCombat = mob.getPersistentDataContainer()
                        .get(plugin.KEY_LAST_COMBAT, PersistentDataType.LONG);

                if (lastCombat != null && (now - lastCombat) < combatGrace * 1000L) {
                    continue;
                }

                // ----- REMOVE MOB -----
                mob.remove();

                if (plugin.debugLogSpawns) {
                    plugin.getLogger().info(
                            "[Cleanup] Removed abandoned affixed mob (tier " + tier + ")"
                    );
                }
            }
        }
    }
}

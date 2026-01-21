package am;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AffixMobListener implements Listener {

    private final AffixMobs plugin;

    // Allow-list (safe by design)
    private static final Set<EntityType> ALLOWED = EnumSet.of(
            EntityType.ZOMBIE, EntityType.SKELETON,
            EntityType.HUSK, EntityType.DROWNED, EntityType.STRAY,
            EntityType.PIGLIN, EntityType.ZOMBIFIED_PIGLIN,
            EntityType.PIGLIN_BRUTE, EntityType.IRON_GOLEM,
            EntityType.WOLF
    );

    // Caps tracking
    private final Map<UUID, Integer> worldCount = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Long, Integer>> chunkCount = new ConcurrentHashMap<>();

    public AffixMobListener(AffixMobs plugin) {
        this.plugin = plugin;
    }

    // ----- Spawn: roll + apply -----
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!(e.getEntity() instanceof LivingEntity mob)) return;

        // gate: allow-list
        if (!ALLOWED.contains(mob.getType())) return;

        // gate: spawn reasons from config
        if (!plugin.allowedSpawnReasons.contains(e.getSpawnReason())) return;

        // gate: already affixed
        if (plugin.isAffixed(mob)) return;

        // compute tier
        int tier = computeTier(mob.getLocation());
        if (tier <= 0) return;

        // debug: force tier
        if (plugin.debugForceTier > 0) tier = plugin.debugForceTier;

        // roll chance
        boolean affix =
                plugin.debugForceAffix ||
                plugin.rng.nextDouble() < plugin.affixChanceByTier.getOrDefault(tier, 0.0);

        if (plugin.debugLogSpawns) {
            plugin.getLogger().info("[Spawn] " + mob.getType() +
                    " reason=" + e.getSpawnReason() +
                    " tier=" + tier +
                    " roll=" + (affix ? "AFFIX" : "no"));
        }

        if (!affix) return;

        // caps
        if (!canAddAffixed(mob.getWorld(), mob.getLocation().getChunk())) {
            if (plugin.debugLogSpawns) plugin.getLogger().info("[Caps] blocked affix spawn (caps hit)");
            return;
        }

        // apply affix
        plugin.setAffixed(mob);
        setTier(mob, tier);

        applyHealthMult(mob, plugin.hpMultByTier.getOrDefault(tier, 1.0));
        applyName(mob, tier);

        // wolf special: only hostile (angry) wolves, otherwise revert
        if (mob.getType() == EntityType.WOLF) {
            Wolf w = (Wolf) mob;
            if (!w.isAngry()) {
                clearAffixed(mob);
                // did not "add" counts yet, so no need to remove
                return;
            }
        }

        // commit counts
        addAffixed(mob.getWorld(), mob.getLocation().getChunk());
    }

    // ----- Death: decrement caps -----
    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity mob = e.getEntity();
        if (!plugin.isAffixed(mob)) return;

        removeAffixed(mob.getWorld(), mob.getLocation().getChunk());
    }

    // ----- Chunk unload: reconcile caps for affixed mobs in that chunk -----
    @EventHandler(ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent e) {
        Chunk c = e.getChunk();
        World w = c.getWorld();

        int removed = 0;
        for (Entity ent : c.getEntities()) {
            if (!(ent instanceof LivingEntity le)) continue;
            if (!plugin.isAffixed(le)) continue;
            removed++;
        }
        if (removed <= 0) return;

        UUID wid = w.getUID();

        // world counter
        worldCount.compute(wid, (k, v) -> {
            int cur = (v == null) ? 0 : v;
            int nv = cur - removed;
            return nv <= 0 ? null : nv;
        });

        // chunk counter
        long ck = chunkKey(c);
        Map<Long, Integer> map = chunkCount.get(wid);
        if (map != null) {
            map.compute(ck, (k, v) -> {
                int cur = (v == null) ? 0 : v;
                int nv = cur - removed;
                return nv <= 0 ? null : nv;
            });
            if (map.isEmpty()) chunkCount.remove(wid);
        }
    }

    // ----- Tier math -----
    private int computeTier(Location loc) {
        World w = loc.getWorld();
        if (w == null) return -1;

        Location anchor = w.getSpawnLocation();
        double dx = loc.getX() - anchor.getX();
        double dz = loc.getZ() - anchor.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        for (int tier : plugin.tierOrder) {
            int max = plugin.tierMaxDistance.getOrDefault(tier, -1);
            if (max < 0) return tier; // -1 means "and up"
            if (dist <= max) return tier;
        }
        return -1;
    }

    // ----- Visuals / Attributes -----
    private void applyHealthMult(LivingEntity mob, double mult) {
        AttributeInstance max = mob.getAttribute(Attribute.MAX_HEALTH);
        if (max == null) return;

        double base = max.getBaseValue();
        double scaled = Math.max(1.0, base * mult);

        max.setBaseValue(scaled);
        mob.setHealth(scaled); // heal to full after scaling
    }

    private void applyName(LivingEntity mob, int tier) {
        NamedTextColor c = switch (tier) {
            case 1 -> NamedTextColor.GRAY;
            case 2 -> NamedTextColor.GREEN;
            case 3 -> NamedTextColor.GOLD;
            default -> NamedTextColor.RED;
        };

        String niceType = mob.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        niceType = Character.toUpperCase(niceType.charAt(0)) + niceType.substring(1);

        mob.customName(Component.text("Affixed " + niceType, c));
        mob.setCustomNameVisible(true);
    }

    // ----- PDC helpers -----
    private void setTier(LivingEntity e, int tier) {
        e.getPersistentDataContainer().set(plugin.KEY_TIER, PersistentDataType.INTEGER, tier);
    }

    private void clearAffixed(LivingEntity e) {
        e.getPersistentDataContainer().remove(plugin.KEY_AFFIXED);
        e.getPersistentDataContainer().remove(plugin.KEY_TIER);
        e.setCustomName(null);
        e.setCustomNameVisible(false);
    }

    // ----- Caps helpers -----
    private boolean canAddAffixed(World w, Chunk c) {
        int wMax = plugin.capMaxPerWorld;
        int cMax = plugin.capMaxPerChunk;

        int wc = worldCount.getOrDefault(w.getUID(), 0);
        if (wc >= wMax) return false;

        long ck = chunkKey(c);
        Map<Long, Integer> map = chunkCount.computeIfAbsent(w.getUID(), k -> new ConcurrentHashMap<>());
        int cc = map.getOrDefault(ck, 0);
        return cc < cMax;
    }

    private void addAffixed(World w, Chunk c) {
        UUID wid = w.getUID();
        worldCount.merge(wid, 1, Integer::sum);

        long ck = chunkKey(c);
        chunkCount.computeIfAbsent(wid, k -> new ConcurrentHashMap<>())
                .merge(ck, 1, Integer::sum);
    }

    private void removeAffixed(World w, Chunk c) {
        UUID wid = w.getUID();

        worldCount.compute(wid, (k, v) -> {
            if (v == null) return null;
            int nv = v - 1;
            return nv <= 0 ? null : nv;
        });

        long ck = chunkKey(c);
        Map<Long, Integer> map = chunkCount.get(wid);
        if (map == null) return;

        map.compute(ck, (k, v) -> {
            if (v == null) return null;
            int nv = v - 1;
            return nv <= 0 ? null : nv;
        });

        if (map.isEmpty()) chunkCount.remove(wid);
    }

    private long chunkKey(Chunk c) {
        // pack (x,z) into one long
        return (((long) c.getX()) << 32) ^ (c.getZ() & 0xffffffffL);
    }
}

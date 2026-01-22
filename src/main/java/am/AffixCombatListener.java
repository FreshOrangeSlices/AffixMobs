package am;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;

public class AffixCombatListener implements Listener {

    private final AffixMobs plugin;

    public AffixCombatListener(AffixMobs plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity mob)) return;
        if (!(e.getDamager() instanceof Player)) return;

        if (!mob.getPersistentDataContainer().has(
                plugin.KEY_AFFIXED,
                PersistentDataType.BYTE
        )) return;

        mob.getPersistentDataContainer().set(
                plugin.KEY_LAST_COMBAT,
                PersistentDataType.LONG,
                System.currentTimeMillis()
        );
    }
}

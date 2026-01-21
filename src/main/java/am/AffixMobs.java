package am;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class AffixMobs extends JavaPlugin {

    // PDC key(s)
    public NamespacedKey KEY_AFFIXED;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        KEY_AFFIXED = new NamespacedKey(this, "affixed");

        getLogger().info("AffixMobs enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("AffixMobs disabled");
    }

    // Helpers (we’ll use these in Step 8+)
    public boolean isAffixed(LivingEntity e) {
        return e.getPersistentDataContainer().has(KEY_AFFIXED, PersistentDataType.BYTE);
    }

    public void setAffixed(LivingEntity e) {
        e.getPersistentDataContainer().set(KEY_AFFIXED, PersistentDataType.BYTE, (byte) 1);
    }

    public FileConfiguration cfg() {
        return getConfig();
    }
}

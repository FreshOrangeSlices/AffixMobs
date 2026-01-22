package am;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

public class AffixCleanupTask implements Runnable {

    private final JavaPlugin plugin;

    public AffixCleanupTask(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("cleanup.enabled", true)) return;

        // Placeholder — logic comes next step
        // This runs safely every X seconds
    }
}

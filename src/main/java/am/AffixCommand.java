package am;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public class AffixCommand implements CommandExecutor {

    private final AffixMobs plugin;

    public AffixCommand(AffixMobs plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length < 3 || !args[0].equalsIgnoreCase("spawn")) {
            player.sendMessage("/affix spawn <mob> <tier>");
            return true;
        }

        EntityType type;
        try {
            type = EntityType.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("Unknown mob type.");
            return true;
        }

        int tier;
        try {
            tier = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("Tier must be a number.");
            return true;
        }

        Location loc = player.getLocation();
        LivingEntity mob = (LivingEntity) loc.getWorld().spawnEntity(loc, type);

        // Apply affix
        plugin.setAffixed(mob);
        mob.getPersistentDataContainer().set(plugin.KEY_TIER, PersistentDataType.INTEGER, tier);
        mob.getPersistentDataContainer().set(plugin.KEY_LAST_SEEN, PersistentDataType.LONG, System.currentTimeMillis());

        // Health scaling
        double mult = plugin.hpMultByTier.getOrDefault(tier, 1.0);
        AttributeInstance max = mob.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            double scaled = Math.max(1.0, max.getBaseValue() * mult);
            max.setBaseValue(scaled);
            mob.setHealth(scaled);
        }

        // Name
        mob.customName(Component.text("Affixed " + type.name()));
        mob.setCustomNameVisible(true);

        player.sendMessage("Spawned affixed " + type.name() + " (tier " + tier + ")");
        return true;
    }
}

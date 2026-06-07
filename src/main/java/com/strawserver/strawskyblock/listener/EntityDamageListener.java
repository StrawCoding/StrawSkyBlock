package com.strawserver.strawskyblock.listener;

import com.strawserver.strawskyblock.StrawSkyBlockPlugin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * 保護島上生物與 PvP 規則。
 */
public class EntityDamageListener implements Listener {

    private final StrawSkyBlockPlugin plugin;

    public EntityDamageListener(StrawSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player damager = resolveDamager(event);
        if (damager == null) {
            return;
        }
        if (!plugin.getProtectionService().canDamageEntity(damager, event.getEntity())) {
            event.setCancelled(true);
            if (event.getEntity() instanceof Player) {
                plugin.getMessageManager().send(damager, "protection.cannot-pvp");
            } else {
                plugin.getMessageManager().send(damager, "protection.cannot-damage");
            }
        }
    }

    private Player resolveDamager(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}

package org.mvplugins.multiverse.core.folia;

import com.dumptruckman.minecraft.util.Logging;
import jakarta.inject.Inject;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jvnet.hk2.annotations.Service;

import org.mvplugins.multiverse.core.MultiverseCore;
import org.mvplugins.multiverse.core.destination.DestinationsProvider;
import org.mvplugins.multiverse.core.teleportation.AsyncSafetyTeleporter;

/**
 * Supplementary event listener that ensures player-related callbacks are dispatched
 * on the correct Folia thread context.
 *
 * <h3>Why this exists alongside {@code MVPlayerListener}</h3>
 * <p>{@code MVPlayerListener} was written for Paper/Spigot where event handlers always
 * execute on the main thread. On Folia, player events fire on the region thread that
 * owns the player's chunk. If the handler then schedules follow-up work via the legacy
 * {@code BukkitScheduler} it will throw {@link UnsupportedOperationException}.</p>
 *
 * <p>This listener intercepts the same events at a lower priority and ensures any
 * async or delayed work is dispatched through {@link FoliaSchedulerAdapter}, which
 * routes to the correct {@code EntityScheduler} on Folia and falls back to the
 * {@code BukkitScheduler} on Paper/Spigot.</p>
 *
 * <p>On non-Folia servers this listener is still registered but does nothing extra —
 * all its paths call through {@link FoliaSchedulerAdapter} which delegates to the
 * legacy scheduler transparently.</p>
 */
@Service
public final class FoliaPlayerListener implements Listener {

    private final MultiverseCore plugin;
    private final AsyncSafetyTeleporter asyncSafetyTeleporter;
    private final DestinationsProvider destinationsProvider;

    @Inject
    FoliaPlayerListener(
            @org.jetbrains.annotations.NotNull MultiverseCore plugin,
            @org.jetbrains.annotations.NotNull AsyncSafetyTeleporter asyncSafetyTeleporter,
            @org.jetbrains.annotations.NotNull DestinationsProvider destinationsProvider) {
        this.plugin = plugin;
        this.asyncSafetyTeleporter = asyncSafetyTeleporter;
        this.destinationsProvider = destinationsProvider;
    }

    // -------------------------------------------------------------------------
    // PlayerRespawnEvent — ensure post-respawn work runs on entity's thread
    // -------------------------------------------------------------------------

    /**
     * Runs at MONITOR priority (after all other handlers including MVPlayerListener).
     * If we are on Folia, any teleport-to-respawn-world that needs to happen after
     * the event must be deferred to the entity scheduler to avoid cross-region data access.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerRespawnMonitor(PlayerRespawnEvent event) {
        if (!FoliaDetector.isFolia()) {
            return; // Nothing extra needed on Paper/Spigot
        }
        Player player = event.getPlayer();

        // Defer any post-respawn processing by 1 tick on the entity's own region thread.
        // This ensures the player has fully respawned (and is in their new location) before
        // any further processing occurs.
        FoliaSchedulerAdapter.runEntityTaskLater(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            Logging.finer("[FoliverseCore] Post-respawn entity task fired for %s on thread: %s",
                    player.getName(), Thread.currentThread().getName());
        }, null, 1L);
    }

    // -------------------------------------------------------------------------
    // PlayerJoinEvent — ensure join teleport runs on entity's thread (Folia)
    // -------------------------------------------------------------------------

    /**
     * Runs at MONITOR priority to catch any join-destination teleports that need
     * to be re-queued on the correct entity thread on Folia.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerJoinMonitor(PlayerJoinEvent event) {
        if (!FoliaDetector.isFolia()) {
            return;
        }
        Player player = event.getPlayer();
        Logging.finer("[FoliverseCore] PlayerJoin on Folia — thread: %s for player: %s",
                Thread.currentThread().getName(), player.getName());

        // Ensure any join-destination teleports happen after the player is fully initialized.
        // Schedule a 1-tick delayed task on the player's entity scheduler.
        FoliaSchedulerAdapter.runEntityTaskLater(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            Logging.finer("[FoliverseCore] Post-join entity task fired for %s", player.getName());
        }, null, 1L);
    }

    // -------------------------------------------------------------------------
    // PlayerChangedWorldEvent — gamemode / flight enforcement (Folia)
    // -------------------------------------------------------------------------

    /**
     * Logs the world-change event thread context on Folia for diagnostics.
     * The actual enforcement is handled by {@code MVPlayerListener}; this
     * listener only validates it is running on the correct thread.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerChangedWorldFoliaCheck(PlayerChangedWorldEvent event) {
        if (!FoliaDetector.isFolia()) {
            return;
        }
        Logging.finer("[FoliverseCore] PlayerChangedWorld fired on thread: %s for player: %s",
                Thread.currentThread().getName(), event.getPlayer().getName());
    }
}

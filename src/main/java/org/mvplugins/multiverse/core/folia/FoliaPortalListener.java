package org.mvplugins.multiverse.core.folia;

import com.dumptruckman.minecraft.util.Logging;
import jakarta.inject.Inject;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.jvnet.hk2.annotations.Service;

import org.mvplugins.multiverse.core.MultiverseCore;
import org.mvplugins.multiverse.core.teleportation.AsyncSafetyTeleporter;
import org.mvplugins.multiverse.core.world.LoadedMultiverseWorld;
import org.mvplugins.multiverse.core.world.WorldManager;

/**
 * Handles cross-world portal events in a Folia-safe manner.
 *
 * <h2>Why vanilla portals are problematic on Folia</h2>
 * <p>Folia's regionized multithreading means that {@code PlayerPortalEvent} fires on
 * the region thread owning the portal block. If the destination world is ticking in a
 * different region (or hasn't been loaded yet), vanilla portal-link logic can read or
 * write data from another region's thread context, causing undefined behaviour or errors.</p>
 *
 * <h2>What this listener does</h2>
 * <ol>
 *   <li>Intercepts cross-world {@code PlayerPortalEvent}s at LOW priority (before vanilla handling).</li>
 *   <li>Cancels the vanilla portal handling.</li>
 *   <li>Teleports the player to the destination world's spawn using {@link AsyncSafetyTeleporter}
 *       ({@code PaperLib.teleportAsync}), which is Folia-safe.</li>
 * </ol>
 *
 * <p>On non-Folia servers this listener is still registered, but it defers to the
 * normal MV portal handling and only intervenes when vanilla portal data is missing.</p>
 */
@Service
public final class FoliaPortalListener implements Listener {

    private final MultiverseCore plugin;
    private final WorldManager worldManager;
    private final AsyncSafetyTeleporter asyncSafetyTeleporter;

    @Inject
    FoliaPortalListener(
            @org.jetbrains.annotations.NotNull MultiverseCore plugin,
            @org.jetbrains.annotations.NotNull WorldManager worldManager,
            @org.jetbrains.annotations.NotNull AsyncSafetyTeleporter asyncSafetyTeleporter) {
        this.plugin = plugin;
        this.worldManager = worldManager;
        this.asyncSafetyTeleporter = asyncSafetyTeleporter;
    }

    /**
     * Intercepts cross-world portal events.
     * <p>
     * On Folia: Always cancels the vanilla event and manually teleports the player
     * to the correct world spawn using {@code teleportAsync}.
     * </p>
     * <p>
     * On Paper/Spigot: Only intervenes if the destination location or world is null
     * (i.e., the portal has no linked destination yet).
     * </p>
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        // Only handle cross-world portals
        if (to == null || to.getWorld() == null) {
            // No destination — let vanilla/MV handle it normally
            return;
        }
        if (from.getWorld() != null && from.getWorld().equals(to.getWorld())) {
            // Same-world portal, not our concern
            return;
        }

        Player player = event.getPlayer();

        if (FoliaDetector.isFolia()) {
            // On Folia: cancel the vanilla portal event and manually teleport via EntityScheduler
            event.setCancelled(true);

            LoadedMultiverseWorld toMvWorld = worldManager.getLoadedWorld(to.getWorld()).getOrNull();
            if (toMvWorld == null) {
                Logging.warning("[FoliverseCore] Portal destination world '%s' is not managed by Multiverse. "
                        + "Cancelling portal.", to.getWorld().getName());
                return;
            }

            // Use the world's configured spawn as the portal destination
            Location spawnLocation = toMvWorld.getSpawnLocation();

            Logging.fine("[FoliverseCore] Intercepting portal for %s on Folia — teleporting to %s spawn via EntityScheduler",
                    player.getName(), toMvWorld.getName());

            // Teleport asynchronously; PaperLib.teleportAsync handles cross-region boundaries on Folia
            asyncSafetyTeleporter.to(spawnLocation)
                    .checkSafety(true)
                    .teleportSingle(player)
                    .onSuccess(ignored -> Logging.fine(
                            "[FoliverseCore] Portal teleport succeeded for %s to %s",
                            player.getName(), toMvWorld.getName()))
                    .onFailure(failures -> Logging.warning(
                            "[FoliverseCore] Portal teleport failed for %s: %s",
                            player.getName(), failures));
        }
        // On Paper/Spigot: let existing MVPortalListener handle it normally
    }
}

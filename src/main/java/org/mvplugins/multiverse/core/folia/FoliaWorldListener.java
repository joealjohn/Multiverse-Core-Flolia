package org.mvplugins.multiverse.core.folia;

import com.dumptruckman.minecraft.util.Logging;
import jakarta.inject.Inject;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jvnet.hk2.annotations.Service;

import org.mvplugins.multiverse.core.event.world.MVWorldCreatedEvent;
import org.mvplugins.multiverse.core.event.world.MVWorldLoadedEvent;
import org.mvplugins.multiverse.core.event.world.MVWorldUnloadedEvent;

/**
 * Logs Folia-relevant diagnostic information when Multiverse world lifecycle events fire.
 * <p>
 * On Folia, world events fire on the {@code GlobalRegionScheduler} thread. This listener
 * confirms that event handling is running on the expected thread context, which is
 * invaluable for debugging scheduler routing issues.
 * </p>
 */
@Service
public final class FoliaWorldListener implements Listener {

    @Inject
    FoliaWorldListener() { }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onWorldCreated(MVWorldCreatedEvent event) {
        if (!FoliaDetector.isFolia()) {
            return;
        }
        Logging.fine("[FoliverseCore] World '%s' was created — thread: %s",
                event.getWorld().getName(), Thread.currentThread().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onWorldLoaded(MVWorldLoadedEvent event) {
        if (!FoliaDetector.isFolia()) {
            return;
        }
        Logging.fine("[FoliverseCore] World '%s' was loaded — thread: %s",
                event.getWorld().getName(), Thread.currentThread().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onWorldUnloaded(MVWorldUnloadedEvent event) {
        if (!FoliaDetector.isFolia()) {
            return;
        }
        Logging.fine("[FoliverseCore] World '%s' was unloaded — thread: %s",
                event.getWorld().getName(), Thread.currentThread().getName());
    }
}

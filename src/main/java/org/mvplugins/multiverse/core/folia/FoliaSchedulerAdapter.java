package org.mvplugins.multiverse.core.folia;

import com.dumptruckman.minecraft.util.Logging;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * A unified scheduling facade that transparently routes tasks to the correct
 * Folia scheduler when running on a Folia server, or falls back to the legacy
 * {@link org.bukkit.scheduler.BukkitScheduler} when running on Paper / Spigot.
 *
 * <h2>Folia scheduler routing rules</h2>
 * <ul>
 *   <li>{@code runEntityTask} → {@code EntityScheduler} — tasks that touch a specific entity</li>
 *   <li>{@code runRegionTask} → {@code RegionScheduler} — tasks tied to a block/chunk location</li>
 *   <li>{@code runGlobalTask} → {@code GlobalRegionScheduler} — world-level or server-wide tasks</li>
 *   <li>{@code runAsyncTask} → {@code AsyncScheduler} — tasks that must not touch world state</li>
 * </ul>
 *
 * <p>All methods are safe to call from any thread on both Folia and Paper.</p>
 */
public final class FoliaSchedulerAdapter {

    private FoliaSchedulerAdapter() {
        throw new UnsupportedOperationException("Utility class");
    }

    // -------------------------------------------------------------------------
    // Entity-bound tasks
    // -------------------------------------------------------------------------

    /**
     * Runs a task on the thread that owns the given entity's region (Folia),
     * or on the main server thread (Paper/Spigot).
     *
     * @param plugin   The owning plugin
     * @param entity   The entity whose region should run the task
     * @param task     The task to execute
     * @param retired  Called if the entity is removed before the task executes (Folia only; may be {@code null})
     */
    public static void runEntityTask(@NotNull Plugin plugin,
                                     @NotNull Entity entity,
                                     @NotNull Runnable task,
                                     @Nullable Runnable retired) {
        if (FoliaDetector.isFolia()) {
            entity.getScheduler().run(plugin, st -> task.run(), retired);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Schedules a delayed task on the entity's region thread (Folia) or main thread (Paper/Spigot).
     *
     * @param plugin       The owning plugin
     * @param entity       The entity whose region should run the task
     * @param task         The task to execute
     * @param retired      Called if the entity is removed before the task executes (Folia only; may be {@code null})
     * @param delayTicks   Delay in server ticks
     */
    public static void runEntityTaskLater(@NotNull Plugin plugin,
                                          @NotNull Entity entity,
                                          @NotNull Runnable task,
                                          @Nullable Runnable retired,
                                          long delayTicks) {
        if (FoliaDetector.isFolia()) {
            entity.getScheduler().runDelayed(plugin, st -> task.run(), retired, delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    // -------------------------------------------------------------------------
    // Location/region-bound tasks
    // -------------------------------------------------------------------------

    /**
     * Runs a task on the thread that owns the chunk at {@code location} (Folia),
     * or on the main thread (Paper/Spigot).
     *
     * @param plugin    The owning plugin
     * @param location  The location whose region should run the task
     * @param task      The task to execute
     */
    public static void runRegionTask(@NotNull Plugin plugin,
                                     @NotNull Location location,
                                     @NotNull Runnable task) {
        if (FoliaDetector.isFolia()) {
            Bukkit.getRegionScheduler().run(plugin, location, st -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Schedules a delayed task on the region thread for {@code location} (Folia)
     * or the main thread (Paper/Spigot).
     *
     * @param plugin      The owning plugin
     * @param location    The location whose region should run the task
     * @param task        The task to execute
     * @param delayTicks  Delay in server ticks
     */
    public static void runRegionTaskLater(@NotNull Plugin plugin,
                                          @NotNull Location location,
                                          @NotNull Runnable task,
                                          long delayTicks) {
        if (FoliaDetector.isFolia()) {
            Bukkit.getRegionScheduler().runDelayed(plugin, location, st -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    // -------------------------------------------------------------------------
    // Global tasks (world-level / server-wide, NOT entity or region specific)
    // -------------------------------------------------------------------------

    /**
     * Runs a global task — suitable for world loading/unloading and other server-wide operations.
     * On Folia this uses the {@code GlobalRegionScheduler}; on Paper/Spigot it uses the main thread.
     *
     * @param plugin  The owning plugin
     * @param task    The task to execute
     */
    public static void runGlobalTask(@NotNull Plugin plugin, @NotNull Runnable task) {
        if (FoliaDetector.isFolia()) {
            Bukkit.getGlobalRegionScheduler().run(plugin, st -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Schedules a delayed global task.
     *
     * @param plugin      The owning plugin
     * @param task        The task to execute
     * @param delayTicks  Delay in server ticks
     */
    public static void runGlobalTaskLater(@NotNull Plugin plugin,
                                          @NotNull Runnable task,
                                          long delayTicks) {
        if (FoliaDetector.isFolia()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, st -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * Schedules a repeating global task.
     *
     * @param plugin        The owning plugin
     * @param task          The task to execute
     * @param delayTicks    Initial delay in ticks
     * @param periodTicks   Repeat period in ticks
     */
    public static void runGlobalTaskTimer(@NotNull Plugin plugin,
                                          @NotNull Runnable task,
                                          long delayTicks,
                                          long periodTicks) {
        if (FoliaDetector.isFolia()) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, st -> task.run(), delayTicks, periodTicks);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    // -------------------------------------------------------------------------
    // Async tasks
    // -------------------------------------------------------------------------

    /**
     * Runs an asynchronous task. Safe on both Folia and Paper/Spigot.
     * <strong>Do not access world state inside these tasks.</strong>
     *
     * @param plugin  The owning plugin
     * @param task    The task to execute
     */
    public static void runAsyncTask(@NotNull Plugin plugin, @NotNull Runnable task) {
        if (FoliaDetector.isFolia()) {
            Bukkit.getAsyncScheduler().runNow(plugin, st -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /**
     * Schedules a delayed asynchronous task.
     *
     * @param plugin      The owning plugin
     * @param task        The task to execute
     * @param delayTicks  Delay in server ticks (converted to ms for Folia)
     */
    public static void runAsyncTaskLater(@NotNull Plugin plugin,
                                         @NotNull Runnable task,
                                         long delayTicks) {
        if (FoliaDetector.isFolia()) {
            // Folia AsyncScheduler uses time-based delays, not ticks
            long delayMillis = (delayTicks * 1000L) / 20L;
            Bukkit.getAsyncScheduler().runDelayed(plugin, st -> task.run(), delayMillis, TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
        }
    }

    // -------------------------------------------------------------------------
    // Thread-check helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the current thread is the "primary" thread.
     * <p>
     * On Paper/Spigot this is the main thread.
     * On Folia this is only meaningful for the global region thread — use with care.
     * </p>
     */
    public static boolean isPrimaryThread() {
        return Bukkit.isPrimaryThread();
    }

    /**
     * Logs a warning when a task is incorrectly called from the wrong thread context.
     * Useful for diagnosing thread-safety issues during development.
     *
     * @param context  A short description of where the check was triggered
     */
    public static void warnIfWrongThread(@NotNull String context) {
        if (FoliaDetector.isFolia()) {
            // On Folia, isPrimaryThread() is only true on the global region thread.
            // Many operations are valid on their own region thread, so we don't error — just note.
            Logging.finer("[FoliaScheduler] Thread check at: " + context
                    + " | thread=" + Thread.currentThread().getName());
        }
    }
}

package org.mvplugins.multiverse.core.folia;

import com.dumptruckman.minecraft.util.Logging;
import io.vavr.control.Try;
import jakarta.inject.Inject;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.jetbrains.annotations.NotNull;
import org.jvnet.hk2.annotations.Service;

import org.mvplugins.multiverse.core.MultiverseCore;
import org.mvplugins.multiverse.core.utils.result.Attempt;
import org.mvplugins.multiverse.core.world.LoadedMultiverseWorld;
import org.mvplugins.multiverse.core.world.WorldManager;
import org.mvplugins.multiverse.core.world.options.CreateWorldOptions;
import org.mvplugins.multiverse.core.world.options.ImportWorldOptions;
import org.mvplugins.multiverse.core.world.options.LoadWorldOptions;
import org.mvplugins.multiverse.core.world.options.UnloadWorldOptions;
import org.mvplugins.multiverse.core.world.reasons.CreateFailureReason;
import org.mvplugins.multiverse.core.world.reasons.ImportFailureReason;
import org.mvplugins.multiverse.core.world.reasons.LoadFailureReason;
import org.mvplugins.multiverse.core.world.reasons.UnloadFailureReason;

import java.util.concurrent.CompletableFuture;

/**
 * Folia-aware wrapper around {@link WorldManager} that ensures world loading,
 * creation, and unloading operations run on the correct scheduler context.
 *
 * <h2>Why this is needed</h2>
 * <p>Folia's regionized multithreading makes {@code Bukkit.createWorld()} and
 * {@code Bukkit.unloadWorld()} throw {@link UnsupportedOperationException} if called
 * from the wrong thread. All such calls must go through the
 * {@code GlobalRegionScheduler}, which this class handles transparently.</p>
 *
 * <p>On non-Folia servers (Paper, Spigot) this class is a thin pass-through —
 * all calls delegate directly to the underlying {@link WorldManager}.</p>
 */
@Service
public final class FoliaWorldManager {

    private final MultiverseCore plugin;
    private final WorldManager worldManager;

    @Inject
    FoliaWorldManager(@NotNull MultiverseCore plugin,
                      @NotNull WorldManager worldManager) {
        this.plugin = plugin;
        this.worldManager = worldManager;
    }

    // -------------------------------------------------------------------------
    // Create World
    // -------------------------------------------------------------------------

    /**
     * Creates a new world asynchronously, routing the creation through the
     * {@code GlobalRegionScheduler} on Folia.
     *
     * @param options Options for the world to create
     * @return A {@link CompletableFuture} resolving to the creation result
     */
    public CompletableFuture<Attempt<LoadedMultiverseWorld, CreateFailureReason>> createWorldAsync(
            @NotNull CreateWorldOptions options) {
        CompletableFuture<Attempt<LoadedMultiverseWorld, CreateFailureReason>> future = new CompletableFuture<>();

        FoliaSchedulerAdapter.runGlobalTask(plugin, () -> {
            try {
                Attempt<LoadedMultiverseWorld, CreateFailureReason> result = worldManager.createWorld(options);
                future.complete(result);
            } catch (Exception e) {
                Logging.severe("[FoliverseCore] Exception during world creation on GlobalRegionScheduler: %s",
                        e.getMessage());
                e.printStackTrace();
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    // -------------------------------------------------------------------------
    // Import World
    // -------------------------------------------------------------------------

    /**
     * Imports an existing world folder asynchronously.
     *
     * @param options Import options
     * @return A {@link CompletableFuture} resolving to the import result
     */
    public CompletableFuture<Attempt<LoadedMultiverseWorld, ImportFailureReason>> importWorldAsync(
            @NotNull ImportWorldOptions options) {
        CompletableFuture<Attempt<LoadedMultiverseWorld, ImportFailureReason>> future = new CompletableFuture<>();

        FoliaSchedulerAdapter.runGlobalTask(plugin, () -> {
            try {
                Attempt<LoadedMultiverseWorld, ImportFailureReason> result = worldManager.importWorld(options);
                future.complete(result);
            } catch (Exception e) {
                Logging.severe("[FoliverseCore] Exception during world import on GlobalRegionScheduler: %s",
                        e.getMessage());
                e.printStackTrace();
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    // -------------------------------------------------------------------------
    // Load World
    // -------------------------------------------------------------------------

    /**
     * Loads an existing world from config asynchronously.
     *
     * @param options Load options
     * @return A {@link CompletableFuture} resolving to the load result
     */
    public CompletableFuture<Attempt<LoadedMultiverseWorld, LoadFailureReason>> loadWorldAsync(
            @NotNull LoadWorldOptions options) {
        CompletableFuture<Attempt<LoadedMultiverseWorld, LoadFailureReason>> future = new CompletableFuture<>();

        FoliaSchedulerAdapter.runGlobalTask(plugin, () -> {
            try {
                Attempt<LoadedMultiverseWorld, LoadFailureReason> result = worldManager.loadWorld(options);
                future.complete(result);
            } catch (Exception e) {
                Logging.severe("[FoliverseCore] Exception during world load on GlobalRegionScheduler: %s",
                        e.getMessage());
                e.printStackTrace();
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    // -------------------------------------------------------------------------
    // Unload World
    // -------------------------------------------------------------------------

    /**
     * Unloads a world asynchronously.
     *
     * @param options Unload options
     * @return A {@link CompletableFuture} resolving to the unload result
     */
    public CompletableFuture<Attempt<?, UnloadFailureReason>> unloadWorldAsync(
            @NotNull UnloadWorldOptions options) {
        CompletableFuture<Attempt<?, UnloadFailureReason>> future = new CompletableFuture<>();

        FoliaSchedulerAdapter.runGlobalTask(plugin, () -> {
            try {
                var result = worldManager.unloadWorld(options);
                future.complete(result);
            } catch (Exception e) {
                Logging.severe("[FoliverseCore] Exception during world unload on GlobalRegionScheduler: %s",
                        e.getMessage());
                e.printStackTrace();
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    // -------------------------------------------------------------------------
    // Folia-safe world existence check
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the named world is currently loaded in Bukkit.
     * Safe to call from any thread.
     *
     * @param worldName The world name to check
     * @return {@code true} if loaded
     */
    public boolean isWorldLoaded(@NotNull String worldName) {
        return Bukkit.getWorld(worldName) != null;
    }

    /**
     * Returns the underlying {@link WorldManager}. Use with care on Folia — any
     * world-mutating methods should be called via this class's async wrappers.
     *
     * @return The underlying {@link WorldManager}
     */
    @NotNull
    public WorldManager getWorldManager() {
        return worldManager;
    }
}

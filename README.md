<p align="center">
  <img src="config/multiverse-banner.png" alt="FoliverseCore Logo">
</p>

# FoliverseCore — Folia-Compatible Multiverse-Core

**FoliverseCore** is a modern, Folia-compatible fork/distribution of [Multiverse-Core](https://github.com/Multiverse/Multiverse-Core), the legendary multi-world management plugin for Minecraft servers. 

Folia introduces **regionised multithreading**, meaning chunks and worlds run in parallel on separate threads. Standard plugins that assume a single-threaded server model (relying heavily on `BukkitScheduler`) will crash or cause data corruption on Folia. 

FoliverseCore solves this by introducing a conditional, reflection-based compatibility layer directly into the codebase. It dynamically detects if it is running on a Folia server and redirects all scheduler, teleport, world-lifecycle, and player events through Folia's region-aware schedulers.

---

## Will it work like Multiverse-Core on Paper / Spigot?

**Yes, absolutely!** 

One of the core design goals of FoliverseCore is **universal compatibility**. You do not need separate versions of the plugin for different server software. 
* **Under Spigot, Paper, or Purpur**: The Folia compatibility layer remains completely dormant. The plugin utilizes the standard `BukkitScheduler` and works exactly like the upstream, classic Multiverse-Core.
* **Under Folia**: The compatibility layer automatically activates. The plugin routes operations through Folia's regional schedulers, enabling safe multi-world administration on multithreaded servers.

---

## Detailed Comparison: Paper vs. Folia Behavior

| Feature / Behavior | Paper / Spigot Mode | Folia Mode |
| :--- | :--- | :--- |
| **Engine Threading** | Single-threaded main loop. | Regionised multithreading (separate threads per chunk/world region). |
| **World Creation & Loading** | Runs synchronously on the main thread, blocking the server tick until done. | Runs asynchronously on the `GlobalRegionScheduler` to prevent region thread lockup. |
| **Teleportation** | Synchronous instant teleportation. | Asynchronous teleportation via `teleportAsync` and `EntityScheduler` mapping. |
| **Portal Handling** | Normal vanilla portal linking, search, and destination creation. | Intercepted, cancelled, and redirected. Player is safely teleported to the target world's spawn location. |
| **Player Respawn** | Instantaneous coordinate relocation during the respawn tick. | Deferred by 1 tick on the player's `EntityScheduler` to resolve region thread bounds safely. |
| **Tasks & Delay Logic** | Scheduled via legacy `BukkitScheduler`. | Dynamically mapped to Entity, Region, Global, or Async Folia schedulers. |

---

## Summary of Implementations Done for Folia

To support Folia's strict regionised architecture, the following implementations and patches were introduced under `org.mvplugins.multiverse.core.folia` and within core utility classes:

### 1. Unified Facade Scheduling ([FoliaSchedulerAdapter.java](file:///c:/Projects/plugins/FoliverseCore/src/main/java/org/mvplugins/multiverse/core/folia/FoliaSchedulerAdapter.java))
The classic `BukkitScheduler` is blocked on Folia. We created a routing layer that maps task execution dynamically:
* **Entity-bound tasks**: Executed on the thread currently owning the entity (using `EntityScheduler`).
* **Region/Location tasks**: Executed on the thread owning the specified coordinate (using `RegionScheduler`).
* **Global tasks**: Executed on the server-wide tick thread (using `GlobalRegionScheduler`).
* **Async tasks**: Executed on the virtual thread pool (using `AsyncScheduler`), converting tick delays to milliseconds.
* **Fallback**: Translates automatically to standard `Bukkit.getScheduler()` when running on non-Folia platforms.

### 2. Reflection-based Detection ([FoliaDetector.java](file:///c:/Projects/plugins/FoliverseCore/src/main/java/org/mvplugins/multiverse/core/folia/FoliaDetector.java))
Checks for Folia-exclusive classes (e.g. `RegionizedServer`) at startup using reflection. This guards all Folia-specific calls and prevents `NoClassDefFoundError` crashes when loading on classic Paper or Spigot servers.

### 3. Async World Lifecycle Bridge ([FoliaWorldManager.java](file:///c:/Projects/plugins/FoliverseCore/src/main/java/org/mvplugins/multiverse/core/folia/FoliaWorldManager.java))
Folia restricts `Bukkit.createWorld()`, `loadWorld()`, and `unloadWorld()` to the global tick thread. 
* We wrapped these methods in `GlobalRegionScheduler` tasks.
* All world management routines now return `CompletableFuture`s, executing asynchronously without blocking region threads.

### 4. Portal Interception & Redirection ([FoliaPortalListener.java](file:///c:/Projects/plugins/FoliverseCore/src/main/java/org/mvplugins/multiverse/core/folia/FoliaPortalListener.java))
Vanilla portal generation and searches across worlds violate Folia's thread-safety boundaries (reading/writing to other regions).
* This listener intercepts cross-world portal travel.
* It cancels the vanilla event on Folia, and manually teleports the player safely to the destination world's spawn location using `teleportAsync` on the `EntityScheduler`.

### 5. Player Thread-Safety Handler ([FoliaPlayerListener.java](file:///c:/Projects/plugins/FoliverseCore/src/main/java/org/mvplugins/multiverse/core/folia/FoliaPlayerListener.java))
* **Respawns**: Defers post-respawn processing by 1 tick on the player's `EntityScheduler` to ensure the player entity is fully initialized in the target region before applying Multiverse configurations.
* **Joins**: Schedules join-destination coordinates processing safely on the player's own entity thread context.

### 6. Codebase Patches
* Modified [AsyncSafetyTeleporterAction.java](file:///c:/Projects/plugins/FoliverseCore/src/main/java/org/mvplugins/multiverse/core/teleportation/AsyncSafetyTeleporterAction.java) to route passenger dismount and post-teleport actions through `FoliaSchedulerAdapter`.
* Modified [MVPlayerListener.java](file:///c:/Projects/plugins/FoliverseCore/src/main/java/org/mvplugins/multiverse/core/listeners/MVPlayerListener.java) to apply game mode and flight delay adjustments on the `EntityScheduler`.
* Wired all compatibility listeners dynamically into [MultiverseCore.java](file:///c:/Projects/plugins/FoliverseCore/src/main/java/org/mvplugins/multiverse/core/MultiverseCore.java) on plugin load.

---

## Known Limitations on Folia

Due to Folia's design, certain features behave differently or are constrained:
1. **Portal Frames & Teleportation**: When using nether or end portals to travel between worlds, you will be teleported to the target world's spawn instead of having a portal frame linked and generated at the exact destination coordinates.
2. **Scoreboard Integrations**: Any scoreboard features are currently limited/disabled due to Folia's API constraints.
3. **Delay Ticks**: While tick-based tasks are precise on region threads, async tasks with tick delays are converted to millisecond delays (`ticks * 50ms`) on Folia's async scheduler.
4. **Dynamic World Loading & Creation**: Folia does not support dynamic (runtime) world creation or loading (refer to PaperMC/Folia Issue #134). The core `Bukkit.createWorld()` and `WorldCreator` APIs are disabled at runtime and throw an `UnsupportedOperationException`. Consequently, runtime commands like `/mv import`, `/mv create`, or `/mv load` (for unloaded worlds) will fail.
   * **Workaround**: You must declare your custom worlds in the server's `bukkit.yml` under the `worlds:` block so they are loaded by the server at startup:
     ```yaml
     worlds:
       Parkour:
         generator: VoidGen # (Optional: specify if using a custom generator)
     ```
     Once loaded during server startup, Multiverse-Core will automatically detect, register, and manage these worlds without any issues.

---

## Compilation

Builds require **JDK 21** (due to Gradle compatibility).

```powershell
# Set Java Home to JDK 21 and build shadowJar
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew clean shadowJar
```

The output shaded plugin will be generated at `build/libs/multiverse-core-folia.jar`.

---

## Credits & Original Project

**FoliverseCore** is an unofficial community fork. All core logic, features, design, and original code belong entirely to the **Multiverse Team** and their contributors.

* **Folia Port Developer**: `Ace` (Folia compatibility implementation, thread-safety scheduling layer, and startup bootstrap patches)
* **Original Project**: [Multiverse-Core on GitHub](https://github.com/Multiverse/Multiverse-Core)
* **Original Authors**: `dumptruckman`, `Rigby`, `fernferret`, `lithium3141`, `main--`, `benwoo1110`, `Zax71`, and all other contributors.
* **Disclaimer**: This fork is created solely to apply a Folia compatibility layer. It is not affiliated with, endorsed by, or supported by the official Multiverse Team. Please do not report bugs or ask for help regarding this Folia build on the official Multiverse Discord or GitHub channels.

### License
This project is licensed under the BSD-3-Clause License, matching the original Multiverse-Core license. See the `LICENSE` file for details.


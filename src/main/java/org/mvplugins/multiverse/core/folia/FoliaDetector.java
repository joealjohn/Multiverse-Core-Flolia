package org.mvplugins.multiverse.core.folia;

/**
 * Utility class for detecting whether the server is running Folia.
 * <p>
 * Uses reflection-based class detection so this jar loads safely on non-Folia servers
 * (Paper, Spigot, etc.) without throwing {@link ClassNotFoundException} at startup.
 * </p>
 */
public final class FoliaDetector {

    private static final boolean IS_FOLIA;

    static {
        boolean detected = false;
        try {
            // RegionizedServer is Folia-exclusive — its presence reliably identifies a Folia runtime.
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            detected = true;
        } catch (ClassNotFoundException ignored) {
            // Not Folia — regular Paper / Spigot / Bukkit
        }
        IS_FOLIA = detected;
    }

    private FoliaDetector() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns {@code true} when the current server software is Folia (or a fork thereof).
     *
     * @return {@code true} if running on Folia
     */
    public static boolean isFolia() {
        return IS_FOLIA;
    }

    /**
     * Checks if the current thread is the global tick thread (Folia-only).
     * Safe to call on non-Folia servers (returns false).
     *
     * @return true if running on Folia and the current thread is the global tick thread
     */
    public static boolean isGlobalTickThread() {
        if (!IS_FOLIA) {
            return false;
        }
        try {
            return (boolean) org.bukkit.Bukkit.class.getMethod("isGlobalTickThread").invoke(null);
        } catch (Exception e) {
            return false;
        }
    }
}

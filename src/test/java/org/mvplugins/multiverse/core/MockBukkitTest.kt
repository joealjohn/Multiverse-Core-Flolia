package org.mvplugins.multiverse.core

import kotlin.test.Test
import kotlin.test.assertNotNull

open class MockBukkitTest : TestWithMockBukkit() {

    @Test
    fun `MockBukkit loads the plugin`() {
        assertNotNull(multiverseCore)
    }

    @Test
    fun testReflection() {
        println("=== BUKKIT METHODS ===")
        org.bukkit.Bukkit::class.java.methods.forEach {
            println(it.toString())
        }
        println("=== SERVER METHODS ===")
        org.bukkit.Server::class.java.methods.forEach {
            println(it.toString())
        }
    }
}

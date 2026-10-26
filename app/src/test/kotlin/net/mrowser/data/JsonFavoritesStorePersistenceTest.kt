package net.mrowser.data

import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonFavoritesStorePersistenceTest {

    @Test fun `favorites are available after recreating the store`() {
        val directory = Files.createTempDirectory("mrowser-private-favorites").toFile()
        try {
            val file = File(directory, "favorites.json")
            val favorites = listOf(
                Favorite("One", "https://one.example/path"),
                Favorite("Two", "https://two.example/")
            )
            JsonFavoritesStore(file, DIRECT_EXECUTOR).addAll(favorites)

            val restartedStore = JsonFavoritesStore(file, DIRECT_EXECUTOR)
            assertEquals(favorites, restartedStore.findAll())
        } finally {
            directory.deleteRecursively()
        }
    }

    private companion object {
        val DIRECT_EXECUTOR = Executor { it.run() }
    }
}

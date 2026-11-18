package com.catsmoker.app.screens.editgamefiles.storage

import com.catsmoker.app.screens.editgamefiles.storage.ScopedConfigBackups.Target
import com.catsmoker.app.screens.editgamefiles.storage.ScopedConfigBackups.Target.Location
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScopedConfigBackupsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `a same-named document cannot see another document or a direct backup`() {
        val store = ScopedConfigBackups(temp.root)
        val a = Target("com.game", "config.json", Location.Document("content://provider/document/a"))
        val b = a.copy(source = Location.Document("content://provider/document/b"))
        val direct = a.copy(source = Location.Direct("/game/config.json"))
        val original = "first".toByteArray()
        assertTrue(store.save(a, original))
        assertNull(store.latest(b))
        assertNull(store.latest(direct))
        assertTrue(store.save(b, "second".toByteArray()))
        assertTrue(store.save(direct, "direct".toByteArray()))
        assertArrayEquals(original, store.read(checkNotNull(store.latest(a))))
        assertEquals("second", store.read(checkNotNull(store.latest(b)))?.decodeToString())
        assertEquals("direct", store.read(checkNotNull(store.latest(direct)))?.decodeToString())
    }

    @Test fun `legacy backups are retained but never considered an exact match`() {
        val legacy = ConfigBackupStore(File(temp.root, "config_backups"))
        val target = Target("com.game", "config.json", Location.Direct("/game/config.json"))
        val entry = checkNotNull(legacy.save(target.packageName, target.fileName, "old".toByteArray()))
        assertNull(ScopedConfigBackups(temp.root).latest(target))
        assertTrue(entry.file.isFile)
    }

    @Test fun `source isolation retains the per-game backup limit`() {
        val store = ScopedConfigBackups(temp.root)
        val target = Target("com.game", "config.json", Location.Document("content://provider/1"))
        repeat(11) { i ->
            assertTrue(store.save(target.copy(source = Location.Document("content://provider/$i")), byteArrayOf(1)))
        }
        val leaf = File(temp.root, "config_backups_scoped_v1/${target.packageName}")
        assertEquals(10, leaf.listFiles()?.size)
    }
}

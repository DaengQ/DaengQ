package com.example.pawgo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PetRecordStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun record(time: Long, behavior: PetBehavior = PetBehavior.CRYING) =
        PetRecord(timeMillis = time, behavior = behavior, naviStatus = null, address = null, latitude = 37.4, longitude = 127.1)

    private fun recordsFile() = File(folder.root, "pet_records.json")
    private fun backups() = folder.root.listFiles { f -> f.name.startsWith("pet_records.json.damaged") }!!.toList()

    @Test
    fun savedRecordsLoadBack() {
        val store = PetRecordStore(folder.root)
        store.save(listOf(record(1000), record(2000, PetBehavior.HOWLING)))
        assertEquals(listOf(1000L, 2000L), store.load().map { it.timeMillis })
        assertEquals(emptyList<File>(), backups())
        assertFalse("no temp file is left behind", File(folder.root, "pet_records.json.tmp").exists())
    }

    @Test
    fun oneBrokenEntryKeepsTheOthers() {
        recordsFile().writeText(
            """[{"time":1000,"behavior":"CRYING"},{"time":"oops","behavior":"CRYING"},{"time":3000,"behavior":"WHINING"}]""",
        )
        assertEquals(listOf(1000L, 3000L), PetRecordStore(folder.root).load().map { it.timeMillis })
        assertEquals(1, backups().size)
    }

    @Test
    fun truncatedFileKeepsItsCompleteRecordsThroughTheNextSave() {
        val store = PetRecordStore(folder.root)
        store.save(listOf(record(1000), record(2000), record(3000)))
        val original = recordsFile().readText()
        recordsFile().writeText(original.dropLast(5))

        // MainActivity.recordBehavior: load, add the new record, save
        val loaded = store.load()
        store.save(loaded + record(9000, PetBehavior.WHINING))

        assertEquals(listOf(1000L, 2000L, 9000L), store.load().map { it.timeMillis })
        assertEquals("the damaged original is kept once", original.dropLast(5), backups().single().readText())
    }

    @Test
    fun recordsOfRetiredTypesAreSkippedWithoutCountingAsDamage() {
        recordsFile().writeText("""[{"time":1000,"behavior":"SOMETHING_OLD"},{"time":2000,"behavior":"HOWLING"}]""")
        assertEquals(listOf(2000L), PetRecordStore(folder.root).load().map { it.timeMillis })
        assertEquals(emptyList<File>(), backups())
    }
}

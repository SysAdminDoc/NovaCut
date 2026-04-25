package com.novacut.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AtomicFilesTest {

    @Test
    fun moveFileReplacing_overwritesTargetAndDeletesSource() {
        val dir = Files.createTempDirectory("atomic-files-").toFile()
        try {
            val source = File(dir, "source.txt").apply { writeText("new", Charsets.UTF_8) }
            val target = File(dir, "target.txt").apply { writeText("old", Charsets.UTF_8) }

            moveFileReplacing(source, target)

            assertFalse(source.exists())
            assertTrue(target.exists())
            assertEquals("new", target.readText(Charsets.UTF_8))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writeUtf8TextAtomically_replacesContentUsingUtf8() {
        val dir = Files.createTempDirectory("atomic-write-").toFile()
        try {
            val target = File(dir, "template.json").apply { writeText("before", Charsets.UTF_8) }

            writeUtf8TextAtomically(target, "caf\u00e9")

            assertEquals("caf\u00e9", target.readText(Charsets.UTF_8))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writeFileAtomically_replacesContentUsingCompleteBinaryOutput() {
        val dir = Files.createTempDirectory("atomic-binary-write-").toFile()
        try {
            val target = File(dir, "preview.gif")

            writeFileAtomically(target, requireNonEmpty = true) { tempFile ->
                tempFile.writeBytes(byteArrayOf(71, 73, 70, 56))
            }

            assertArrayEquals(byteArrayOf(71, 73, 70, 56), target.readBytes())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writeFileAtomically_preservesTargetAndCleansTempOnFailure() {
        val dir = Files.createTempDirectory("atomic-failed-write-").toFile()
        try {
            val target = File(dir, "library.cube").apply { writeText("before", Charsets.UTF_8) }

            try {
                writeFileAtomically(target, requireNonEmpty = true) { tempFile ->
                    tempFile.writeText("partial", Charsets.UTF_8)
                    throw IllegalStateException("copy failed")
                }
                fail("Expected failed atomic write to throw")
            } catch (_: IllegalStateException) {
                // Expected.
            }

            assertEquals("before", target.readText(Charsets.UTF_8))
            assertEquals(listOf("library.cube"), dir.listFiles()?.map { it.name }?.sorted())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writeFileAtomically_rejectsEmptyRequiredOutput() {
        val dir = Files.createTempDirectory("atomic-empty-write-").toFile()
        try {
            val target = File(dir, "capture.mp4").apply { writeText("before", Charsets.UTF_8) }

            try {
                writeFileAtomically(target, requireNonEmpty = true) { _ -> }
                fail("Expected empty required output to throw")
            } catch (_: java.io.IOException) {
                // Expected.
            }

            assertEquals("before", target.readText(Charsets.UTF_8))
            assertEquals(listOf("capture.mp4"), dir.listFiles()?.map { it.name }?.sorted())
        } finally {
            dir.deleteRecursively()
        }
    }
}

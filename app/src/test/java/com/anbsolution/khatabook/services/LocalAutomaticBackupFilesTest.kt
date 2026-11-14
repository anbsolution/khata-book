package com.anbsolution.khatabook.services

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalAutomaticBackupFilesTest {
    @Test
    fun retentionKeepsNewestBackupsAndReturnsOnlyOlderFiles() {
        val directory = Files.createTempDirectory("khata-backups").toFile()
        try {
            val files = (1..5).map { index ->
                File(directory, "backup-$index").apply {
                    writeText("backup")
                    setLastModified(index * 1_000L)
                }
            }

            assertEquals(
                listOf("backup-2", "backup-1"),
                LocalAutomaticBackupFiles.filesToDelete(files, retentionCount = 3).map { it.name }
            )
        } finally {
            directory.listFiles()?.forEach(File::delete)
            directory.delete()
        }
    }
}

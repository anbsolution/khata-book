package com.anbsolution.khatabook.services

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

enum class AutomaticBackupCadence(val storageValue: String, val intervalDays: Long) {
    DAILY("daily", 1),
    WEEKLY("weekly", 7);

    companion object {
        fun fromStorageValue(value: String?): AutomaticBackupCadence? =
            entries.firstOrNull { it.storageValue == value }
    }
}

object LocalAutomaticBackupFiles {
    const val RETENTION_COUNT = 7
    const val MAX_BACKUP_BYTES = 50 * 1024 * 1024
    private const val DIRECTORY_NAME = "automatic-backups"
    private const val EXTENSION = ".khataauto"

    fun latest(context: Context): File? =
        all(context).maxByOrNull { it.lastModified() }

    fun all(context: Context): List<File> =
        backupDirectory(context).listFiles()
            ?.filter { it.isFile && it.name.endsWith(EXTENSION) }
            .orEmpty()
            .sortedByDescending { it.lastModified() }

    fun write(context: Context, cadence: AutomaticBackupCadence, bytes: ByteArray): File {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BACKUP_BYTES) {
            "Automatic backup size is invalid."
        }
        val directory = backupDirectory(context)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create automatic backup storage.")
        }
        val name = "${cadence.storageValue}-${System.currentTimeMillis()}-" +
            "${java.util.UUID.randomUUID()}$EXTENSION"
        val file = File(directory, name)
        val temporaryFile = File(directory, ".$name.tmp")
        FileOutputStream(temporaryFile).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        if (!temporaryFile.renameTo(file)) {
            temporaryFile.delete()
            throw IOException("Could not publish the automatic backup.")
        }

        val oldFiles = filesToDelete(
            directory.listFiles()
                ?.filter {
                    it.isFile &&
                        it.name.startsWith("${cadence.storageValue}-") &&
                        it.name.endsWith(EXTENSION)
                }
                .orEmpty(),
            RETENTION_COUNT
        )
        oldFiles.forEach {
            if (!it.delete()) throw IOException("Could not remove an expired automatic backup.")
        }
        return file
    }

    fun read(file: File): ByteArray {
        require(file.isFile && file.length() in 1..MAX_BACKUP_BYTES.toLong()) {
            "Automatic backup file is missing or too large."
        }
        return file.readBytes()
    }

    internal fun filesToDelete(files: List<File>, retentionCount: Int): List<File> {
        require(retentionCount >= 1) { "At least one automatic backup must be retained." }
        return files.sortedWith(
            compareByDescending<File> { it.lastModified() }
                .thenByDescending { it.name }
        ).drop(retentionCount)
    }

    private fun backupDirectory(context: Context): File =
        File(context.filesDir, DIRECTORY_NAME)
}

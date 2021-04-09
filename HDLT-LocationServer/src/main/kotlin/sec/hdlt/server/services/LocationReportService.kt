package sec.hdlt.server.services

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import sec.hdlt.server.EPOCH_INTERVAL
import sec.hdlt.server.data.Coordinates
import sec.hdlt.server.data.LocationReport
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

class LocationReportService(directory: String) {
    private val reportsDirectory: String = directory
    private val logger = LoggerFactory.getLogger("Location")

    fun storeLocationReport(
        epoch: Int,
        user1: Int,
        user2: Int,
        coordinates1: Coordinates,
        coordinates2: Coordinates
    ) {
        val file = getFile(reportsDirectory, epoch)

        if (!file.exists()) {
            CoroutineScope(Dispatchers.Default).launch {
                delay(EPOCH_INTERVAL * 4)
                validateLocationReports(file, epoch)
            }
        }

        try {
            FileOutputStream(file, true).bufferedWriter().use { out ->
                out.write("$user1 $coordinates1 $user2 $coordinates2\n")
            }
        } catch (ex: FileNotFoundException) {
            logger.error(ex.message)
        }
    }

    fun getLocationReport(userId: Int, epoch: Int): LocationReport? {
        val file = getFile(reportsDirectory, epoch)
        try {
            file.readLines().forEach {
                val words = it.split(" ")
                if (words[0] == userId.toString()) {
                    val coordinates = Coordinates(words[1].toInt(), words[2].toInt())
                    return LocationReport(userId, epoch, coordinates)
                }
            }
        } catch (ex: FileNotFoundException) {
            logger.error(ex.message)
        }

        return null
    }

    private fun getFile(pathname: String, epoch: Int) : File {
        return File("${pathname}epoch${epoch}")
    }

    private fun validateLocationReports(file: File, epoch: Int) {
        try {
            val reportValidationList = file.readLines() as MutableList<String>

            reportValidationList.forEach {
                val words = it.split(" ")

                if (!reportValidationList.contains("${words[2]} ${words[3]} ${words[0]} ${words[1]}")) {
                    logger.info("BUSTED - User ${words[2]} did not communicated with the server on epoch $epoch")
                }
            }

        } catch (ex: FileNotFoundException) {
            logger.error(ex.message)
        }
    }
}
package sec.locationserver.services

import org.slf4j.LoggerFactory
import sec.locationserver.data.Coordinates
import sec.locationserver.data.LocationReport
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
        try {
            FileOutputStream(file, true).bufferedWriter().use { out ->
                out.write("$user1 $coordinates1.toString() $user2 $coordinates2.toString()\n")
            }
        } catch (ex: FileNotFoundException) {
            logger.error(ex.message)
        }
    }

    fun getLocationReport(userId: Int, epoch: Int): LocationReport? {
        val file = getFile(reportsDirectory, epoch)
        try {
            file.readLines().forEach {
                val words = it.split("\\W+".toRegex())
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
}
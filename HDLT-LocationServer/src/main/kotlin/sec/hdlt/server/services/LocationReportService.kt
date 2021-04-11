package sec.hdlt.server.services

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import sec.hdlt.protos.server.Report
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
        user: Int,
        coordinates: Coordinates,
        proofs: List<Report.LocationProof>
    ) {
        val file = getFile(reportsDirectory, epoch)

        if (!file.exists()) {
            CoroutineScope(Dispatchers.Default).launch {
                delay(EPOCH_INTERVAL * 4)
                validateLocationReports(file, epoch)
            }
        }

        try {
            var storedProof = "$user $coordinates"
            proofs.forEach { proof ->
                storedProof += " ${proof.proverId}"
            }

            FileOutputStream(file, true).bufferedWriter().use { out ->
                out.write("$storedProof\n")
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

    private fun validateLocationReports(file: File, epoch: Int) {
        try {
            val reportValidationList = file.readLines() as MutableList<String>

            reportValidationList.forEach {
                val words = it.split(" ")

                if (words.size > 2) {
                    val userCoordinates = getCoordinates(words[1])
                    for (i in 2 until words.size) {
                        val prooferCoordinates = getProoferCoordinates(reportValidationList, words[i])
                        if (prooferCoordinates == null) {
                            logger.info("BUSTED - User ${words[i]} did not send his report on epoch $epoch")

                        } else if (!userCoordinates.isNear(prooferCoordinates)) {
                            logger.info("BUSTED - User ${words[0]} is not close to user ${words[i]} on epoch $epoch")
                        }
                    }
                }
            }

        } catch (ex: FileNotFoundException) {
            logger.error(ex.message)
        }
    }

    private fun getFile(pathname: String, epoch: Int) : File {
        return File("${pathname}epoch${epoch}")
    }

    private fun getCoordinates(words: String): Coordinates {
        return Coordinates(words[1].toInt(), words[3].toInt())
    }

    private fun getProoferCoordinates(reportValidationList: MutableList<String>, prooferId: String): Coordinates? {
        reportValidationList.forEach {
            val prooferReport = it.split(" ")
            if (prooferReport[0] == prooferId)
                return getCoordinates(prooferReport[1])
        }
        return null
    }
}
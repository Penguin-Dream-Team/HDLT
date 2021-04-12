package sec.hdlt.server.services

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import sec.hdlt.server.EPOCH_INTERVAL
import sec.hdlt.server.data.Coordinates
import sec.hdlt.server.data.Proof
import sec.hdlt.server.data.ReportInfo
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

const val DELAY_TIME = 4
const val FLAGGED_AMOUNT = 3
const val WELL_BEHAVED_AMOUNT = 10

@Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class LocationReportService(directory: String) {
    private val reportsDirectory: String = directory
    private val logger = LoggerFactory.getLogger("Location")
    private val byzantineUsers = hashMapOf<Long, Boolean>()
    private val flaggedUsers = hashMapOf<Long, Int>()          // 3 flags and user becomes byzantine
    private val wellBehavedUsers = hashMapOf<Long, Int>()      // 10 times well behaved and removes one flag

    fun clearOldReports() {
        File(reportsDirectory).listFiles().forEach { file ->
            if (file.name.startsWith("epoch")) {
                file.delete()
            }
        }
    }

    fun storeLocationReport(
        epoch: Int,
        user: Int,
        coordinates: Coordinates,
        proofs: List<Proof>
    ) {
        val file = getFile(reportsDirectory, epoch)

        if (!file.exists()) {
            CoroutineScope(Dispatchers.Default).launch {
                delay(EPOCH_INTERVAL * DELAY_TIME)
                validateLocationReports(file, epoch)
            }
        }

        try {
            var storedProof = "$user $coordinates"
            proofs.forEach { proof ->
                storedProof += " ${proof.prover}"
            }

            FileOutputStream(file, true).bufferedWriter().use { out ->
                out.write("$storedProof\n")
            }
        } catch (ex: FileNotFoundException) {
            logger.error(ex.message)
        }
    }

    fun getLocationReport(userId: Long, epoch: Int): ReportInfo? {
        if (byzantineUsers[userId]!!) {
            return null
        }

        val file = getFile(reportsDirectory, epoch)
        try {
            file.readLines().forEach {
                val words = it.split(" ")
                if (words[0] == userId.toString()) {
                    val coordinates = Coordinates(words[1].toInt(), words[2].toInt())
                    return ReportInfo(userId.toInt(), epoch, coordinates)
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
                            flagUser(words[i].toLong(), epoch)
                        } else if (!userCoordinates.isNear(prooferCoordinates)) {
                            logger.error("BUSTED - User ${words[0]} is not close to user ${words[i]} on epoch $epoch")
                        } else {
                            wellBehavedUser(words[i].toLong())
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
        initUser(words[0].toLong())
        return Coordinates(words[1].toInt(), words[3].toInt())
    }

    private fun getProoferCoordinates(reportValidationList: MutableList<String>, prooferId: String): Coordinates? {
        initUser(prooferId.toLong())
        reportValidationList.forEach {
            val prooferReport = it.split(" ")
            if (prooferReport[0] == prooferId)
                return getCoordinates(prooferReport[1])
        }
        return null
    }

    private fun initUser(userId: Long) {
        if (!byzantineUsers.containsKey(userId)) byzantineUsers[userId] = false
        if (!flaggedUsers.containsKey(userId)) flaggedUsers[userId] = 0
        if (!wellBehavedUsers.containsKey(userId)) wellBehavedUsers[userId] = 0
    }

    private fun flagUser(userId: Long, epoch: Int) {
        flaggedUsers[userId] = flaggedUsers[userId]!!.plus(1)
        if (!byzantineUsers[userId]!!)
            logger.warn("User $userId got flagged by not sending his report on epoch $epoch. Current flags = ${flaggedUsers[userId]}")

        if (flaggedUsers[userId]!! == FLAGGED_AMOUNT) {
            byzantineUsers[userId] = true
            wellBehavedUsers[userId] = 0
            logger.error("BUSTED - User $userId got busted on epoch $epoch")
        }
    }

    private fun wellBehavedUser(userId: Long) {
        if (flaggedUsers[userId]!! > 0) {
            wellBehavedUsers[userId] = wellBehavedUsers[userId]!!.plus(1)

            if (wellBehavedUsers[userId]!! == WELL_BEHAVED_AMOUNT) {
                wellBehavedUsers[userId] = 0
                flaggedUsers[userId] = flaggedUsers[userId]!!.minus(1)
                logger.info("FIX - User $userId well behaved for $WELL_BEHAVED_AMOUNT epochs. Current flags = ${flaggedUsers[userId]}")

                if (byzantineUsers[userId]!! && flaggedUsers[userId]!! == 0) {
                    byzantineUsers[userId] = false
                    logger.info("FIX - User $userId was misbehaved as byzantine")
                }
            }
        }
    }
}
package sec.hdlt.server.services

import org.slf4j.LoggerFactory
import sec.hdlt.server.domain.*
import sec.hdlt.server.exceptions.DuplicateReportException
import sec.hdlt.server.exceptions.HDLTException

class LocationReportService {
    companion object {
        private val logger = LoggerFactory.getLogger("Location")

        suspend fun storeLocationReport(
            report: LocationReport,
            epoch: Int,
            user: Int,
            coordinates: Coordinates,
            proofs: List<Proof>
        ) : Boolean {
            return try {
                if (Database.reportDAO.hasUserReport(user, epoch)) {
                    throw DuplicateReportException(user, epoch)
                }
                if (CommunicationService.write(report)) {
                    Database.reportDAO.saveUserReport(epoch, user, coordinates, proofs)
                    return true
                }
                false
            } catch (ex: HDLTException) {
                logger.error(ex.message)
                false
            }
        }

        fun getLocationReport(userId: Int, epoch: Int, fLine: Int): LocationResponse? {
            return try {
                validateLocationReport(
                    Database.reportDAO.getUserLocationReport(userId, epoch),
                    Database.reportDAO.getEpochReports(epoch),
                    fLine
                )
            } catch (ex: HDLTException) {
                logger.error(ex.message)
                null
            }
        }

        fun hasReport(userId: Int, epoch: Int): Boolean {
            return Database.reportDAO.hasUserReport(userId, epoch)
        }

        fun getUsersAtLocation(epoch: Int, coords: Coordinates): List<Int> {
            return Database.reportDAO.getUsersAtLocation(epoch, coords)
        }

        private fun validateLocationReport(report: LocationReport, reports: List<LocationReport>, fLine: Int): LocationResponse? {
            var rightUsers = 0
            report.proofs.forEach { proof ->
                val prooverCoordinates = getProoferCoordinates(reports, proof.prover)

                if (prooverCoordinates != null && !report.location.isNear(prooverCoordinates)) {
                    logger.error("BUSTED - User ${report.id} is not close to user ${proof.prover} on epoch ${report.epoch}")
                } else if (prooverCoordinates != null){
                    rightUsers++
                }
            }

            return LocationResponse(
                id = report.id,
                epoch = report.epoch,
                coords = report.location,
                serverInfo = when {
                    report.proofs.size > fLine -> "Report validated by a quorum of good users"
                    rightUsers == 0 -> "Can not ensure the quality of the report. Not enough proofs"
                    else -> "Report validated by at least $rightUsers good users"
                },
                report.proofs,

                // SIGNATURE NOT USED HERE
                ""
            )
        }

        private fun getProoferCoordinates(reports: List<LocationReport>, prooferId: Int): Coordinates? {
            return reports.firstOrNull { report -> report.id == prooferId }?.location
        }
    }
}
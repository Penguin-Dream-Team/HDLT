package sec.hdlt.server.services

import org.slf4j.LoggerFactory
import sec.hdlt.server.data.*
import sec.hdlt.server.exceptions.HDLTException

class LocationReportService {
    companion object {
        private val logger = LoggerFactory.getLogger("Location")

        fun storeLocationReport(
            epoch: Int,
            user: Int,
            coordinates: Coordinates,
            proofs: List<Proof>
        ) {
            try {
                Database.reportDAO.saveUserReport(epoch, user, coordinates, proofs)
            } catch (ex: HDLTException) {
                logger.error(ex.message)
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
                val prooferCoordinates = getProoferCoordinates(reports, proof.prover)

                if (prooferCoordinates != null && !report.location.isNear(prooferCoordinates)) {
                    logger.error("BUSTED - User ${report.id} is not close to user ${proof.prover} on epoch ${report.epoch}")
                    // FIXME: Just ignore??
                    return null
                } else if (prooferCoordinates != null){
                    rightUsers++
                }
            }

            val quorum = rightUsers - fLine
            return LocationResponse(
                id = report.id,
                epoch = report.epoch,
                coords = report.location,
                serverInfo = when {
                    quorum > fLine -> "Report validated by a quorum of good users"
                    quorum > 0 -> "Report validated by at least $quorum good users"
                    else -> "Can not ensure the quality of the report. Not enough proofs"
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
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

        fun getLocationReport(userId: Int, epoch: Int, fLine: Int): ReportInfo? {
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

        private fun validateLocationReport(report: LocationReport, reports: List<LocationReport>, fLine: Int): ReportInfo? {
            var rightUsers = 0

            report.proofs.forEach { proof ->
                val prooferCoordinates = getProoferCoordinates(reports, proof.prover)

                if (prooferCoordinates != null && !report.location.isNear(prooferCoordinates)) {
                    logger.error("BUSTED - User ${report.id} is not close to user ${proof.prover} on epoch ${report.epoch}")
                    return null
                } else {
                    rightUsers++
                }
            }

            val quorum = rightUsers - fLine
            return ReportInfo(
                id = report.id,
                epoch = report.epoch,
                coordinates = report.location,
                serverInfo = when {
                    quorum > fLine -> "Report validated by a quorum of good users"
                    quorum > 0 -> "Report validated by at least $quorum good users"
                    else -> "Can not ensure the quality of the report. Not enough proofs"
                }
            )
        }

        private fun getProoferCoordinates(reports: List<LocationReport>, prooferId: Int): Coordinates? {
            return reports.firstOrNull { report -> report.id == prooferId }?.location
        }
    }
}
package sec.hdlt.server.services

import org.slf4j.LoggerFactory
import sec.hdlt.server.dao.ReportDAO
import sec.hdlt.server.data.Coordinates
import sec.hdlt.server.data.LocationReport
import sec.hdlt.server.data.Proof
import sec.hdlt.server.data.ReportInfo
import sec.hdlt.server.exceptions.HDLTException

class LocationReportService(
    private val reportDao: ReportDAO
) {
    private val logger = LoggerFactory.getLogger("Location")

    fun storeLocationReport(
        epoch: Int,
        user: Int,
        coordinates: Coordinates,
        proofs: List<Proof>
    ) {
        try {
            reportDao.saveUserReport(epoch, user, coordinates, proofs)
        } catch (ex: HDLTException) {
            logger.error(ex.message)
        }
    }

    fun getLocationReport(userId: Int, epoch: Int): ReportInfo? {
        return try {
            validateLocationReport(reportDao.getUserLocationReport(userId, epoch), reportDao.getEpochReports(epoch))
        } catch (ex: HDLTException) {
            logger.error(ex.message)
            null
        }
    }

    private fun validateLocationReport(report: LocationReport, reports: List<LocationReport>): ReportInfo? {
        report.proofs.forEach { proof ->
            val prooferCoordinates = getProoferCoordinates(reports, proof.prover)

            if (prooferCoordinates != null && !report.location.isNear(prooferCoordinates)) {
                logger.error("BUSTED - User ${report.id} is not close to user ${proof.prover} on epoch ${report.epoch}")
                return null
            }
        }

        return ReportInfo(
            id = report.id,
            epoch = report.epoch,
            coordinates = report.location
        )
    }

    private fun validateLocationReports(epoch: Int) {
        val reports = reportDao.getEpochReports(epoch)

        reports.forEach { report ->
            validateLocationReport(report, reports)
        }

    }

    private fun getProoferCoordinates(reports: List<LocationReport>, prooferId: Int): Coordinates? {
        return reports.firstOrNull { report -> report.id == prooferId }?.location
    }
}
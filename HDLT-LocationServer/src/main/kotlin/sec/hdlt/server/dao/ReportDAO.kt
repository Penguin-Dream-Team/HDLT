package sec.hdlt.server.dao

import org.jetbrains.annotations.NotNull
import org.jooq.*
import org.jooq.impl.DSL
import org.jooq.types.UInteger
import sec.hdlt.server.data.Coordinates
import sec.hdlt.server.data.LocationReport
import sec.hdlt.server.data.Proof
import sec.hdlt.server.data.ReportInfo
import sec.hdlt.server.db.database.Tables.PROOFS
import sec.hdlt.server.db.database.Tables.REPORTS
import sec.hdlt.server.exceptions.ProofCreationException
import sec.hdlt.server.exceptions.ReportCreationException
import sec.hdlt.server.exceptions.UserReportNotFoundException

class ReportDAO(
    conf: Configuration
) {
    private val dslContext = DSL.using(conf)

    fun epochHasReports(epoch: Int, create: DSLContext = dslContext): Boolean {
        return create.fetchCount(
            create.select(REPORTS.ID)
                .from(REPORTS)
                .where(REPORTS.EPOCH.eq(epoch))
        ) != 0
    }

    fun hasUserReport(userId: Int, epoch: Int, create: DSLContext = dslContext): Boolean {
        return create.fetchCount(
            create.select(REPORTS.ID)
                .from(REPORTS)
                .where(REPORTS.USER_ID.eq(userId))
                .and(REPORTS.EPOCH.eq(epoch))
        ) != 0
    }

    fun getUserLocationReport(userId: Int, epoch: Int, create: DSLContext = dslContext): LocationReport {
        return create.select()
            .from(REPORTS)
            .where(REPORTS.EPOCH.eq(epoch))
            .fetchOne()?.map {
                getUserLocationReport(
                    it[REPORTS.ID], ReportInfo(
                        id = it[REPORTS.USER_ID].toInt(),
                        epoch = it[REPORTS.EPOCH].toInt(),
                        coordinates = Coordinates(
                            x = it[REPORTS.X].toInt(),
                            y = it[REPORTS.Y].toInt()
                        )
                    )
                )
            } ?: throw UserReportNotFoundException(userId, epoch)
    }

    fun getEpochReports(epoch: Int, create: DSLContext = dslContext): List<LocationReport> {
        val reports = create.select()
            .from(REPORTS)
            .where(REPORTS.EPOCH.eq(epoch))
            .fetch().map {
                it[REPORTS.ID] to ReportInfo(
                    id = it[REPORTS.USER_ID].toInt(),
                    epoch = it[REPORTS.EPOCH].toInt(),
                    coordinates = Coordinates(
                        x = it[REPORTS.X].toInt(),
                        y = it[REPORTS.Y].toInt()
                    )
                )
            }
        return reports.map { (reportId, report) ->
            getUserLocationReport(reportId, report)
        }
    }

    private fun getUserLocationReport(
        reportId: Int,
        report: ReportInfo,
        create: DSLContext = dslContext
    ): LocationReport {
        val proofs = create.select()
            .from(PROOFS)
            .where(PROOFS.REPORT_ID.eq(reportId))
            .fetch().map {
                Proof(
                    requester = report.id,
                    prover = it[PROOFS.OTHER_USER_ID].toInt(),
                    epoch = report.epoch,
                    signature = it[PROOFS.SIGNATURE]
                )
            }

        // SIGNATURE IS NOT USED IN THIS CASE
        return LocationReport(
            id = report.id,
            epoch = report.epoch,
            location = report.coordinates,
            proofs = proofs,
            signature = ""
        )
    }

    fun getUserReport(userId: Int, epoch: Int, create: DSLContext = dslContext): ReportInfo {
        return create.select()
            .from(REPORTS)
            .where(REPORTS.USER_ID.eq(userId))
            .and(REPORTS.EPOCH.eq(epoch))
            .fetchOne()?.map {
                ReportInfo(
                    id = it[REPORTS.USER_ID].toInt(),
                    epoch = it[REPORTS.EPOCH].toInt(),
                    coordinates = Coordinates(
                        x = it[REPORTS.X].toInt(),
                        y = it[REPORTS.Y].toInt()
                    )
                )
            } ?: throw UserReportNotFoundException(userId, epoch)
    }

    fun saveUserReport(
        epoch: Int,
        user: Int,
        coordinates: Coordinates,
        proofs: List<Proof>,
        create: DSLContext = dslContext
    ) {
        create.transaction { configuration ->
            val transaction = DSL.using(configuration)
            val reportId: Int = transaction.insertInto(REPORTS)
                .set(REPORTS.EPOCH, epoch)
                .set(REPORTS.USER_ID, user)
                .set(REPORTS.X, coordinates.x)
                .set(REPORTS.Y, coordinates.y)
                .returningResult(REPORTS.ID)
                .fetchOne()?.map { it[REPORTS.ID] }
                ?: throw ReportCreationException(epoch, user)

            proofs.forEach {
                saveUserProof(it, reportId, transaction)
            }
        }
    }

    private fun saveUserProof(proof: Proof, reportId: Int, create: DSLContext): Int {
        return create.insertInto(PROOFS)
            .set(PROOFS.REPORT_ID, reportId)
            .set(PROOFS.OTHER_USER_ID, proof.prover)
            .set(PROOFS.SIGNATURE, proof.signature)
            .returningResult(PROOFS.ID)
            .fetchOne()?.map { it[PROOFS.ID] }
            ?: throw ProofCreationException(reportId, proof.prover)
    }

}

private fun <R : Record> InsertSetMoreStep<R>.set(column: TableField<R, UInteger>, value: Int): InsertSetMoreStep<R> {
    return set(column, UInteger.valueOf(value))
}

private fun <R : Record> InsertSetStep<R>.set(column: TableField<R, UInteger>, value: Int): InsertSetMoreStep<R> {
    return set(column, UInteger.valueOf(value))
}

/**
 * Helper extension to avoid repetition
 */
private fun <R : Record> TableField<R, UInteger>.eq(other: Int): Condition {
    return eq(UInteger.valueOf(other))
}

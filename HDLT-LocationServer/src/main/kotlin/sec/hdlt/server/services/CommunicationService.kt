package sec.hdlt.server.services

import sec.hdlt.server.domain.*

class CommunicationService {
    companion object {
        private var id = 0
        private var servers = 0

        private lateinit var writeService: ServerToServerWriteService
        private lateinit var readService: ServerToServerReadService

        // Common process values
        private var timestampValue = Pair<Int, LocationReport?>(0, null)
        private var readList = mutableListOf<Pair<Int, LocationReport?>>()
        private var readId = 0

        // Writer process values
        private var writtenTimestamp = 0
        private var acknowledgments = mutableMapOf<Int, Int>()

        // Reader process values
        private var readValue: LocationReport? = null
        private var reading: Boolean = false

        fun initValues(
            serverId: Int,
            numberOfServers: Int
        ) {
            id = serverId
            servers = numberOfServers
            writeService = ServerToServerWriteService(serverId, numberOfServers)
            readService = ServerToServerReadService(serverId, numberOfServers)
        }

        // ------------------------------ Write Operations ------------------------------
        suspend fun write(report: LocationReport): Boolean {
            println("[EPOCH ${report.epoch}] new write from user ${report.id}")

            readId++
            writtenTimestamp++
            acknowledgments[writtenTimestamp] = 1

            return writeService.writeBroadCast(id, writtenTimestamp, report)
        }

        fun deliverWrite(serverId: Int, timeStamp: Int, report: LocationReport): Triple<Int, Int, Boolean> {
            println("[EPOCH ${report.epoch}] received a write request from server $serverId")

            if (timeStamp > timestampValue.first)
                timestampValue = Pair(timeStamp, report)

            return Triple(id, timeStamp, true)
        }

        suspend fun deliverAck(epoch: Int, serverId: Int, timeStamp: Int, acknowledgement: Boolean): Boolean {
            println("[EPOCH $epoch] Received a $acknowledgement from server $serverId")

            // FIXME Lista de ack por (serverId, timestamp) ??
            acknowledgments[timeStamp] = acknowledgments[timeStamp]!!.inc()
            if (acknowledgments[timeStamp]!! > servers / 2) {
                acknowledgments[timeStamp] = 1
                if (reading) {
                    reading = false
                    // Não sei bem o que fazer com este readReturn
                    //readService.readReturn(readValue)

                } else {
                    println("[EPOCH $epoch] Received all acknowledgements. Saving report ...")
                    return true
                }
            }
            return false
        }

        // ------------------------------ Read Operations ------------------------------
        suspend fun read(userId: Int, epoch: Int, fLine: Int): Pair<Int, Int> {
            println("[EPOCH $epoch] new read from user $userId")

            readId++
            acknowledgments[writtenTimestamp] = 1
            readList.clear()
            reading = true

            return readService.readBroadCast(id, readId, userId, epoch, fLine)
        }

        suspend fun deliverRead(serverId: Int, epoch: Int, fLine: Int): Pair<Int, LocationReport> {
            println("[EPOCH $epoch] received a read request from server $serverId")

            // Não sei se temos de ir buscar o report à base de dados ou basta usar o timestampValue, visto que foi
            // updated quando este server respondeu a um pedido de write
            /*val locationResponse = LocationReportService.validateLocationReport(
                Database.reportDAO.getUserLocationReport(userId, epoch),
                Database.reportDAO.getEpochReports(epoch),
                fLine
            )*/

            return Pair(timestampValue.first, timestampValue.second!!)
        }

        suspend fun deliverValue(serverId: Int, readId: Int, timeStamp: Int, report: LocationReport): Boolean {
            println("[EPOCH ${report.epoch}] Received a value from server $serverId")

            readList[serverId] = Pair(timeStamp, report)
            if (readList.size > servers / 2) {
                var maxPair = readList[0]
                readList.forEach { pair ->
                    if (pair.first > maxPair.first) maxPair = pair
                }
                readList.clear()

                println("[EPOCH ${report.epoch}] Received all values. Writing-Back report ...")
                writeService.writeBroadCast(readId, maxPair.first, maxPair.second!!)
                return true
            }
            return false
        }
    }
}
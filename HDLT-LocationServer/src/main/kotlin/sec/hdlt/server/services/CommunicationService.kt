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

        suspend fun deliverAcks(epoch: Int, serverId: Int, timeStamp: Int, acknowledgement: Boolean): Boolean {
            println("[EPOCH $epoch] Received a $acknowledgement from server $serverId")

            // FIXME Lista de ack por (serverId, timestamp) ??
            acknowledgments[timeStamp] = acknowledgments[timeStamp]!!.inc()
            if (acknowledgments[timeStamp]!! > servers / 2) {
                acknowledgments[timeStamp] = 1
                if (reading) {
                    reading = false
                    //readService.readReturn(readValue)

                } else {
                    println("[EPOCH $epoch] Received all acknowledgements. Saving report ...")
                    return true
                }
            }
            return false
        }

        // ------------------------------ Read Operations ------------------------------
        suspend fun read() {
            readId++
            acknowledgments[writtenTimestamp] = 1
            readList.clear()
            reading = true

            readService.readBroadCast(id, readId)
        }

        suspend fun deliverRead(serverId: Int, readId: Int): Pair<Int, LocationReport> {
            return Pair(timestampValue.first, timestampValue.second!!)
        }

        suspend fun deliverValue(serverId: Int, readId: Int, timeStamp: Int, report: LocationReport) {
            readList[serverId] = Pair(timeStamp, report)
            if (readList.size > servers / 2) {
                var maxPair = readList[0]
                readList.forEach { pair ->
                    if (pair.first > maxPair.first) maxPair = pair
                }
                readList.clear()
                writeService.writeBroadCast(readId, maxPair.first, maxPair.second!!)
            }
        }
    }
}
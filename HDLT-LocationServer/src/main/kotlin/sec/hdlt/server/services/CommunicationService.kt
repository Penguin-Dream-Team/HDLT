package sec.hdlt.server.services

import org.slf4j.LoggerFactory
import sec.hdlt.server.domain.*

class CommunicationService {
    companion object {
        private val logger = LoggerFactory.getLogger("Communication")
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
        private var acknowledgments = 0

        // Reader process values
        private var readValue: LocationReport? = null
        private var reading: Boolean = false

        fun initValues(
            serverId: Int,
            numberOfServers: Int,
            serverToServerWriteService: ServerToServerWriteService,
            serverToServerReadService: ServerToServerReadService
        ) {
            id = serverId
            servers = numberOfServers
            writeService = serverToServerWriteService
            readService = serverToServerReadService
        }

        // ------------------------------ Write Operations ------------------------------
        suspend fun write(report: LocationReport) {
            readId++
            writtenTimestamp++
            acknowledgments = 0

            writeService.writeBroadCast(id, writtenTimestamp, report)
        }

        suspend fun deliverWrite(serverId: Int, timeStamp: Int, report: LocationReport) {
            if (timeStamp > timestampValue.first)
                timestampValue = Pair(timeStamp, report)

            writeService.writeAcknowledgment(id, timeStamp, true)
        }

        suspend fun deliverAcks(serverId: Int, acknlowdgment: Boolean, timeStamp: Int) {
            acknowledgments++
            if (acknowledgments > servers / 2) {
                acknowledgments = 0
                if (reading) {
                    reading = false
                    readService.readReturn(readValue)

                } else {
                    writeService.writeReturn()
                }
            }
        }

        // ------------------------------ Read Operations ------------------------------
        suspend fun read() {
            readId++
            acknowledgments = 0
            readList.clear()
            reading = true

            readService.readBroadCast(id, readId)
        }

        suspend fun deliverRead(serverId: Int, readId: Int) {
            readService.readAcknowledgment(readId, timestampValue.first, timestampValue.second!!)
        }

        suspend fun deliverValue(serverId: Int, value: Any, readId: Int, timeStamp: Int, report: LocationReport) {
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
package sec.hdlt.server.services

import org.slf4j.LoggerFactory
import sec.hdlt.server.data.*

class CommunicationService {
    companion object {
        private val logger = LoggerFactory.getLogger("Communication")
        private var id = 0
        private var servers = 0

        // Common process values
        private var timestampValue = Pair<Int, LocationReport?>(0, null)
        private var readList = mutableListOf<Pair<Int, LocationReport?>>()
        private var readId = 0

        // Writer process values
        private var writtenTimestamp = 0
        private var acknowledgments = 0

        fun initValues(serverId: Int, numberOfServers: Int) {
            id = serverId
            servers = numberOfServers
        }

        fun write(report: LocationReport) {
            writtenTimestamp++
            acknowledgments = 0
            // Trigger BestEffortBroadcast Write(id, writtenTimestamp, report)
        }

        fun deliverWrite(serverId: Int, timeStamp: Int, report: LocationReport) {
            if (timeStamp > timestampValue.first)
                timestampValue = Pair(timeStamp, report)
            // Trigger Send PerfectLink Acknowledgments(serverId, true, timeStamp)
        }

        fun deliverAcks(serverId: Int, acknlowdgment: Boolean, timeStamp: Int) {
            acknowledgments++
            if (acknowledgments > servers / 2) {
                acknowledgments = 0
                // Trigger Write Return
            }
        }

        fun read() {
            readId++
            readList.clear()
            // Trigger BestEffortBroadcast Read(serverId, readId)
        }

        fun deliverRead(serverId: Int, readId: Int) {
            // Trigger Send PerfectLink Read(serverId, Value, readId, timeStamp, val)
        }

        fun deliverValue(serverId: Int, value: Any, readId: Int, timeStamp: Int, report: LocationReport) {
            readList[serverId] = Pair(timeStamp, report)
            if (readList.size > servers / 2) {
                var maxPair = readList[0]
                readList.forEach { pair ->
                    if (pair.first > maxPair.first) maxPair = pair
                }
                readList.clear()
                // Trigger Read Return(maxPair.second)
            }
        }
    }
}
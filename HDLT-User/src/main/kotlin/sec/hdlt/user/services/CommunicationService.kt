package sec.hdlt.user.services

import sec.hdlt.user.dto.LocationResponse
import sec.hdlt.user.dto.ReportDto

class CommunicationService {
    companion object {
        private var servers = 0


        // Common process values
        //private var timestampValue = Pair<Int, ReportDto?>(0, null)
        private var readList = mutableListOf<Pair<Int, ReportDto?>>()
        private var readId = 0

        // Writer process values
        private var writtenTimestamp = 0
        private var acknowledgments = mutableMapOf<Int, Int>()

        // Reader process values
        //private var readValue: ReportDto? = null
        private var reading: Boolean = false

        fun initValues(numberOfServers: Int) {
            servers = numberOfServers
        }

        // ------------------------------ Write Operations ------------------------------
        suspend fun write(report: ReportDto) {
            println("[EPOCH ${report.epoch}] new write from user ${report.id}")

            readId++
            writtenTimestamp++
            acknowledgments[writtenTimestamp] = 1
        }

        suspend fun deliverAck(serverId: Int, epoch: Int, acknowledgement: Boolean): Boolean {
            println("[EPOCH $epoch] Received a $acknowledgement from server $serverId")

            // FIXME Lista de ack por (serverId, timestamp) ??
            acknowledgments[writtenTimestamp] = acknowledgments[writtenTimestamp]!!.inc()
            if (acknowledgments[writtenTimestamp]!! > servers / 2) {
                acknowledgments[writtenTimestamp] = 1
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
        suspend fun read(userId: Int, epoch: Int) {
            println("[EPOCH $epoch] new read from user $userId")

            readId++
            acknowledgments[writtenTimestamp] = 1
            readList.clear()
            reading = true
        }

        suspend fun deliverValue(serverId: Int, report: LocationResponse): Boolean {
            println("[EPOCH ${report.epoch}] Received a value from server $serverId")

            //readList[serverId] = Pair(writtenTimestamp, reportDto)
            if (readList.size > servers / 2) {
                var maxPair = readList[0]
                readList.forEach { pair ->
                    if (pair.first > maxPair.first) maxPair = pair
                }
                readList.clear()

                println("[EPOCH ${report.epoch}] Received all values. Writing-Back report ...")
                //writeService.writeBroadCast(readId, maxPair.first, maxPair.second!!)
                return true
            }
            return false
        }
    }
}
package sec.hdlt.server.domain

import sec.hdlt.server.BASE_PORT

data class ServerInfo(val id: Int, val port: Int = id + BASE_PORT) {
}
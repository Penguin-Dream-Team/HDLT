package sec.hdlt.server.antispam

import java.math.BigInteger
import java.nio.ByteBuffer

data class ProofOfWorkRequest(
    val data: String,
    val workTarget: BigInteger,
) {
    fun toByteArrayList(): MutableList<ByteArray> {
        return mutableListOf(
            data.toByteArray(),
            "SHA256".toByteArray(),
            ByteBuffer.allocate(8).putLong(0).array() //for nonce
        )
    }

    fun toProofWork(hash: String, nonce: Long): ProofOfWork {
        return ProofOfWork(
            data, hash, nonce
        )
    }
}
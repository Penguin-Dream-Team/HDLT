package sec.hdlt.user.antispam

import java.math.BigInteger
import java.nio.ByteBuffer
import java.time.Instant

data class ProofOfWorkRequest(
    val data: String,
    val workTarget: BigInteger,
    val timestamp: Long = Instant.now().toEpochMilli()
) {
    fun toByteArrayList(): MutableList<ByteArray> {
        return mutableListOf(
            data.toByteArray(),
            workTarget.toByteArray(),
            "SHA256".toByteArray(),
            ByteBuffer.allocate(8).putLong(timestamp).array(),
            ByteBuffer.allocate(8).putLong(0).array() //for nonce
        )
    }

    fun toProofWork(hash: String, nonce: Long): ProofOfWork {
        return ProofOfWork(
            data, workTarget, timestamp, hash, nonce
        )
    }
}
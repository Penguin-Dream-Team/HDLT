package sec.hdlt.user.antispam

import sec.hdlt.protos.server.Report
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.*

data class ProofOfWork(
    val data: String,
    val workTarget: BigInteger,
    val timestamp: Long,
    val hash: String,
    val nonce: Long
) {
    fun toByteArrayList(): MutableList<ByteArray> {
        return mutableListOf(
            data.toByteArray(),
            workTarget.toByteArray(),
            "SHA256".toByteArray(),
            ByteBuffer.allocate(8).putLong(timestamp).array(),
            ByteBuffer.allocate(8).putLong(nonce).array()//for nonce
        )
    }

    fun toGrpcProof(): Report.ProofOfWork {
        return Report.ProofOfWork.newBuilder().apply {
            data = this@ProofOfWork.data
            workTarget = Base64.getEncoder().encodeToString(this@ProofOfWork.workTarget.toByteArray())
            timestamp = this@ProofOfWork.timestamp
            hash = this@ProofOfWork.hash
            nonce = this@ProofOfWork.nonce
        }.build()
    }

    companion object {
        fun fromGrpcProof(proof: Report.ProofOfWork): ProofOfWork {
            return ProofOfWork(
                proof.data,
                BigInteger(Base64.getDecoder().decode(proof.workTarget)),
                proof.timestamp,
                proof.hash,
                proof.nonce
            )
        }
    }
}
package sec.hdlt.ha.antispam

import sec.hdlt.protos.server.Report
import java.nio.ByteBuffer
import java.util.*

data class ProofOfWork(
    val data: String,
    val hash: String,
    val nonce: Long
) {
    fun toByteArrayList(): MutableList<ByteArray> {
        return mutableListOf(
            data.toByteArray(),
            "SHA256".toByteArray(),
            ByteBuffer.allocate(8).putLong(nonce).array() //for nonce
        )
    }

    fun toGrpcProof(): Report.ProofOfWork {
        return Report.ProofOfWork.newBuilder().apply {
            data = this@ProofOfWork.data
            hash = this@ProofOfWork.hash
            nonce = this@ProofOfWork.nonce
        }.build()
    }

    companion object {
        fun fromGrpcProof(proof: Report.ProofOfWork): ProofOfWork {
            return ProofOfWork(
                proof.data,
                proof.hash,
                proof.nonce
            )
        }
    }
}
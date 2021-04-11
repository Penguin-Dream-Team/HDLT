package sec.hdlt.user.domain;

public class Proof {
    private final int requester;
    private final int prover;
    private final int epoch;
    private final String signature;

    public Proof(int requester, int prover, int epoch, String signature) {
        this.requester = requester;
        this.prover = prover;
        this.epoch = epoch;
        this.signature = signature;
    }

    public int getRequester() {
        return requester;
    }

    public int getProver() {
        return prover;
    }

    public int getEpoch() {
        return epoch;
    }

    public String getSignature() {
        return signature;
    }
}

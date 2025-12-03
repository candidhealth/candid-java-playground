package com.candid.api.inference;

/**
 * Result of a claim denial prediction.
 */
public class DenialPrediction {

    private final boolean willBeDenied;
    private final double probability;
    private final double rawScore;

    public DenialPrediction(boolean willBeDenied, double probability, double rawScore) {
        this.willBeDenied = willBeDenied;
        this.probability = probability;
        this.rawScore = rawScore;
    }

    /**
     * Returns true if the claim is predicted to be denied.
     */
    public boolean willBeDenied() {
        return willBeDenied;
    }

    /**
     * Returns the calibrated probability of denial in [0, 1].
     */
    public double getProbability() {
        return probability;
    }

    /**
     * Returns the raw model score before calibration.
     */
    public double getRawScore() {
        return rawScore;
    }

    @Override
    public String toString() {
        return String.format("DenialPrediction{willBeDenied=%s, probability=%.4f, rawScore=%.4f}",
                willBeDenied, probability, rawScore);
    }
}

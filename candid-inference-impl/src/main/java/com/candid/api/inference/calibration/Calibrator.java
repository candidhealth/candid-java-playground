package com.candid.api.inference.calibration;

/**
 * Interface for probability calibration methods.
 * Calibrators transform raw model scores into calibrated probabilities
 * that better represent true likelihoods.
 */
public interface Calibrator {

    /**
     * Calibrates a raw model score to a probability.
     *
     * @param rawScore Raw score from the model
     * @return Calibrated probability in [0, 1]
     */
    double calibrate(double rawScore);

    /**
     * Creates a no-op calibrator that returns the raw score unchanged.
     */
    static Calibrator identity() {
        return rawScore -> rawScore;
    }
}

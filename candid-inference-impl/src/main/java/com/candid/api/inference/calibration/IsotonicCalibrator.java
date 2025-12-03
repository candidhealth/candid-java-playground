package com.candid.api.inference.calibration;

import java.util.Arrays;

/**
 * Isotonic regression calibrator that applies a piecewise constant mapping.
 * Uses binary search to find the appropriate calibrated value for a given score.
 *
 * This calibrator maintains the monotonic ordering of predictions while
 * adjusting them to better match observed frequencies.
 */
public class IsotonicCalibrator implements Calibrator {

    private final double[] thresholds;
    private final double[] values;

    /**
     * Creates an isotonic calibrator with the given threshold-value pairs.
     *
     * @param thresholds Sorted array of score thresholds
     * @param values Corresponding calibrated probability values
     */
    public IsotonicCalibrator(double[] thresholds, double[] values) {
        if (thresholds.length != values.length) {
            throw new IllegalArgumentException(
                    "Thresholds and values must have the same length: " +
                    thresholds.length + " vs " + values.length
            );
        }

        this.thresholds = thresholds;
        this.values = values;
    }

    @Override
    public double calibrate(double rawScore) {
        if (thresholds.length == 0) {
            // No calibration data, return score as-is
            return rawScore;
        }

        // Find the appropriate bucket using binary search
        int index = Arrays.binarySearch(thresholds, rawScore);

        if (index >= 0) {
            // Exact match found
            return values[index];
        } else {
            // Not exact match: binarySearch returns (-(insertion point) - 1)
            int insertionPoint = -(index + 1);

            if (insertionPoint == 0) {
                // Score is below the lowest threshold
                return values[0];
            } else if (insertionPoint >= thresholds.length) {
                // Score is above the highest threshold
                return values[values.length - 1];
            } else {
                // Score falls between thresholds[insertionPoint-1] and thresholds[insertionPoint]
                // Use the value for the lower threshold
                return values[insertionPoint - 1];
            }
        }
    }

    public double[] getThresholds() {
        return thresholds;
    }

    public double[] getValues() {
        return values;
    }
}

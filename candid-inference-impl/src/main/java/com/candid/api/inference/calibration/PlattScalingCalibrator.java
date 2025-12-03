package com.candid.api.inference.calibration;

/**
 * Platt scaling calibrator that applies a sigmoid transformation to raw scores.
 * Formula: P(y=1|score) = 1 / (1 + exp(a * score + b))
 *
 * The parameters a and b are typically learned from a validation set by fitting
 * a logistic regression on the raw model scores.
 */
public class PlattScalingCalibrator implements Calibrator {

    private final double a;
    private final double b;

    /**
     * Creates a Platt scaling calibrator with the given parameters.
     *
     * @param a Coefficient (slope) parameter
     * @param b Intercept parameter
     */
    public PlattScalingCalibrator(double a, double b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public double calibrate(double rawScore) {
        // Apply sigmoid: 1 / (1 + exp(a * score + b))
        double z = a * rawScore + b;
        return 1.0 / (1.0 + Math.exp(z));
    }

    public double getA() {
        return a;
    }

    public double getB() {
        return b;
    }
}

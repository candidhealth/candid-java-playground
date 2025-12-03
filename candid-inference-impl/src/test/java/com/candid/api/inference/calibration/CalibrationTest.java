package com.candid.api.inference.calibration;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

class CalibrationTest {

    @Test
    void testPlattScaling() {
        PlattScalingCalibrator calibrator = new PlattScalingCalibrator(-1.5, 0.3);

        // Test some calibration values
        // With a=-1.5, b=0.3: P = 1 / (1 + exp(-1.5*score + 0.3))

        double p0 = calibrator.calibrate(0.0);
        assertThat(p0).isBetween(0.0, 1.0);
        assertThat(p0).isCloseTo(0.426, within(0.001));  // 1/(1+exp(0.3))

        double p1 = calibrator.calibrate(1.0);
        assertThat(p1).isBetween(0.0, 1.0);
        assertThat(p1).isCloseTo(0.769, within(0.001));  // 1/(1+exp(-1.2))

        double pNeg1 = calibrator.calibrate(-1.0);
        assertThat(pNeg1).isBetween(0.0, 1.0);
        assertThat(pNeg1).isCloseTo(0.142, within(0.001));  // 1/(1+exp(1.8))
    }

    @Test
    void testPlattScalingExtremeValues() {
        PlattScalingCalibrator calibrator = new PlattScalingCalibrator(-1.0, 0.0);

        // Very high score should give probability close to 1
        double highScore = calibrator.calibrate(10.0);
        assertThat(highScore).isCloseTo(1.0, within(0.01));

        // Very low score should give probability close to 0
        double lowScore = calibrator.calibrate(-10.0);
        assertThat(lowScore).isCloseTo(0.0, within(0.01));
    }

    @Test
    void testIsotonicCalibration() {
        double[] thresholds = {0.0, 0.3, 0.6, 0.9};
        double[] values = {0.1, 0.4, 0.7, 0.95};

        IsotonicCalibrator calibrator = new IsotonicCalibrator(thresholds, values);

        // Exact threshold matches
        assertThat(calibrator.calibrate(0.0)).isEqualTo(0.1);
        assertThat(calibrator.calibrate(0.3)).isEqualTo(0.4);
        assertThat(calibrator.calibrate(0.6)).isEqualTo(0.7);

        // Values between thresholds should use the lower threshold's value
        assertThat(calibrator.calibrate(0.2)).isEqualTo(0.1);  // Between 0.0 and 0.3
        assertThat(calibrator.calibrate(0.5)).isEqualTo(0.4);  // Between 0.3 and 0.6
        assertThat(calibrator.calibrate(0.8)).isEqualTo(0.7);  // Between 0.6 and 0.9

        // Values below minimum threshold
        assertThat(calibrator.calibrate(-0.5)).isEqualTo(0.1);

        // Values above maximum threshold
        assertThat(calibrator.calibrate(1.5)).isEqualTo(0.95);
    }

    @Test
    void testIsotonicEmptyArrays() {
        IsotonicCalibrator calibrator = new IsotonicCalibrator(new double[0], new double[0]);

        // With no calibration data, should return the raw score
        assertThat(calibrator.calibrate(0.5)).isEqualTo(0.5);
        assertThat(calibrator.calibrate(0.9)).isEqualTo(0.9);
    }

    @Test
    void testIsotonicMismatchedArrays() {
        double[] thresholds = {0.0, 0.5};
        double[] values = {0.1};  // Wrong length

        assertThatThrownBy(() -> new IsotonicCalibrator(thresholds, values))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must have the same length");
    }

    @Test
    void testLoadCalibrationConfig() throws IOException {
        CalibrationConfig config = CalibrationConfig.fromResource("models/calibration_params.json");

        assertThat(config.getType()).isEqualTo(CalibrationConfig.CalibrationType.PLATT_SCALING);

        Calibrator calibrator = config.createCalibrator();
        assertThat(calibrator).isInstanceOf(PlattScalingCalibrator.class);

        PlattScalingCalibrator platt = (PlattScalingCalibrator) calibrator;
        assertThat(platt.getA()).isEqualTo(-1.5);
        assertThat(platt.getB()).isEqualTo(0.3);
    }

    @Test
    void testIdentityCalibrator() {
        Calibrator identity = Calibrator.identity();

        assertThat(identity.calibrate(0.0)).isEqualTo(0.0);
        assertThat(identity.calibrate(0.5)).isEqualTo(0.5);
        assertThat(identity.calibrate(1.0)).isEqualTo(1.0);
        assertThat(identity.calibrate(0.723)).isEqualTo(0.723);
    }
}

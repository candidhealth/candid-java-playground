package com.candid.api.inference.calibration;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Configuration for probability calibration.
 * Loads calibration parameters from JSON and creates the appropriate Calibrator.
 */
public class CalibrationConfig {

    private final CalibrationType type;
    private final Map<String, Object> parameters;

    public CalibrationConfig(CalibrationType type, Map<String, Object> parameters) {
        this.type = type;
        this.parameters = parameters;
    }

    /**
     * Loads calibration configuration from a JSON resource.
     *
     * @param resourcePath Path to the JSON file in resources
     * @return CalibrationConfig instance
     * @throws IOException if the resource cannot be read
     */
    public static CalibrationConfig fromResource(String resourcePath) throws IOException {
        try (InputStream is = CalibrationConfig.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            Gson gson = new Gson();
            return gson.fromJson(
                    new InputStreamReader(is, StandardCharsets.UTF_8),
                    CalibrationConfig.class
            );
        }
    }

    /**
     * Creates a Calibrator instance based on this configuration.
     *
     * @return Configured calibrator
     */
    public Calibrator createCalibrator() {
        return switch (type) {
            case PLATT_SCALING -> {
                double a = getDoubleParameter("a");
                double b = getDoubleParameter("b");
                yield new PlattScalingCalibrator(a, b);
            }
            case ISOTONIC -> {
                double[] thresholds = getDoubleArrayParameter("thresholds");
                double[] values = getDoubleArrayParameter("values");
                yield new IsotonicCalibrator(thresholds, values);
            }
            case NONE -> Calibrator.identity();
        };
    }

    private double getDoubleParameter(String name) {
        Object value = parameters.get(name);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        throw new IllegalArgumentException("Parameter '" + name + "' is not a number: " + value);
    }

    private double[] getDoubleArrayParameter(String name) {
        Object value = parameters.get(name);
        if (value instanceof Iterable) {
            return ((Iterable<?>) value).spliterator()
                    .estimateSize() > 0 ?
                    toDoubleArray((Iterable<?>) value) :
                    new double[0];
        }
        throw new IllegalArgumentException("Parameter '" + name + "' is not an array: " + value);
    }

    private double[] toDoubleArray(Iterable<?> iterable) {
        return java.util.stream.StreamSupport.stream(iterable.spliterator(), false)
                .map(o -> ((Number) o).doubleValue())
                .mapToDouble(Double::doubleValue)
                .toArray();
    }

    public CalibrationType getType() {
        return type;
    }

    /**
     * Types of calibration methods supported.
     */
    public enum CalibrationType {
        @SerializedName("PLATT_SCALING")
        PLATT_SCALING,

        @SerializedName("ISOTONIC")
        ISOTONIC,

        @SerializedName("NONE")
        NONE
    }
}

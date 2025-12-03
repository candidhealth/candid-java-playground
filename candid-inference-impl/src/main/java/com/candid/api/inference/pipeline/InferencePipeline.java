package com.candid.api.inference.pipeline;

import ai.onnxruntime.*;
import com.candid.api.inference.calibration.Calibrator;
import com.candid.api.inference.model.FeatureMetadata;
import com.candid.api.inference.model.FeatureVector;

import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Map;

/**
 * Complete inference pipeline that orchestrates feature encoding, ONNX model prediction,
 * and probability calibration.
 *
 * Pipeline flow:
 * 1. Raw features (Map) → FeatureVector (encoded, ordered)
 * 2. FeatureVector → OnnxTensor → ONNX Runtime prediction
 * 3. Raw score → Calibrator → Calibrated probability
 */
public class InferencePipeline {

    private final OrtSession session;
    private final OrtEnvironment environment;
    private final FeatureMetadata featureMetadata;
    private final Calibrator calibrator;
    private final String inputName;

    /**
     * Creates an inference pipeline with the given components.
     *
     * @param session ONNX Runtime session with loaded model
     * @param environment ONNX Runtime environment
     * @param featureMetadata Feature definitions and encodings
     * @param calibrator Probability calibrator
     * @param inputName Name of the model's input tensor (e.g., "float_input")
     */
    public InferencePipeline(
            OrtSession session,
            OrtEnvironment environment,
            FeatureMetadata featureMetadata,
            Calibrator calibrator,
            String inputName) {
        this.session = session;
        this.environment = environment;
        this.featureMetadata = featureMetadata;
        this.calibrator = calibrator;
        this.inputName = inputName;
    }

    /**
     * Runs inference on raw feature values.
     *
     * @param rawFeatures Map of feature name to value
     * @return Prediction result with raw and calibrated scores
     * @throws OrtException if ONNX Runtime prediction fails
     */
    public PredictionResult predict(Map<String, Object> rawFeatures) throws OrtException {
        // Step 1: Build feature vector (handles encoding and ordering)
        FeatureVector featureVector = new FeatureVector.Builder(featureMetadata)
                .setFeatures(rawFeatures)
                .build();

        // Step 2: Create ONNX tensor from feature values
        float[] values = featureVector.getValues();
        long[] shape = {1, values.length};  // [batch_size=1, num_features]

        OnnxTensor tensor = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(values),
                shape
        );

        try {
            // Step 3: Run ONNX model inference
            Map<String, OnnxTensor> inputs = Collections.singletonMap(inputName, tensor);
            OrtSession.Result result = session.run(inputs);

            try {
                // Step 4: Extract raw score from model output
                // Assumes model outputs a 2D array [batch_size, num_outputs]
                float[][] output = (float[][]) result.get(0).getValue();
                double rawScore = output[0][0];

                // Step 5: Apply calibration
                double calibratedProbability = calibrator.calibrate(rawScore);

                return new PredictionResult(rawScore, calibratedProbability);
            } finally {
                result.close();
            }
        } finally {
            tensor.close();
        }
    }

    /**
     * Closes the ONNX session and releases resources.
     */
    public void close() throws OrtException {
        if (session != null) {
            session.close();
        }
    }

    /**
     * Result of a prediction containing both raw and calibrated scores.
     */
    public static class PredictionResult {
        private final double rawScore;
        private final double calibratedProbability;

        public PredictionResult(double rawScore, double calibratedProbability) {
            this.rawScore = rawScore;
            this.calibratedProbability = calibratedProbability;
        }

        /**
         * Gets the raw model score before calibration.
         */
        public double getRawScore() {
            return rawScore;
        }

        /**
         * Gets the calibrated probability in [0, 1].
         */
        public double getCalibratedProbability() {
            return calibratedProbability;
        }

        /**
         * Determines if the prediction is positive based on a threshold.
         *
         * @param threshold Decision threshold (typically 0.5)
         * @return true if calibrated probability >= threshold
         */
        public boolean isPredictedPositive(double threshold) {
            return calibratedProbability >= threshold;
        }

        /**
         * Determines if the claim is predicted to be denied (threshold = 0.5).
         */
        public boolean isPredictedDenial() {
            return isPredictedPositive(0.5);
        }

        @Override
        public String toString() {
            return String.format("PredictionResult{rawScore=%.4f, calibratedProbability=%.4f, denied=%s}",
                    rawScore, calibratedProbability, isPredictedDenial());
        }
    }
}

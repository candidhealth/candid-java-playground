package com.candid.api.inference;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.candid.api.inference.calibration.CalibrationConfig;
import com.candid.api.inference.calibration.Calibrator;
import com.candid.api.inference.model.FeatureMetadata;
import com.candid.api.inference.pipeline.InferencePipeline;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * High-level API for predicting insurance claim denials.
 * Loads all resources (model, metadata, calibration) from classpath
 * and provides a simple predict() method.
 *
 * Example usage:
 * <pre>
 * DenialPredictor predictor = new DenialPredictor();
 *
 * ClaimData claim = new ClaimData.Builder()
 *     .setAmount(15000.00)
 *     .setPatientAge(45)
 *     .setProcedureCategory("SURGERY")
 *     .setProviderState("CA")
 *     .setPriorDenialCount(1)
 *     .build();
 *
 * DenialPrediction prediction = predictor.predict(claim);
 * System.out.println("Denied: " + prediction.willBeDenied());
 * System.out.println("Probability: " + prediction.getProbability());
 * </pre>
 */
public class DenialPredictor implements AutoCloseable {

    private final InferencePipeline pipeline;

    /**
     * Creates a DenialPredictor by loading default resources from classpath.
     *
     * @throws IOException if resource files cannot be loaded
     * @throws OrtException if ONNX model cannot be loaded
     */
    public DenialPredictor() throws IOException, OrtException {
        this(
                "models/claim_denial_model.onnx",
                "models/feature_metadata.json",
                "models/calibration_params.json",
                "float_input"  // Default ONNX input name
        );
    }

    /**
     * Creates a DenialPredictor with custom resource paths.
     *
     * @param modelPath Path to ONNX model file in resources
     * @param metadataPath Path to feature metadata JSON in resources
     * @param calibrationPath Path to calibration params JSON in resources
     * @param inputName Name of the ONNX model's input tensor
     * @throws IOException if resource files cannot be loaded
     * @throws OrtException if ONNX model cannot be loaded
     */
    public DenialPredictor(
            String modelPath,
            String metadataPath,
            String calibrationPath,
            String inputName) throws IOException, OrtException {

        // Load feature metadata
        FeatureMetadata metadata = FeatureMetadata.fromResource(metadataPath);

        // Load calibration configuration
        CalibrationConfig calibrationConfig = CalibrationConfig.fromResource(calibrationPath);
        Calibrator calibrator = calibrationConfig.createCalibrator();

        // Load ONNX model
        OrtEnvironment environment = OrtEnvironment.getEnvironment();
        byte[] modelBytes = readResourceBytes(modelPath);
        OrtSession session = environment.createSession(modelBytes);

        // Create inference pipeline
        this.pipeline = new InferencePipeline(
                session,
                environment,
                metadata,
                calibrator,
                inputName
        );
    }

    /**
     * Predicts whether a claim will be denied.
     *
     * @param claim Insurance claim data
     * @return Prediction result
     * @throws OrtException if inference fails
     */
    public DenialPrediction predict(ClaimData claim) throws OrtException {
        // Convert ClaimData to feature map
        Map<String, Object> features = new HashMap<>();
        features.put("claim_amount", claim.getAmount());
        features.put("patient_age", claim.getPatientAge());
        features.put("procedure_category", claim.getProcedureCategory());
        features.put("provider_state", claim.getProviderState());
        features.put("prior_denials", claim.getPriorDenialCount());

        // Run inference
        InferencePipeline.PredictionResult result = pipeline.predict(features);

        // Convert to DenialPrediction
        return new DenialPrediction(
                result.isPredictedDenial(),
                result.getCalibratedProbability(),
                result.getRawScore()
        );
    }

    /**
     * Closes the ONNX session and releases resources.
     */
    @Override
    public void close() throws OrtException {
        pipeline.close();
    }

    /**
     * Reads a resource file from the classpath as bytes.
     */
    private static byte[] readResourceBytes(String resourcePath) throws IOException {
        try (InputStream is = DenialPredictor.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return is.readAllBytes();
        }
    }
}

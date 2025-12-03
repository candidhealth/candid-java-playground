package com.candid.api.server;

import com.candid.api.ServiceLineDenialPredictionPayload;
import com.candid.api.ServiceLinePredictionResponse;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Denial prediction model service with XGBoost backend and model caching.
 * Caches the loaded model for 5 minutes to avoid repeated disk I/O.
 * Supports both single and batch inference.
 */
public class DenialPredictionModelService {
    private static final Logger logger = LoggerFactory.getLogger(DenialPredictionModelService.class);

    // Cache the XGBoost model for 5 minutes
    private final Cache<String, Booster> modelCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(1)
            .removalListener((key, value, cause) -> {
                logger.info("Model cache evicted: key={}, cause={}", key, cause);
                if (value instanceof Booster booster) {
                    try {
                        booster.dispose();
                    } catch (Exception e) {
                        logger.warn("Error disposing XGBoost model: {}", e.getMessage());
                    }
                }
            })
            .build();

    private final String modelPath;

    public DenialPredictionModelService(String modelPath) {
        this.modelPath = modelPath;
    }

    /**
     * Constructor that defaults to Desktop model path for development.
     */
    public DenialPredictionModelService() {
        this(getDefaultModelPath());
    }

    /**
     * Predicts denial probability for a single service line.
     */
    public ServiceLinePredictionResponse predict(ServiceLineDenialPredictionPayload payload) throws ModelException {
        Map<String, ServiceLinePredictionResponse> batchResults = predictBatch(List.of(payload));
        return batchResults.get(payload.getServiceLineId());
    }

    /**
     * Predicts denial probabilities for multiple service lines in a batch.
     * More efficient than individual predictions for multiple service lines.
     *
     * @param payloads List of service line payloads to predict
     * @return Map of service line ID to prediction response
     * @throws ModelException if batch prediction fails
     */
    public Map<String, ServiceLinePredictionResponse> predictBatch(List<ServiceLineDenialPredictionPayload> payloads) throws ModelException {
        if (payloads == null || payloads.isEmpty()) {
            return new HashMap<>();
        }

        try {
            Booster model = getOrLoadModel();

            // Use dummy predictions if no model is loaded
            float[][] predictions;
            if (model == null) {
                logger.info("Generating {} dummy predictions", payloads.size());
                predictions = generateDummyPredictions(payloads);
            } else {
                // Convert all payloads to a single DMatrix for batch inference
                DMatrix dmatrix = createBatchDMatrixFromPayloads(payloads);

                // Run batch prediction
                predictions = model.predict(dmatrix);

                if (predictions.length != payloads.size()) {
                    throw new ModelException("Prediction count mismatch: expected " + payloads.size() + ", got " + predictions.length);
                }
            }

            // Build response map
            Map<String, ServiceLinePredictionResponse> results = new HashMap<>();
            for (int i = 0; i < payloads.size(); i++) {
                ServiceLineDenialPredictionPayload payload = payloads.get(i);

                if (predictions[i].length == 0) {
                    throw new ModelException("Model returned empty prediction for service line: " + payload.getServiceLineId());
                }

                float rawScore = predictions[i][0];

                ServiceLinePredictionResponse response = ServiceLinePredictionResponse.newBuilder()
                        .setRawScore(rawScore)
                        .setProbability(rawScore)
                        .setRawReason("")
                        .setHumanReadableReason("")
                        .build();

                results.put(payload.getServiceLineId(), response);
            }

            logger.info("Completed batch prediction for {} service lines", payloads.size());
            return results;

        } catch (Exception e) {
            throw new ModelException("Batch prediction failed for " + payloads.size() + " service lines", e);
        }
    }

    private Booster getOrLoadModel() throws ModelException {
        // TODO: Replace with actual XGBoost model loading once model file is available
        // For now, return null to use dummy predictions
        logger.info("Using dummy model predictions (XGBoost model not yet available)");
        return null;
    }

    private DMatrix createBatchDMatrixFromPayloads(List<ServiceLineDenialPredictionPayload> payloads) throws Exception {
        int numRows = payloads.size();
        int numFeatures = getFeatureCount(); // Define the number of features your model expects

        // Create flattened feature array for all payloads
        float[] allFeatures = new float[numRows * numFeatures];

        for (int i = 0; i < payloads.size(); i++) {
            ServiceLineDenialPredictionPayload payload = payloads.get(i);
            float[] features = extractFeatures(payload);

            // Copy features into the flattened array
            System.arraycopy(features, 0, allFeatures, i * numFeatures, numFeatures);
        }

        // Create DMatrix with batch data
        return new DMatrix(allFeatures, numRows, numFeatures);
    }

    private float[] extractFeatures(ServiceLineDenialPredictionPayload payload) {
        return new float[] {
            payload.getHasPriorAuthorizationNumber() ? 1.0f : 0.0f,
            payload.getHasReferralNumber() ? 1.0f : 0.0f,
            payload.getHasReferringProvider() ? 1.0f : 0.0f,
            payload.getHasSupervisingProvider() ? 1.0f : 0.0f,
            payload.getHasInitialReferringProvider() ? 1.0f : 0.0f,
            payload.getHasOrderingProvider() ? 1.0f : 0.0f,
            payload.getIsDrugClaim() ? 1.0f : 0.0f,
            payload.getHasServiceLineDescription() ? 1.0f : 0.0f,
            payload.getQuantity(),
            payload.getChargePerUnit(),
            (float) payload.getDaysSinceEpoch(),
            // Add more features as needed...
        };
    }

    private int getFeatureCount() {
        // Return the number of features your model expects
        // Should match the length of the array returned by extractFeatures()
        return 11; // Update this as you add more features
    }

    private static String getDefaultModelPath() {
        // Default to Desktop for development - you can change this
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, "Desktop", "denial_model.xgb").toString();
    }

    private float[][] generateDummyPredictions(List<ServiceLineDenialPredictionPayload> payloads) {
        float[][] predictions = new float[payloads.size()][1];

        for (int i = 0; i < payloads.size(); i++) {
            // Generate simple dummy predictions - just use index for variety
            predictions[i][0] = 0.5f + (i % 5) * 0.1f; // 0.5, 0.6, 0.7, 0.8, 0.9, 0.5, ...
        }

        return predictions;
    }

    /**
     * Exception thrown when model operations fail.
     */
    public static class ModelException extends Exception {
        public ModelException(String message, Throwable cause) {
            super(message, cause);
        }

        public ModelException(String message) {
            super(message);
        }
    }
}
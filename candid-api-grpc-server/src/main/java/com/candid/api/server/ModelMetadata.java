package com.candid.api.server;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Model metadata containing feature order, categorical mappings, and other model information.
 * Loaded from JSON file exported during Python model training.
 */
public class ModelMetadata {

    @JsonProperty("version")
    private String version;

    @JsonProperty("model_file")
    private String modelFile;

    @JsonProperty("feature_order")
    private List<String> featureOrder;

    @JsonProperty("categorical_mappings")
    private Map<String, Map<String, Integer>> categoricalMappings;

    @JsonProperty("feature_count")
    private int featureCount;

    @JsonProperty("export_timestamp")
    private String exportTimestamp;

    @JsonProperty("feature_types")
    private Map<String, String> featureTypes;

    // Default constructor for Jackson
    public ModelMetadata() {}

    // Getters
    public String getVersion() { return version; }
    public String getModelFile() { return modelFile; }
    public List<String> getFeatureOrder() { return featureOrder; }
    public Map<String, Map<String, Integer>> getCategoricalMappings() { return categoricalMappings; }
    public int getFeatureCount() { return featureCount; }
    public String getExportTimestamp() { return exportTimestamp; }
    public Map<String, String> getFeatureTypes() { return featureTypes; }

    /**
     * Load metadata from JSON file.
     */
    public static ModelMetadata fromFile(String metadataPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(new File(metadataPath), ModelMetadata.class);
    }

    /**
     * Get encoded value for a categorical feature.
     * Returns null if feature is not categorical or value not found.
     */
    public Integer getEncodedValue(String featureName, String categoryValue) {
        Map<String, Integer> mapping = categoricalMappings.get(featureName);
        if (mapping == null) {
            return null;
        }

        // Try exact match first
        Integer encoded = mapping.get(categoryValue);
        if (encoded != null) {
            return encoded;
        }

        // Fall back to "other" if available
        return mapping.get("other");
    }

    /**
     * Check if a feature is categorical.
     */
    public boolean isCategorical(String featureName) {
        return "categorical".equals(featureTypes.get(featureName));
    }

    /**
     * Get the index of a feature in the feature order.
     */
    public int getFeatureIndex(String featureName) {
        return featureOrder.indexOf(featureName);
    }

    /**
     * Validate that this metadata is compatible with the expected model.
     */
    public void validate() throws IllegalStateException {
        if (featureOrder == null || featureOrder.isEmpty()) {
            throw new IllegalStateException("Feature order is missing or empty");
        }
        if (featureCount != featureOrder.size()) {
            throw new IllegalStateException("Feature count mismatch: expected " + featureCount + ", got " + featureOrder.size());
        }
        if (categoricalMappings == null) {
            throw new IllegalStateException("Categorical mappings are missing");
        }
    }

    @Override
    public String toString() {
        return String.format("ModelMetadata{version='%s', features=%d, exported=%s}",
            version, featureCount, exportTimestamp);
    }
}
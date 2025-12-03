package com.candid.api.inference.model;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Metadata describing features used in the model, including their names, types,
 * ordering, and categorical encodings. This class loads configuration from JSON
 * to ensure consistent feature ordering and categorical value mappings.
 */
public class FeatureMetadata {

    private final List<FeatureDefinition> features;
    private final Map<String, FeatureDefinition> featureMap;

    /**
     * Constructs FeatureMetadata from a list of feature definitions.
     */
    private FeatureMetadata(List<FeatureDefinition> features) {
        // Sort features by index to ensure correct ordering
        this.features = features.stream()
                .sorted(Comparator.comparingInt(FeatureDefinition::getIndex))
                .collect(Collectors.toList());

        // Create lookup map for quick access by name
        this.featureMap = this.features.stream()
                .collect(Collectors.toMap(FeatureDefinition::getName, f -> f));
    }

    /**
     * Loads feature metadata from a JSON resource on the classpath.
     *
     * @param resourcePath Path to the JSON file in resources (e.g., "models/feature_metadata.json")
     * @return FeatureMetadata instance
     * @throws IOException if the resource cannot be read
     */
    public static FeatureMetadata fromResource(String resourcePath) throws IOException {
        try (InputStream is = FeatureMetadata.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            Gson gson = new Gson();
            MetadataJson json = gson.fromJson(
                    new InputStreamReader(is, StandardCharsets.UTF_8),
                    MetadataJson.class
            );

            return new FeatureMetadata(json.features);
        }
    }

    /**
     * Gets all features in their correct order (sorted by index).
     */
    public List<FeatureDefinition> getOrderedFeatures() {
        return features;
    }

    /**
     * Gets a feature definition by name.
     *
     * @throws IllegalArgumentException if the feature name is not found
     */
    public FeatureDefinition getFeature(String name) {
        FeatureDefinition feature = featureMap.get(name);
        if (feature == null) {
            throw new IllegalArgumentException("Unknown feature: " + name);
        }
        return feature;
    }

    /**
     * Returns the total number of features.
     */
    public int getFeatureCount() {
        return features.size();
    }

    /**
     * Gets the index of a feature by name.
     */
    public int getFeatureIndex(String name) {
        return getFeature(name).getIndex();
    }

    /**
     * Defines a single feature with its properties.
     */
    public static class FeatureDefinition {
        private final String name;
        private final FeatureType type;
        private final int index;
        private final Map<String, Double> mapping;

        public FeatureDefinition(String name, FeatureType type, int index, Map<String, Double> mapping) {
            this.name = name;
            this.type = type;
            this.index = index;
            this.mapping = mapping;
        }

        public String getName() {
            return name;
        }

        public FeatureType getType() {
            return type;
        }

        public int getIndex() {
            return index;
        }

        /**
         * Gets the categorical mapping for this feature.
         * Returns null for numeric features.
         */
        public Map<String, Double> getCategoricalMapping() {
            return mapping;
        }
    }

    /**
     * Type of feature: numeric (continuous) or categorical (discrete).
     */
    public enum FeatureType {
        @SerializedName("NUMERIC")
        NUMERIC,

        @SerializedName("CATEGORICAL")
        CATEGORICAL
    }

    /**
     * Helper class for JSON deserialization.
     */
    private static class MetadataJson {
        List<FeatureDefinition> features;
    }
}

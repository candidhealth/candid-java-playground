package com.candid.api.inference.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an ordered vector of feature values ready for model inference.
 * Features are stored in the correct order as defined by the metadata.
 * Use the Builder to construct instances in a type-safe, name-based manner.
 */
public class FeatureVector {

    private final float[] values;
    private final FeatureMetadata metadata;

    private FeatureVector(float[] values, FeatureMetadata metadata) {
        this.values = values;
        this.metadata = metadata;
    }

    /**
     * Gets the raw feature values in their correct order.
     * This array can be used directly with ONNX Runtime for inference.
     */
    public float[] getValues() {
        return values;
    }

    /**
     * Builder for constructing FeatureVector instances using named features.
     */
    public static class Builder {
        private final Map<String, Object> rawFeatures = new HashMap<>();
        private final FeatureMetadata metadata;
        private final CategoricalEncoder encoder;

        public Builder(FeatureMetadata metadata) {
            this.metadata = metadata;
            this.encoder = new CategoricalEncoder(metadata);
        }

        /**
         * Sets a feature value by name.
         *
         * @param name Feature name (must match metadata)
         * @param value Feature value (Number for numeric, String for categorical)
         * @return this Builder for chaining
         */
        public Builder setFeature(String name, Object value) {
            rawFeatures.put(name, value);
            return this;
        }

        /**
         * Sets multiple features at once from a map.
         *
         * @param features Map of feature name to value
         * @return this Builder for chaining
         */
        public Builder setFeatures(Map<String, Object> features) {
            rawFeatures.putAll(features);
            return this;
        }

        /**
         * Builds the FeatureVector, encoding and ordering features according to metadata.
         *
         * @return FeatureVector with features in correct order
         * @throws IllegalArgumentException if required features are missing
         */
        public FeatureVector build() {
            float[] orderedValues = new float[metadata.getFeatureCount()];

            // Process features in the order defined by metadata
            for (FeatureMetadata.FeatureDefinition feature : metadata.getOrderedFeatures()) {
                String name = feature.getName();
                Object rawValue = rawFeatures.get(name);

                if (rawValue == null) {
                    throw new IllegalArgumentException("Missing required feature: " + name);
                }

                int index = feature.getIndex();

                // Handle numeric vs categorical features
                if (feature.getType() == FeatureMetadata.FeatureType.NUMERIC) {
                    if (!(rawValue instanceof Number)) {
                        throw new IllegalArgumentException(
                                "Feature '" + name + "' is numeric but got: " + rawValue.getClass().getSimpleName()
                        );
                    }
                    orderedValues[index] = ((Number) rawValue).floatValue();
                } else {
                    // Categorical feature - encode to numeric
                    if (!(rawValue instanceof String)) {
                        throw new IllegalArgumentException(
                                "Feature '" + name + "' is categorical but got: " + rawValue.getClass().getSimpleName()
                        );
                    }
                    orderedValues[index] = (float) encoder.encode(name, (String) rawValue);
                }
            }

            return new FeatureVector(orderedValues, metadata);
        }
    }
}

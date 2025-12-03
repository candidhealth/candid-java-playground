package com.candid.api.inference.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Encodes categorical string values to numeric codes using metadata-driven mappings.
 * Handles unknown values gracefully by falling back to an "UNKNOWN" category.
 */
public class CategoricalEncoder {

    private static final Logger logger = LoggerFactory.getLogger(CategoricalEncoder.class);
    private static final String UNKNOWN_KEY = "UNKNOWN";

    private final FeatureMetadata metadata;

    public CategoricalEncoder(FeatureMetadata metadata) {
        this.metadata = metadata;
    }

    /**
     * Encodes a categorical value to its numeric code.
     *
     * @param featureName Name of the feature
     * @param value Categorical value to encode
     * @return Numeric code for the value
     * @throws IllegalArgumentException if the feature is not categorical
     */
    public double encode(String featureName, String value) {
        FeatureMetadata.FeatureDefinition feature = metadata.getFeature(featureName);

        if (feature.getType() != FeatureMetadata.FeatureType.CATEGORICAL) {
            throw new IllegalArgumentException(
                    "Feature '" + featureName + "' is not categorical (type: " + feature.getType() + ")"
            );
        }

        Map<String, Double> mapping = feature.getCategoricalMapping();
        if (mapping == null) {
            throw new IllegalStateException(
                    "Categorical feature '" + featureName + "' has no mapping defined"
            );
        }

        // Try to find the exact value
        Double encoded = mapping.get(value);

        // If not found, try UNKNOWN fallback
        if (encoded == null) {
            logger.warn(
                    "Unknown categorical value '{}' for feature '{}', using {} fallback",
                    value,
                    featureName,
                    UNKNOWN_KEY
            );

            encoded = mapping.get(UNKNOWN_KEY);

            if (encoded == null) {
                throw new IllegalArgumentException(
                        "Unknown categorical value '" + value + "' for feature '" + featureName +
                        "' and no UNKNOWN fallback is defined"
                );
            }
        }

        return encoded;
    }
}

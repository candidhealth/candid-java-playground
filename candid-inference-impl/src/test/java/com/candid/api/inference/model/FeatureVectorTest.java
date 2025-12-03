package com.candid.api.inference.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

class FeatureVectorTest {

    private FeatureMetadata metadata;

    @BeforeEach
    void setUp() throws IOException {
        metadata = FeatureMetadata.fromResource("models/feature_metadata.json");
    }

    @Test
    void testLoadMetadata() {
        assertThat(metadata.getFeatureCount()).isEqualTo(5);

        assertThat(metadata.getFeature("claim_amount").getType())
                .isEqualTo(FeatureMetadata.FeatureType.NUMERIC);

        assertThat(metadata.getFeature("procedure_category").getType())
                .isEqualTo(FeatureMetadata.FeatureType.CATEGORICAL);
    }

    @Test
    void testCategoricalEncoding() {
        CategoricalEncoder encoder = new CategoricalEncoder(metadata);

        // Known categorical values
        assertThat(encoder.encode("procedure_category", "SURGERY")).isEqualTo(0.0);
        assertThat(encoder.encode("procedure_category", "DIAGNOSTIC")).isEqualTo(1.0);
        assertThat(encoder.encode("provider_state", "CA")).isEqualTo(0.0);
        assertThat(encoder.encode("provider_state", "NY")).isEqualTo(1.0);
    }

    @Test
    void testUnknownCategoricalFallback() {
        CategoricalEncoder encoder = new CategoricalEncoder(metadata);

        // Unknown value should fall back to UNKNOWN
        double encoded = encoder.encode("provider_state", "WA");  // Not in mapping
        assertThat(encoded).isEqualTo(4.0);  // UNKNOWN code
    }

    @Test
    void testFeatureOrdering() throws Exception {
        FeatureVector vector = new FeatureVector.Builder(metadata)
                .setFeature("claim_amount", 15000.0)
                .setFeature("patient_age", 45)
                .setFeature("procedure_category", "SURGERY")
                .setFeature("provider_state", "CA")
                .setFeature("prior_denials", 1)
                .build();

        float[] values = vector.getValues();

        // Verify features are in correct order (matching indices in metadata)
        assertThat(values).hasSize(5);
        assertThat(values[0]).isEqualTo(15000.0f);  // claim_amount (index 0)
        assertThat(values[1]).isEqualTo(45.0f);     // patient_age (index 1)
        assertThat(values[2]).isEqualTo(0.0f);      // procedure_category=SURGERY (index 2)
        assertThat(values[3]).isEqualTo(0.0f);      // provider_state=CA (index 3)
        assertThat(values[4]).isEqualTo(1.0f);      // prior_denials (index 4)
    }

    @Test
    void testMissingFeature() {
        assertThatThrownBy(() ->
                new FeatureVector.Builder(metadata)
                        .setFeature("claim_amount", 10000.0)
                        // Missing other required features
                        .build()
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing required feature");
    }

    @Test
    void testInvalidFeatureType() {
        assertThatThrownBy(() ->
                new FeatureVector.Builder(metadata)
                        .setFeature("claim_amount", "not_a_number")  // String instead of number
                        .setFeature("patient_age", 30)
                        .setFeature("procedure_category", "SURGERY")
                        .setFeature("provider_state", "CA")
                        .setFeature("prior_denials", 0)
                        .build()
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is numeric but got");
    }
}

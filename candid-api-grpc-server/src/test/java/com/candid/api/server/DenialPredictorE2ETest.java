package com.candid.api.server;

import com.candid.api.DenialPredictionPayload;
import com.candid.api.DenialPredictionResponse;
import com.candid.api.client.DenialPredictorClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class DenialPredictorE2ETest {
    private static final int TEST_PORT = 9091;
    private DenialPredictorServer server;
    private DenialPredictorClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new DenialPredictorServer(TEST_PORT);
        server.start();
        client = new DenialPredictorClient("localhost", TEST_PORT);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (client != null) {
            client.shutdown();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void testPredictDenial_returnsHardcodedResponse() {
        // Given
        DenialPredictionPayload request = DenialPredictionPayload.newBuilder()
                .setPatientId("P12345")
                .setClaimAmount(15000.00)
                .setProcedureCode("CPT-99999")
                .build();

        // When
        DenialPredictionResponse response = client.predictDenial(request);

        // Then
        assertNotNull(response);
        assertTrue(response.getWillBeDenied(), "Expected denial prediction to be true");
        assertEquals(0.85, response.getConfidenceScore(), 0.001, "Expected confidence score of 0.85");
        assertEquals("High-cost procedure exceeds typical threshold", response.getReason());
    }
}

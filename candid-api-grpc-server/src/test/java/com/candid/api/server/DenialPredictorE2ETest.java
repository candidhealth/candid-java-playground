package com.candid.api.server;

import com.candid.api.DenialPredictionPayload;
import com.candid.api.DenialPredictionResponse;
import com.candid.api.ServiceLineDenialPredictionPayload;
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
        ServiceLineDenialPredictionPayload serviceLinePayload = ServiceLineDenialPredictionPayload.newBuilder()
                .setServiceLineId("test-service-line-123")
                .setHasPriorAuthorizationNumber(false)
                .setChargePerUnit(1000.0f)
                .setProcedureCode("99213")
                .setPayerId("test-payer")
                .setBillingProviderState("CA")
                .build();

        DenialPredictionPayload payload = DenialPredictionPayload.newBuilder()
                .addItems(serviceLinePayload)
                .build();

        // When
        DenialPredictionResponse response = client.predictDenial(payload);

        // Then
        assertNotNull(response);
    }
}

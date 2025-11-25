package com.candid.api.server;

import com.candid.api.DenialPredictionPayload;
import com.candid.api.DenialPredictionResponse;
import com.candid.api.DenialPredictorGrpc;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DenialPredictorService extends DenialPredictorGrpc.DenialPredictorImplBase {
    private static final Logger logger = LoggerFactory.getLogger(DenialPredictorService.class);

    @Override
    public void predictDenial(DenialPredictionPayload request, StreamObserver<DenialPredictionResponse> responseObserver) {
        logger.info("Received prediction request for patient: {}, claim amount: {}, procedure: {}",
                request.getPatientId(), request.getClaimAmount(), request.getProcedureCode());

        // Hardcoded response for testing
        DenialPredictionResponse response = DenialPredictionResponse.newBuilder()
                .setWillBeDenied(true)
                .setConfidenceScore(0.85)
                .setReason("High-cost procedure exceeds typical threshold")
                .build();

        logger.info("Returning prediction: denied={}, confidence={}",
                response.getWillBeDenied(), response.getConfidenceScore());

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}

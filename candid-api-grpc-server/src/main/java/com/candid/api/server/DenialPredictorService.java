package com.candid.api.server;

import com.candid.api.DenialPredictionPayload;
import com.candid.api.DenialPredictionResponse;
import com.candid.api.DenialPredictorGrpc;
import com.candid.api.ServiceLinePredictionResponse;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DenialPredictorService extends DenialPredictorGrpc.DenialPredictorImplBase {
    private static final Logger logger = LoggerFactory.getLogger(DenialPredictorService.class);

    @Override
    public void predictDenial(DenialPredictionPayload request, StreamObserver<DenialPredictionResponse> responseObserver) {
        logger.info("Received denial prediction request with {} service lines", request.getItemsCount());

        // Fake response
        ServiceLinePredictionResponse examplePrediction = ServiceLinePredictionResponse.newBuilder()
                .setRawScore(1.25)
                .setProbability(0.78)
                .setRawReason("HIGH_COST NO_PRIOR_AUTH")
                .setHumanReadableReason("High-cost procedure requires prior authorization")
                .build();

        DenialPredictionResponse.Builder responseBuilder = DenialPredictionResponse.newBuilder();

        // Add prediction for each service line ID in the request
        for (var item : request.getItemsList()) {
            responseBuilder.putResults(item.getServiceLineId(), examplePrediction);
        }

        DenialPredictionResponse response = responseBuilder.build();
        logger.info("Returning predictions for {} service lines", response.getResultsCount());

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}

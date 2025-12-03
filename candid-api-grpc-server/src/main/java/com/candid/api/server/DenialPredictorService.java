package com.candid.api.server;

import com.candid.api.DenialPredictionPayload;
import com.candid.api.DenialPredictionResponse;
import com.candid.api.DenialPredictorGrpc;
import com.candid.api.ServiceLinePredictionResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class DenialPredictorService extends DenialPredictorGrpc.DenialPredictorImplBase {
    private static final Logger logger = LoggerFactory.getLogger(DenialPredictorService.class);
    private final DenialPredictionModelService modelService;

    public DenialPredictorService() {
        this.modelService = new DenialPredictionModelService();
    }

    public DenialPredictorService(DenialPredictionModelService modelService) {
        this.modelService = modelService;
    }

    @Override
    public void predictDenial(DenialPredictionPayload request, StreamObserver<DenialPredictionResponse> responseObserver) {
        logger.info("Received denial prediction request with {} service lines", request.getItemsCount());

        try {
            // Use batch prediction for better performance
            Map<String, ServiceLinePredictionResponse> batchPredictions =
                modelService.predictBatch(request.getItemsList());

            DenialPredictionResponse.Builder responseBuilder = DenialPredictionResponse.newBuilder();

            // Add all predictions to response
            for (var entry : batchPredictions.entrySet()) {
                responseBuilder.putResults(entry.getKey(), entry.getValue());
            }

            DenialPredictionResponse response = responseBuilder.build();
            logger.info("Returning batch predictions for {} service lines", response.getResultsCount());

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (DenialPredictionModelService.ModelException e) {
            logger.error("Model prediction error", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Model prediction failed: " + e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            logger.error("Unexpected error during prediction", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Prediction service error: " + e.getMessage())
                    .asRuntimeException());
        }
    }
}

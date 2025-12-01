package com.candid.api.client;

import com.candid.api.DenialPredictionPayload;
import com.candid.api.DenialPredictionResponse;
import com.candid.api.DenialPredictorGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class DenialPredictorClient {
    private static final Logger logger = LoggerFactory.getLogger(DenialPredictorClient.class);

    private final ManagedChannel channel;
    private final DenialPredictorGrpc.DenialPredictorBlockingStub blockingStub;

    public DenialPredictorClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.blockingStub = DenialPredictorGrpc.newBlockingStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public DenialPredictionResponse predictDenial(DenialPredictionPayload payload) {
        logger.info("Sending prediction request");
        try {
            DenialPredictionResponse response = blockingStub.predictDenial(payload);
            logger.info("Received response");
            return response;
        } catch (StatusRuntimeException e) {
            logger.error("RPC failed: {}", e.getStatus());
            throw e;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 9090;

        DenialPredictorClient client = new DenialPredictorClient(host, port);
        try {
            // TODO: Build a proper request payload
            DenialPredictionPayload request = DenialPredictionPayload.newBuilder()
                    .build();

            DenialPredictionResponse response = client.predictDenial(request);
            logger.info("Prediction completed successfully");
        } finally {
            client.shutdown();
        }
    }
}

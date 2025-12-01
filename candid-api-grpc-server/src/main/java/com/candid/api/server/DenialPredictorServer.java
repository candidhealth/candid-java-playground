package com.candid.api.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class DenialPredictorServer {
    private static final Logger logger = LoggerFactory.getLogger(DenialPredictorServer.class);
    private static final int DEFAULT_PORT = 9090;

    private final Server server;

    public DenialPredictorServer(int port) {
        this.server = ServerBuilder.forPort(port)
                .addService(new DenialPredictorService())
                .build();
    }

    public void start() throws IOException {
        server.start();
        logger.info("Server started, listening on port {}", server.getPort());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down gRPC server");
            try {
                DenialPredictorServer.this.stop();
            } catch (InterruptedException e) {
                logger.error("Error during shutdown", e);
            }
        }));
    }

    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        DenialPredictorServer server = new DenialPredictorServer(port);
        server.start();
        server.blockUntilShutdown();
    }
}

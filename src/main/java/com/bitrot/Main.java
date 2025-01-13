package com.bitrot;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;

@SuppressWarnings("CallToPrintStackTrace")
public class Main {
    public static void main(final String[] args) throws IOException {
        final Config config = Config.readConfig();

        final SkipUtil skipUtil = new SkipUtil();
        final MongoManager mongoManager = new MongoManager(config.getMongoConnectionString());

        // Clean up the database before we start
        skipUtil.cleanDatabase();

        final FileProcessor processor = new FileProcessor(skipUtil, mongoManager);

        FileLoggerUtil.log("Mutable paths: " + config.getMutablePaths());
        FileLoggerUtil.log("Immutable paths: " + config.getImmutablePaths());
        FileLoggerUtil.log("--------------------------");

        // Go through the mutable paths first
        for (final String mutablePath : config.getMutablePaths()) {
            processor.processFiles(Paths.get(mutablePath), false);
        }

        // Then the immutable paths
        for (final String immutablePath : config.getImmutablePaths()) {
            processor.processFiles(Paths.get(immutablePath), true);
        }

        // Log the totals now that all paths are processed
        processor.logTotals();

        if (processor.noFailures() && !FileLoggerUtil.encounteredException()) {
            // If there are no failures or exceptions, call the health check
            callHealthCheck(config.getHealthCheckUrl());
        } else {
            // Otherwise exit with a non-zero status
            System.exit(1);
        }
    }

    private static void callHealthCheck(final String url) {
        try (final HttpClient client = HttpClient.newHttpClient()) {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            FileLoggerUtil.log("Health Check Response Code: " + response.statusCode() + " Body: " + response.body());
        } catch (final IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}

package com.bitrot;

import com.bitrot.data.Config;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.DeleteResult;
import org.bson.Document;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static com.bitrot.data.Constants.MONGO_COLLECTION_NAME;
import static com.bitrot.data.Constants.MONGO_DB_NAME;

public class MongoRecordDeleter implements AutoCloseable {
    private final MongoClient client;
    private final MongoCollection<Document> collection;

    public MongoRecordDeleter(final String connectionString) {
        if (connectionString == null || connectionString.trim().isEmpty()) {
            throw new IllegalArgumentException("Connection string cannot be null or empty");
        }
        this.client = MongoClients.create(connectionString);
        try {
            this.collection = client.getDatabase(MONGO_DB_NAME).getCollection(MONGO_COLLECTION_NAME);
        } catch (Exception e) {
            this.client.close();
            throw new RuntimeException("Failed to initialize MongoDB collection", e);
        }
    }

    public boolean deleteRecordByFileId(final String fileId) {
        if (fileId == null || fileId.trim().isEmpty()) {
            throw new IllegalArgumentException("File ID cannot be null or empty");
        }

        final Document query = new Document(MongoManager.FILE_ID_KEY, fileId.trim());
        final DeleteResult result = collection.deleteOne(query);

        System.out.println("Attempted to delete record with file_id: " + fileId);
        System.out.println("Deleted count: " + result.getDeletedCount());

        return result.getDeletedCount() > 0;
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java MongoRecordDeleter <ids_file>");
            System.err.println("Example: java MongoRecordDeleter ids.txt");
            System.exit(1);
        }

        final String idsFileName = args[0];
        final File idsFile = new File(idsFileName);

        if (!idsFile.exists()) {
            System.err.println("Error: IDs file not found: " + idsFileName);
            System.exit(1);
        }

        try {
            // Read configuration
            final Config config = Config.readConfig();
            final String connectionString = config.getMongoConnectionString();

            if (connectionString == null || connectionString.trim().isEmpty()) {
                System.err.println("Error: MongoDB connection string not found in config file");
                System.exit(1);
            }

            // Initialize deleter and process with try-with-resources
            try (MongoRecordDeleter deleter = new MongoRecordDeleter(connectionString)) {
                // Read IDs and delete records
                int totalProcessed = 0;
                int successfullyDeleted = 0;
                int notFound = 0;

                try (BufferedReader reader = Files.newBufferedReader(idsFile.toPath(), StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String fileId = line.trim();

                        // Skip empty lines
                        if (fileId.isEmpty()) {
                            continue;
                        }

                        totalProcessed++;

                        if (deleter.deleteRecordByFileId(fileId)) {
                            successfullyDeleted++;
                        } else {
                            notFound++;
                        }
                    }
                }

                // Print summary
                System.out.println("\n=== Deletion Summary ===");
                System.out.println("Total IDs processed: " + totalProcessed);
                System.out.println("Successfully deleted: " + successfullyDeleted);
                System.out.println("Not found in database: " + notFound);
            }

        } catch (IOException e) {
            System.err.println("Error reading configuration or IDs file: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

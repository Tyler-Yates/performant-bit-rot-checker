package com.bitrot;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static com.bitrot.Constants.*;

public class MongoManager {
    private static final String MONGO_ID_KEY = "_id";
    private static final String FILE_ID_KEY = "file_id";
    private static final String MODIFIED_TIME_KEY = "mtime";
    private static final String SIZE_KEY = "size";
    private static final String CHECKSUM_KEY = "checksum";
    private static final String LAST_ACCESSED_KEY = "last_accessed";

    private final MongoCollection<Document> collection;

    @SuppressWarnings("resource")
    public MongoManager(final String connection_string) {
        final MongoClient client = MongoClients.create(connection_string);
        collection = client.getDatabase(MONGO_DB_NAME).getCollection(MONGO_COLLECTION_NAME);

        // Create a compound index with FILE_ID_KEY and MODIFIED_TIME_KEY
        collection.createIndex(Indexes.ascending(FILE_ID_KEY, MODIFIED_TIME_KEY),
                new IndexOptions().unique(true));

        // Create an index for LAST_ACCESSED_KEY with expiration
        collection.createIndex(Indexes.ascending(LAST_ACCESSED_KEY),
                new IndexOptions().expireAfter(SECONDS_IN_A_YEAR, TimeUnit.SECONDS));
    }

    @Nullable
    private Document findDocumentDifferentMTime(final FileRecord fileRecord, final boolean isImmutable) {
        final Document query = new Document(FILE_ID_KEY, fileRecord.getFileId());
        final Document differentMTimeDocument = collection.find(query).first();

        if (differentMTimeDocument == null) {
            // We have never seen this file before
            return null;
        } else {
            // We have seen this file before, but the modified timestamp is different. This means we need
            // to check the immutability of this file.
            if (isImmutable && !fileRecord.getFilePath().endsWith(".par2")) {
                // We may need to modify par2 files if we add new files to a folder at a later time, so exclude them
                // from the immutability check even if they are in an immutable folder.
                System.out.println("Immutable file has been modified: " + fileRecord.getAbsoluteFilePath());
                return differentMTimeDocument;
            } else {
                // The file is mutable, so we should create a new record.
                System.out.println("File has been seen before but has been modified: " + fileRecord.getAbsoluteFilePath());
                return null;
            }
        }
    }

    /**
     * Updates the last accessed time for the given document in the database.
     * Also updates the last accessed time of the passed in Document in-place.
     *
     * @param databaseDocument the given document
     */
    public void updateLastAccessed(final Document databaseDocument) {
        // Update last accessed time to the current moment
        final Instant currentDatetime = Instant.now();
        final Document update = new Document("$set", new Document(LAST_ACCESSED_KEY, currentDatetime));
        // We do NOT want to upsert because the document should already exist
        final UpdateOptions options = new UpdateOptions().upsert(false);

        final UpdateResult updateResult = collection.updateOne(
                new Document(MONGO_ID_KEY, databaseDocument.get(MONGO_ID_KEY)),
                update,
                options
        );

        // Don't look at modifiedCount as MongoDB may choose to not update the document if the timestamps
        // are too close together.
        if (updateResult.getMatchedCount() != 1) {
            throw new IllegalArgumentException("Could not update last accessed time for " + databaseDocument.get(FILE_ID_KEY));
        }

        // Update the given document
        databaseDocument.put(LAST_ACCESSED_KEY, currentDatetime);
    }

    @Nullable
    private Document findDocument(final FileRecord fileRecord, final boolean isImmutable) throws IOException {
        // Start off with trying to find an exact match (both ID and mtime)
        final Document query = new Document(FILE_ID_KEY, fileRecord.getFileId())
                .append(MODIFIED_TIME_KEY, fileRecord.getMTime());

        final Document exactMatch = collection.find(query).first();

        if (exactMatch == null) {
            // No exact matches, so see if there are matches with different mtimes.
            return findDocumentDifferentMTime(fileRecord, isImmutable);
        } else {
            // If the document exists, update its last accessed time so that it is not cleaned up.
            updateLastAccessed(exactMatch);
            return exactMatch;
        }
    }

    public void processFileRecord(final FileRecord fileRecord, final boolean isImmutable) throws IOException {
        final Document databaseDocument = findDocument(fileRecord, isImmutable);
    }
}

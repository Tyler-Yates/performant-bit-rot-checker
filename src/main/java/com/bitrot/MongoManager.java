package com.bitrot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static com.bitrot.Constants.*;
import static com.bitrot.SkipUtil.fileIsTooNewToSaveToDatabase;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

public class MongoManager {
    private static final ObjectMapper objectMapper;

    static final String MONGO_ID_KEY = "_id";
    static final String FILE_ID_KEY = "file_id";
    static final String MODIFIED_TIME_SECONDS_KEY = "mtime_s";
    static final String MODIFIED_TIME_NANOS_KEY = "mtime_ns";
    static final String SIZE_KEY = "size";
    static final String CHECKSUM_KEY = "checksum";
    static final String LAST_ACCESSED_KEY = "last_accessed";

    private final MongoCollection<Document> collection;

    static {
        // Be able to deserialize ObjectID
        final SimpleModule module = new SimpleModule();
        module.addDeserializer(ObjectId.class, new ObjectIdDeserializer());
        objectMapper = JsonMapper.builder().findAndAddModules().addModule(module).build();
        // Ignore unknown properties
        objectMapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @SuppressWarnings("resource")
    public MongoManager(final String connection_string) {
        final MongoClient client = MongoClients.create(connection_string);
        collection = client.getDatabase(MONGO_DB_NAME).getCollection(MONGO_COLLECTION_NAME);

        ensureIndexes();
    }

    MongoManager(final MongoClient client) {
        collection = client.getDatabase(MONGO_DB_NAME).getCollection(MONGO_COLLECTION_NAME);

        ensureIndexes();
    }

    private void ensureIndexes() {
        // Create a unique compound index with the file ID and two modified time fields.
        // Order of the fields matters! That's why we do the most commonly queried fields first.
        // There should never be two documents with the same values for these fields.
        collection.createIndex(Indexes.ascending(FILE_ID_KEY, MODIFIED_TIME_SECONDS_KEY, MODIFIED_TIME_NANOS_KEY),
                new IndexOptions().unique(true));

        // Create an index for LAST_ACCESSED_KEY with expiration
        collection.createIndex(Indexes.ascending(LAST_ACCESSED_KEY),
                new IndexOptions().expireAfter(SECONDS_IN_A_YEAR, TimeUnit.SECONDS));
    }

    @Nullable
    private DatabaseDocument findOne(final Document query) {
        final Document document = collection.find(query).first();
        if (document == null) {
            return null;
        }

        try {
            return objectMapper.readValue(document.toJson(), DatabaseDocument.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    private DatabaseDocument findDocumentDifferentMTime(final FileRecord fileRecord, final boolean isImmutable) {
        final Document query = new Document(FILE_ID_KEY, fileRecord.getFileId());
        final DatabaseDocument differentMTimeDocument = findOne(query);

        if (differentMTimeDocument == null) {
            // We have never seen this file before
            return null;
        } else {
            // We have seen this file before, but the modified timestamp is different. This means we need
            // to check the immutability of this file.
            if (isImmutable && !fileRecord.getFilePath().endsWith(".par2")) {
                // We may need to modify par2 files if we add new files to a folder at a later time, so exclude them
                // from the immutability check even if they are in an immutable folder.
                System.out.println("Immutable file has been modified: " + fileRecord.getLogIdentifier());
                return differentMTimeDocument;
            } else {
                // The file is mutable, so we should create a new record.
                System.out.println("File has been seen before but has been modified: " + fileRecord.getLogIdentifier());
                return null;
            }
        }
    }

    /**
     * Updates the last accessed time for the given document in the database.
     *
     * @param databaseDocument the given document
     */
    public void updateLastAccessed(final DatabaseDocument databaseDocument) {
        // Update last accessed time to the current moment
        final Instant currentDatetime = Instant.now();
        final Document update = new Document("$set", new Document(LAST_ACCESSED_KEY, currentDatetime));
        // We do NOT want to upsert because the document should already exist
        final UpdateOptions options = new UpdateOptions().upsert(false);

        final UpdateResult updateResult = collection.updateOne(
                new Document(MONGO_ID_KEY, databaseDocument.objectId()),
                update,
                options
        );

        // Don't look at modifiedCount as MongoDB may choose to not update the document if the timestamps
        // are too close together.
        if (updateResult.getMatchedCount() != 1) {
            throw new IllegalArgumentException("Could not update last accessed time for " + databaseDocument.fileId());
        }
    }

    @Nullable
    private DatabaseDocument findDocument(final FileRecord fileRecord, final boolean isImmutable) throws IOException {
        // Start off with trying to find an exact match (both ID and mtime)
        final Document query = new Document(FILE_ID_KEY, fileRecord.getFileId())
                .append(MODIFIED_TIME_SECONDS_KEY, fileRecord.getMTimeSeconds());

        final DatabaseDocument exactMatch = findOne(query);

        if (exactMatch == null) {
            // No exact matches, so see if there are matches with different mtimes.
            return findDocumentDifferentMTime(fileRecord, isImmutable);
        } else {
            // If the document exists, update its last accessed time so that it is not cleaned up.
            updateLastAccessed(exactMatch);
            return exactMatch;
        }
    }

    private FileResult saveNewRecord(final FileRecord fileRecord) throws IOException {
        // This file record is not in the database. Time to create a new document.
        // We still do an update operation because another computer may be creating this document at the same time
        // as us.
        final Document filter = new Document()
                .append(FILE_ID_KEY, fileRecord.getFileId())
                .append(MODIFIED_TIME_SECONDS_KEY, fileRecord.getMTimeSeconds())
                .append(MODIFIED_TIME_NANOS_KEY, fileRecord.getMTimeNanos());

        final Document data = new Document()
                .append(FILE_ID_KEY, fileRecord.getFileId())
                .append(MODIFIED_TIME_SECONDS_KEY, fileRecord.getMTimeSeconds())
                .append(MODIFIED_TIME_NANOS_KEY, fileRecord.getMTimeNanos())
                .append(SIZE_KEY, fileRecord.getSize())
                .append(CHECKSUM_KEY, fileRecord.getChecksum())
                .append(LAST_ACCESSED_KEY, Instant.now());

        final Document update = new Document("$set", data);

        collection.updateOne(filter, update, new UpdateOptions().upsert(true));

        return new FileResult(Result.PASS, "New file record saved to database: " + data);
    }

    public FileResult processFileRecord(final FileRecord fileRecord, final boolean isImmutable) throws IOException {
        final DatabaseDocument databaseDocument = findDocument(fileRecord, isImmutable);

        if (databaseDocument == null) {
            // We need to be confident that a new immutable file is completely done being modified.
            // Newly created files are riskier to make this assumption since they may still be being written to.
            // For example, creating par2 files may take quite a long time, and we don't want to save its
            // initial checksum into the database only for it to change as the creation process completes.
            if (isImmutable && fileIsTooNewToSaveToDatabase(fileRecord)) {
                return new FileResult(
                        Result.SKIP,
                        "Immutable file " + fileRecord.getLogIdentifier() + " skipped because it was created recently"
                );
            }

            // Time to create the new record.
            return saveNewRecord(fileRecord);
        } else {
            // We have seen this record before so now check for any bit rot
            if (!fileRecord.getFileId().equals(databaseDocument.fileId())) {
                throw new IllegalStateException("Fatal error! File ID mismatch for record " + fileRecord.getLogIdentifier() +
                        ": Local=" + fileRecord.getFileId() +
                        " but Database=" + databaseDocument.fileId());
            }

            if (fileRecord.getMTimeSeconds() != databaseDocument.mTimeSeconds()) {
                return new FileResult(Result.FAIL, "File modified time mismatch for record " + fileRecord.getLogIdentifier() +
                        ": Local=" + getMTimeString(fileRecord.getMTimeSeconds(), fileRecord.getMTimeNanos()) +
                        " but Database=" + getMTimeString(databaseDocument.mTimeSeconds(), databaseDocument.mTimeNanos()));
            }

            if (fileRecord.getSize() != databaseDocument.size()) {
                return new FileResult(Result.FAIL, "File size mismatch for record " + fileRecord.getLogIdentifier() +
                        ": Local=" + fileRecord.getSize() +
                        " but Database=" + databaseDocument.size());
            }

            if (fileRecord.getChecksum() != databaseDocument.checksum()) {
                return new FileResult(Result.FAIL, "File CRC mismatch for record " + fileRecord.getLogIdentifier() +
                        ": Local=" + fileRecord.getChecksum() +
                        " but Database=" + databaseDocument.checksum());
            }

            // The old Python-era documents are missing the mtime_ns field. Add that field if necessary.
            if (databaseDocument.mTimeNanos() == null) {
                addMtimeNanoFieldIfNecessary(databaseDocument, fileRecord);
            }

            // If we have reached this point, we passed verification!
            return new FileResult(Result.PASS, "File " + fileRecord.getLogIdentifier() + " passed verification");
        }
    }

    private void addMtimeNanoFieldIfNecessary(final DatabaseDocument databaseDocument, final FileRecord fileRecord) throws IOException {
        final Document filter = new Document(MONGO_ID_KEY, databaseDocument.objectId());
        final Document update = new Document("$set", new Document(MODIFIED_TIME_NANOS_KEY, fileRecord.getMTimeNanos()));

        final UpdateResult updateResult = collection.updateOne(filter, update);

        System.out.println("Found document missing mtime_ns field. Modified document count: " + updateResult.getModifiedCount());
    }

    private static String getMTimeString(final long mtimeSeconds, final int mtimeNanos) {
        return "(seconds=" + mtimeSeconds + ", nanos=" + mtimeNanos + ")";
    }
}

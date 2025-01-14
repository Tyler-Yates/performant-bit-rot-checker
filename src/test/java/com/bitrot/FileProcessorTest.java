package com.bitrot;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.embed.process.io.ProcessOutput;
import de.flapdoodle.reverse.Transition;
import de.flapdoodle.reverse.TransitionWalker;
import de.flapdoodle.reverse.transitions.Start;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.bitrot.Constants.MONGO_COLLECTION_NAME;
import static com.bitrot.Constants.MONGO_DB_NAME;
import static com.bitrot.MongoManager.*;
import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.*;

public class FileProcessorTest {
    private static final Mongod mongod = new Mongod() {
        @Override
        public Transition<ProcessOutput> processOutput() {
            return Start.to(ProcessOutput.class)
                    .initializedWith(ProcessOutput.silent())
                    .withTransitionLabel("no output");
        }
    };

    private TransitionWalker.ReachedState<RunningMongodProcess> runningMongo;
    private MongoClient mongoClient;
    private FileProcessor fileProcessor;

    @BeforeEach
    public void setup() throws SQLException {
        final Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        final SkipUtil skipUtil = new SkipUtil(connection);

        runningMongo = mongod.start(Version.V8_0_3);
        mongoClient = MongoClients.create("mongodb://" + runningMongo.current().getServerAddress());
        final MongoManager mongoManager = new MongoManager(mongoClient);

        fileProcessor = new FileProcessor(skipUtil, mongoManager);
    }

    @AfterEach
    public void tearDown() {
        mongoClient.close();
        runningMongo.close();
    }

    @Test
    public void testProcessFilesImmutable(@TempDir final Path tempDir) throws IOException, InterruptedException {
        final boolean isImmutable = true;

        // Ensure that the collection is empty at the start
        final MongoCollection<Document> collection = mongoClient.getDatabase(MONGO_DB_NAME).getCollection(MONGO_COLLECTION_NAME);
        assertEquals(0, collection.countDocuments());

        // Create a new directory and file
        final String dirName = "dir1";
        final Path dirPath = tempDir.resolve(dirName);
        Files.createDirectories(dirPath);
        final String fileName = "mutable-file.txt";
        final Path tempFile = dirPath.resolve(fileName);
        Files.writeString(tempFile, "abc", StandardOpenOption.CREATE);

        /*
        SECTION
         */
        // We should see no documents in the collection because the immutable file is too new and should be ignored
        final Map<Result, Integer> firstResults = fileProcessor.processFiles(tempDir, isImmutable);
        assertEquals(Map.of(Result.SKIP, 1), firstResults);
        assertEquals(0, collection.countDocuments());

        /*
        SECTION
         */
        // Modify the file to have an older creation date so we can process it
        final FileTime newCreationTime = FileTime.from(Instant.parse("2000-01-01T10:00:00Z"));
        Files.setAttribute(tempFile, "basic:creationTime", newCreationTime);

        final Map<Result, Integer> secondResults = fileProcessor.processFiles(tempDir, isImmutable);
        assertEquals(Map.of(Result.PASS, 1), secondResults);
        assertEquals(1, collection.countDocuments());

        // Assert on the document itself
        final Document firstDocument = collection.find().first();
        assertNotNull(firstDocument);
        final Instant firstModifiedTime = Files.getLastModifiedTime(tempFile).toInstant();
        final Date firstLastAccessed = (Date) firstDocument.get(LAST_ACCESSED_KEY);
        final Document expectedFirstDocument = new Document()
                .append(MONGO_ID_KEY, firstDocument.get(MONGO_ID_KEY))
                .append(FILE_ID_KEY, "cf3ab19e0eb597ee451ab1b172f4c43897ced2428d1e231938c9aaed9f93c16a") // SHA-256 of '\dir1\mutable-file.txt'
                .append(MODIFIED_TIME_SECONDS_KEY, firstModifiedTime.getEpochSecond())
                .append(MODIFIED_TIME_NANOS_KEY, firstModifiedTime.getNano())
                .append(SIZE_KEY, 3L)
                .append(CHECKSUM_KEY, 891568578L) // CRC32 of 'abc'
                .append(LAST_ACCESSED_KEY, firstLastAccessed);
        assertEquals(expectedFirstDocument, firstDocument);
        // Check that the last_accessed field is very recent
        assertTrue((System.currentTimeMillis() - firstLastAccessed.getTime()) / 1000 < 60, "The last_accessed field is too old");

        /*
        SECTION
         */
        // Trying to process again should yield a single SKIP because we processed the file very recently
        final Map<Result, Integer> thirdResults = fileProcessor.processFiles(tempDir, isImmutable);
        assertEquals(Map.of(Result.SKIP, 1), thirdResults);
        // The first document should stay in Mongo unmodified
        assertEquals(1, collection.countDocuments());
        assertEquals(expectedFirstDocument, collection.find().first());

        /*
        SECTION
         */
        // Sleep a bit because the recency util doesn't have nanosecond precision
        final int sleepSeconds = 2;
        System.out.println("Sleeping for " + sleepSeconds + " seconds...");
        sleep(Duration.ofSeconds(sleepSeconds));
        // Let's modify the file to force a re-check
        Files.writeString(tempFile, "xyz", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // The file is immutable so we should see a single failure with no changes in the database
        final Map<Result, Integer> fourthResults = fileProcessor.processFiles(tempDir, isImmutable);
        assertEquals(Map.of(Result.FAIL, 1), fourthResults);
        // There should now be two documents: the first one and the new one for the modification
        assertEquals(1, collection.countDocuments());
        assertEquals(expectedFirstDocument, collection.find().first());
    }

    @Test
    public void testProcessFilesMutable(@TempDir final Path tempDir) throws IOException, InterruptedException {
        final boolean isImmutable = true;

        // Ensure that the collection is empty at the start
        final MongoCollection<Document> collection = mongoClient.getDatabase(MONGO_DB_NAME).getCollection(MONGO_COLLECTION_NAME);
        assertEquals(0, collection.countDocuments());

        // Create a new file in the temp directory
        final String fileName = "specific-test-file.txt";
        final Path tempFile = tempDir.resolve(fileName);
        Files.writeString(tempFile, "123", StandardOpenOption.CREATE);

        /*
        SECTION
         */
        // First processing
        fileProcessor.processFiles(tempDir, isImmutable);
        assertEquals(1, collection.countDocuments());

        // Assert on the document itself
        final Document document = collection.find().first();
        assertNotNull(document);
        final Instant modifiedTime = Files.getLastModifiedTime(tempFile).toInstant();
        final Document expectedDocument = new Document()
                .append(MONGO_ID_KEY, document.get(MONGO_ID_KEY))
                .append(FILE_ID_KEY, "c7f43a78dbc983d05e2ac88098c83f0901847bb75e4719e9ebda55fa8e206205")
                .append(MODIFIED_TIME_SECONDS_KEY, modifiedTime.getEpochSecond())
                .append(MODIFIED_TIME_NANOS_KEY, modifiedTime.getNano())
                .append(SIZE_KEY, 3L)
                .append(CHECKSUM_KEY, 2286445522L)
                .append(LAST_ACCESSED_KEY, document.get(LAST_ACCESSED_KEY));
        assertEquals(expectedDocument, document);

        /*
        SECTION
         */
        // Modify the file to have an older creation date so we can process it
        final FileTime newCreationTime = FileTime.from(Instant.parse("2000-01-01T10:00:00Z"));
        Files.setAttribute(tempFile, "basic:creationTime", newCreationTime);

        final Map<Result, Integer> secondResults = fileProcessor.processFiles(tempDir, isImmutable);
        assertEquals(Map.of(Result.PASS, 1), secondResults);
        assertEquals(1, collection.countDocuments());

        // Assert on the document itself
        final Document firstDocument = collection.find().first();
        assertNotNull(firstDocument);
        final Instant firstModifiedTime = Files.getLastModifiedTime(tempFile).toInstant();
        final Date firstLastAccessed = (Date) firstDocument.get(LAST_ACCESSED_KEY);
        final Document expectedFirstDocument = new Document()
                .append(MONGO_ID_KEY, firstDocument.get(MONGO_ID_KEY))
                .append(FILE_ID_KEY, "cf3ab19e0eb597ee451ab1b172f4c43897ced2428d1e231938c9aaed9f93c16a") // SHA-256 of '\dir1\mutable-file.txt'
                .append(MODIFIED_TIME_SECONDS_KEY, firstModifiedTime.getEpochSecond())
                .append(MODIFIED_TIME_NANOS_KEY, firstModifiedTime.getNano())
                .append(SIZE_KEY, 3L)
                .append(CHECKSUM_KEY, 891568578L) // CRC32 of 'abc'
                .append(LAST_ACCESSED_KEY, firstLastAccessed);
        assertEquals(expectedFirstDocument, firstDocument);
        // Check that the last_accessed field is very recent
        assertTrue((System.currentTimeMillis() - firstLastAccessed.getTime()) / 1000 < 60, "The last_accessed field is too old");

        /*
        SECTION
         */
        // Trying to process again should yield a single SKIP because we processed the file very recently
        final Map<Result, Integer> thirdResults = fileProcessor.processFiles(tempDir, isImmutable);
        assertEquals(Map.of(Result.SKIP, 1), thirdResults);
        // The first document should stay in Mongo unmodified
        assertEquals(1, collection.countDocuments());
        assertEquals(expectedFirstDocument, collection.find().first());

        /*
        SECTION
         */
        // Sleep a bit because the recency util doesn't have nanosecond precision
        sleep(Duration.ofSeconds(2));
        // Let's modify the file to force a re-check
        Files.writeString(tempFile, "xyz", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        final Map<Result, Integer> fourthResults = fileProcessor.processFiles(tempDir, isImmutable);
        assertEquals(Map.of(Result.PASS, 1), fourthResults);
        // There should now be two documents: the first one and the new one for the modification
        assertEquals(2, collection.countDocuments());

        // Assert on the document itself.
        // Ensure we are sorting from oldest to newest document.
        final List<Document> documents = new ArrayList<>();
        collection.find().sort(Sorts.ascending(MONGO_ID_KEY)).into(documents);
        assertEquals(2, documents.size());

        final Instant secondModifiedTime = Files.getLastModifiedTime(tempFile).toInstant();
        final Document secondDocument = documents.get(1);
        final Date secondLastAccessed = (Date) secondDocument.get(LAST_ACCESSED_KEY);
        final Document expectedSecondDocument = new Document()
                .append(MONGO_ID_KEY, secondDocument.get(MONGO_ID_KEY))
                .append(FILE_ID_KEY, "cf3ab19e0eb597ee451ab1b172f4c43897ced2428d1e231938c9aaed9f93c16a") // SHA-256 of '\dir1\mutable-file.txt'
                .append(MODIFIED_TIME_SECONDS_KEY, secondModifiedTime.getEpochSecond())
                .append(MODIFIED_TIME_NANOS_KEY, secondModifiedTime.getNano())
                .append(SIZE_KEY, 3L)
                .append(CHECKSUM_KEY, 3951999591L) // CRC32 of 'xyz'
                .append(LAST_ACCESSED_KEY, secondLastAccessed);

        // Assert that the first document has not changed and second document is what we expect
        assertEquals(List.of(expectedFirstDocument, expectedSecondDocument), documents);

        // Check that the last_accessed field is very recent
        assertTrue((System.currentTimeMillis() - secondLastAccessed.getTime()) / 1000 < 60, "The last_accessed field is too old");
    }
}

package com.bitrot;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.util.Optional;

import static com.bitrot.Constants.MONGO_COLLECTION_NAME;
import static com.bitrot.Constants.MONGO_DB_NAME;

public class MongoManager {
    private final MongoCollection<Document> collection;

    @SuppressWarnings("resource")
    public MongoManager(final String connection_string) {
        final MongoClient client = MongoClients.create(connection_string);
        this.collection = client.getDatabase(MONGO_DB_NAME).getCollection(MONGO_COLLECTION_NAME);
    }

//    private Optional<Document> findDocument(final FileRecord fileRecord, final boolean isImmutable) {
//        Document query = new Document("fileId", fileRecord.getFileId())
//                .append("modifiedTime", fileRecord.getModifiedTime());
//        Document databaseDocument = filesCollection.find(query).first();
//    }

    public String getStoredCRC(final String filePath) {
        final Document doc = collection.find(new Document("filePath", filePath)).first();
        return doc != null ? doc.getString("crc") : null;
    }
}

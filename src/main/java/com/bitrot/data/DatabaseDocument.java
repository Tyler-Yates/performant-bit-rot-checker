package com.bitrot.data;

import org.bson.Document;
import org.bson.types.ObjectId;

import static com.bitrot.MongoManager.*;

public class DatabaseDocument {
    private final Document document;

    public DatabaseDocument(final Document document) {
        this.document = document;
    }

    public ObjectId objectId() {
        return (ObjectId) document.get(MONGO_ID_KEY);
    }

    public String fileId() {
        return document.get(FILE_ID_KEY, null);
    }

    public long mTimeSeconds() {
        return document.get(MODIFIED_TIME_SECONDS_KEY, -1L);
    }

    public int mTimeNanos() {
        return document.get(MODIFIED_TIME_NANOS_KEY, -1);
    }

    public long size() {
        return document.get(SIZE_KEY, -1L);
    }

    public long checksum() {
        return document.get(CHECKSUM_KEY, -1L);
    }
}

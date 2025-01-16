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
        return (String) document.get(FILE_ID_KEY);
    }

    public long mTimeSeconds() {
        final Object val = document.get(MODIFIED_TIME_SECONDS_KEY);
        if (val == null) {
            return -1L;
        }
        return ((Number) val).longValue();
    }

    public int mTimeNanos() {
        return document.get(MODIFIED_TIME_NANOS_KEY, -1);
    }

    public long size() {
        final Object val = document.get(SIZE_KEY);
        if (val == null) {
            return -1L;
        }
        return ((Number) val).longValue();
    }

    public long checksum() {
        final Object val = document.get(CHECKSUM_KEY);
        if (val == null) {
            return -1L;
        }
        return ((Number) val).longValue();
    }
}

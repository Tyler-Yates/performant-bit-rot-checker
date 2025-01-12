package com.bitrot;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;

import static com.bitrot.MongoManager.*;

public record DatabaseDocument(
        @JsonProperty(MONGO_ID_KEY) ObjectId objectId,
        @JsonProperty(FILE_ID_KEY) String fileId,
        @JsonProperty(MODIFIED_TIME_SECONDS_KEY) long mTimeSeconds,
        @JsonProperty(CHECKSUM_KEY) long checksum,
        @JsonProperty(SIZE_KEY) long size
) {
}

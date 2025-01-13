package com.bitrot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.bson.types.ObjectId;

import static com.bitrot.MongoManager.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DatabaseDocument(
        @JsonProperty(MONGO_ID_KEY) ObjectId objectId,
        @JsonProperty(FILE_ID_KEY) String fileId,
        @JsonProperty(MODIFIED_TIME_SECONDS_KEY) long mTimeSeconds,
        @JsonProperty(MODIFIED_TIME_NANOS_KEY) Integer mTimeNanos,
        @JsonProperty(CHECKSUM_KEY) long checksum,
        @JsonProperty(SIZE_KEY) long size
) {
}

package com.bitrot;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public interface Constants {
    Instant RECENCY_CLEANUP_THRESHOLD = Instant.now().minus(1, ChronoUnit.YEARS);
    Instant RECENCY_SKIP_THRESHOLD = Instant.now().minus(90, ChronoUnit.DAYS);

    String MONGO_DB_NAME = "bitrot";
    String MONGO_COLLECTION_NAME = "files";
}

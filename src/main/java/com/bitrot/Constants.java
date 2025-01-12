package com.bitrot;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public interface Constants {
    String CONFIG_FILE_NAME = "config.json";

    Instant DELETE_RECENCY_ENTRIES_OLDER_THAN = Instant.now().minus(365, ChronoUnit.DAYS);
    Instant SKIP_FILES_CHECKED_SINCE = Instant.now().minus(90, ChronoUnit.DAYS);
    Instant DO_NOT_SAVE_FILES_NEWER_THAN = Instant.now().minus(1, ChronoUnit.DAYS);

    // Prefix strings to skip when processing files.
    // Each prefix is evaluated for every part of the path.
    // For example [".st"] means that C:\Program Files\MyProgram\.stver\test.txt would be skipped
    List<String> SKIP_PREFIXES = List.of(".st");

    // Suffix strings to skip when processing files.
    // Each suffix is evaluated for every part of the path.
    // For example [".tmp"] means that C:\Program Files\MyProgram\.stver\test.txt.tmp would be skipped
    List<String> SKIP_SUFFIXES = List.of(".tmp");

    String MONGO_DB_NAME = "bitrot";
    String MONGO_COLLECTION_NAME = "files";
    long SECONDS_IN_A_YEAR = 60 * 60 * 24 * 366;
}

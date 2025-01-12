package com.bitrot;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static com.bitrot.Constants.CONFIG_FILE_NAME;

public class Config {
    private String mongoConnectionString;
    private List<String> mutablePaths;
    private List<String> immutablePaths;

    private Config() {}

    public static Config readConfig() throws IOException {
        final ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        try (final InputStream is = classloader.getResourceAsStream(CONFIG_FILE_NAME)) {
            final ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(is, Config.class);
        }
    }

    public String getMongoConnectionString() {
        return mongoConnectionString;
    }

    public void setMongoConnectionString(String mongoConnectionString) {
        this.mongoConnectionString = mongoConnectionString;
    }

    public List<String> getMutablePaths() {
        return mutablePaths;
    }

    public void setMutablePaths(List<String> mutablePaths) {
        this.mutablePaths = mutablePaths;
    }

    public List<String> getImmutablePaths() {
        return immutablePaths;
    }

    public void setImmutablePaths(List<String> immutablePaths) {
        this.immutablePaths = immutablePaths;
    }
}

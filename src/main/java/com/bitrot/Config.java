package com.bitrot;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.bitrot.Constants.CONFIG_FILE_NAME;

@SuppressWarnings("unused")  // We need the getters and setters for the ObjectMapper
public class Config {
    private String mongoConnectionString;
    private List<String> mutablePaths;
    private List<String> immutablePaths;
    private String healthCheckUrl;

    private Config() {}

    public static Config readConfig() throws IOException {
        final File configFile = new File(CONFIG_FILE_NAME);
        if (!configFile.exists()) {
            throw new IOException("Config file not found: " + CONFIG_FILE_NAME);
        }

        return new ObjectMapper().readValue(configFile, Config.class);
    }

    public String getMongoConnectionString() {
        return mongoConnectionString;
    }

    public void setMongoConnectionString(final String mongoConnectionString) {
        this.mongoConnectionString = mongoConnectionString;
    }

    public List<String> getMutablePaths() {
        return mutablePaths;
    }

    public void setMutablePaths(final List<String> mutablePaths) {
        this.mutablePaths = mutablePaths;
    }

    public List<String> getImmutablePaths() {
        return immutablePaths;
    }

    public void setImmutablePaths(final List<String> immutablePaths) {
        this.immutablePaths = immutablePaths;
    }

    public String getHealthCheckUrl() {
        return healthCheckUrl;
    }

    public void setHealthCheckUrl(final String healthCheckUrl) {
        this.healthCheckUrl = healthCheckUrl;
    }
}

package com.insoftu.thefix.client;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class TheFixSessionProfileStore {
    private static final String STORE_FILE_NAME = "profiles.json";

    private final TheFixClientConfig config;
    private final Path defaultStoragePath;

    private Path storagePath;
    private String activeProfileName;
    private LinkedHashMap<String, TheFixSessionProfile> profiles;

    TheFixSessionProfileStore(TheFixClientConfig config) {
        this.config = config;
        this.defaultStoragePath = Path.of(config.quickFixLogDir(), "profiles").toAbsolutePath().normalize();
        this.storagePath = defaultStoragePath;
        this.profiles = new LinkedHashMap<>();
        this.activeProfileName = TheFixSessionProfile.DEFAULT_PROFILE_NAME;
        try {
            loadOrInitialize(storagePath, true);
        } catch (IOException exception) {
            seedDefaults();
        }
    }

    synchronized TheFixSessionProfile activeProfile() {
        TheFixSessionProfile profile = profiles.get(activeProfileName);
        if (profile != null) {
            return profile;
        }
        seedDefaults();
        return profiles.get(activeProfileName);
    }

    synchronized TheFixSessionProfile profile(String profileName) {
        if (profileName == null || profileName.isBlank()) {
            return activeProfile();
        }
        TheFixSessionProfile profile = profiles.get(profileName.trim());
        return profile != null ? profile : activeProfile();
    }

    synchronized JsonObject snapshot() {
        JsonArray items = new JsonArray();
        for (TheFixSessionProfile profile : profiles.values()) {
            items.add(profile.toJson());
        }
        JsonArray fixVersions = new JsonArray();
        for (TheFixFixVersion version : TheFixFixVersion.options()) {
            fixVersions.add(version.toJson());
        }
        return new JsonObject()
                .put("storagePath", storagePath.toString())
                .put("defaultStoragePath", defaultStoragePath.toString())
                .put("activeProfileName", activeProfileName)
                .put("profiles", items)
                .put("fixVersionOptions", fixVersions)
                .put("profileDraft", activeProfile().toJson());
    }

    synchronized JsonObject workspaceSnapshot() {
        return new JsonObject()
                .put("storagePath", storagePath.toString())
                .put("defaultStoragePath", defaultStoragePath.toString())
                .put("profileCount", profiles.size());
    }

    synchronized void saveProfile(JsonObject request) {
        String originalName = request == null ? null : normalizeName(request.getString("originalName"));
        TheFixSessionProfile profile = TheFixSessionProfile.fromJson(request, config);
        if (originalName != null && !originalName.equals(profile.name())) {
            profiles.remove(originalName);
        }
        profiles.put(profile.name(), profile);
        boolean activate = request == null || !request.containsKey("activate") || request.getBoolean("activate", true);
        if (activate || activeProfileName == null || activeProfileName.isBlank() || profile.name().equals(originalName) || originalName != null && originalName.equals(activeProfileName)) {
            activeProfileName = profile.name();
        }
        persistQuietly();
    }

    synchronized void activateProfile(String profileName) {
        if (profileName == null || profileName.isBlank() || !profiles.containsKey(profileName)) {
            return;
        }
        activeProfileName = profileName;
        persistQuietly();
    }

    synchronized boolean deleteProfile(String profileName) {
        String normalizedName = normalizeName(profileName);
        if (normalizedName == null || profiles.size() <= 1 || !profiles.containsKey(normalizedName)) {
            return false;
        }
        profiles.remove(normalizedName);
        if (normalizedName.equals(activeProfileName)) {
            activeProfileName = profiles.keySet().iterator().next();
        }
        persistQuietly();
        return true;
    }

    synchronized void updateStoragePath(String requestedPath) {
        Path nextPath = requestedPath == null || requestedPath.isBlank()
                ? defaultStoragePath
                : Path.of(requestedPath).toAbsolutePath().normalize();
        try {
            if (Files.exists(storeFile(nextPath))) {
                loadOrInitialize(nextPath, false);
            } else {
                storagePath = nextPath;
                seedDefaults();
                persistQuietly();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to use profile storage path " + nextPath, exception);
        }
    }

    private void loadOrInitialize(Path path, boolean seedIfMissing) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        Path storeFile = storeFile(normalized);
        if (!Files.exists(storeFile)) {
            if (!seedIfMissing) {
                storagePath = normalized;
                seedDefaults();
                persistQuietly();
                return;
            }
            storagePath = normalized;
            seedDefaults();
            persistQuietly();
            return;
        }

        storagePath = normalized;
        JsonObject root = new JsonObject(Files.readString(storeFile));
        LinkedHashMap<String, TheFixSessionProfile> loadedProfiles = new LinkedHashMap<>();
        JsonArray profilesJson = root.getJsonArray("profiles", new JsonArray());
        for (int i = 0; i < profilesJson.size(); i++) {
            JsonObject profileJson = profilesJson.getJsonObject(i);
            TheFixSessionProfile profile = TheFixSessionProfile.fromJson(profileJson, config);
            loadedProfiles.put(profile.name(), profile);
        }
        profiles = loadedProfiles;
        if (profiles.isEmpty()) {
            seedDefaults();
        }
        activeProfileName = root.getString("activeProfileName", TheFixSessionProfile.DEFAULT_PROFILE_NAME);
        if (!profiles.containsKey(activeProfileName)) {
            activeProfileName = profiles.keySet().iterator().next();
        }
    }

    private void seedDefaults() {
        profiles = new LinkedHashMap<>(Map.of(TheFixSessionProfile.DEFAULT_PROFILE_NAME, TheFixSessionProfile.defaultProfile(config)));
        activeProfileName = TheFixSessionProfile.DEFAULT_PROFILE_NAME;
    }

    private void persistQuietly() {
        try {
            writeStore(storagePath, activeProfileName, profiles);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to persist FIX session profiles", exception);
        }
    }

    private static void writeStore(Path path, String activeProfileName, LinkedHashMap<String, TheFixSessionProfile> profiles) throws IOException {
        Files.createDirectories(path);
        JsonArray items = new JsonArray();
        for (TheFixSessionProfile profile : profiles.values()) {
            items.add(profile.toJson());
        }
        JsonObject root = new JsonObject()
                .put("activeProfileName", activeProfileName)
                .put("profiles", items);
        Files.writeString(storeFile(path), root.encodePrettily());
    }

    private static Path storeFile(Path path) {
        return path.resolve(STORE_FILE_NAME);
    }

    private static String normalizeName(String profileName) {
        if (profileName == null || profileName.isBlank()) {
            return null;
        }
        return profileName.trim();
    }
}


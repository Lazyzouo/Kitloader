package com.lazyz.kitloader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

public final class UpdateChecker {
    public static final String REPOSITORY = "Lazyzouo/Kitloader";
    public static final String REPOSITORY_URL = "https://github.com/" + REPOSITORY;
    public static final String RELEASES_URL = REPOSITORY_URL + "/releases";
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/" + REPOSITORY + "/releases/latest";
    private static final long MAX_DOWNLOAD_BYTES = 50L * 1024L * 1024L;

    private final Kitloader plugin;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public UpdateChecker(Kitloader plugin) {
        this.plugin = plugin;
    }

    public void checkOnStartup() {
        if (!plugin.getConfig().getBoolean("updates.enabled", true)) return;
        plugin.logLocalized("update_checking");
        Bukkit.getAsyncScheduler().runNow(plugin, task -> checkLatestRelease());
    }

    private void checkLatestRelease() {
        try {
            JsonObject release = requestJson(LATEST_RELEASE_API);
            String latestVersion = normalizeVersion(release.get("tag_name").getAsString());
            String currentVersion = normalizeVersion(plugin.getDescription().getVersion());
            if (compareVersions(latestVersion, currentVersion) <= 0) {
                plugin.logLocalized("update_latest", "version", currentVersion);
                return;
            }

            plugin.logLocalized("update_available", "latest", latestVersion, "current", currentVersion);
            if (!plugin.getConfig().getBoolean("updates.auto-download", true)) {
                plugin.logLocalized("update_manual", "version", latestVersion, "url", RELEASES_URL);
                return;
            }

            String jarName = activeLanguageSuffix() + ".jar";
            ReleaseAsset jarAsset = findAsset(release.getAsJsonArray("assets"), jarName);
            Path updateDirectory = Bukkit.getUpdateFolderFile().toPath();
            Files.createDirectories(updateDirectory);
            Path temporaryFile = Files.createTempFile(updateDirectory, "kitloader-", ".download");
            try {
                download(jarAsset.uri(), temporaryFile);
                String actualChecksum = sha256(temporaryFile);
                if (!actualChecksum.equalsIgnoreCase(jarAsset.sha256())) {
                    throw new IOException("SHA-256 checksum mismatch");
                }
                Files.move(temporaryFile, updateDirectory.resolve(jarName), StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporaryFile);
            }
            plugin.logLocalized("update_downloaded", "version", latestVersion);
        } catch (Exception exception) {
            plugin.logLocalized("update_failed", "reason", safeReason(exception), "url", RELEASES_URL);
        }
    }

    private JsonObject requestJson(String url) throws IOException, InterruptedException {
        return JsonParser.parseString(requestText(URI.create(url))).getAsJsonObject();
    }

    private String requestText(URI uri) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request(uri).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response.statusCode());
        return response.body();
    }

    private void download(URI uri, Path destination) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = httpClient.send(request(uri).build(),
                HttpResponse.BodyHandlers.ofInputStream());
        ensureSuccess(response.statusCode());
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (contentLength > MAX_DOWNLOAD_BYTES) throw new IOException("release asset exceeds 50 MiB");

        try (InputStream input = response.body(); var output = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            long total = 0L;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_DOWNLOAD_BYTES) throw new IOException("release asset exceeds 50 MiB");
                output.write(buffer, 0, read);
            }
        }
    }

    private HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Kitloader-UpdateChecker/" + plugin.getDescription().getVersion());
    }

    private ReleaseAsset findAsset(JsonArray assets, String expectedName) throws IOException {
        for (JsonElement element : assets) {
            JsonObject asset = element.getAsJsonObject();
            if (!expectedName.equals(asset.get("name").getAsString())) continue;
            String digest = asset.has("digest") && !asset.get("digest").isJsonNull()
                    ? asset.get("digest").getAsString()
                    : "";
            if (!digest.matches("(?i)^sha256:[0-9a-f]{64}$")) {
                throw new IOException("release asset has no valid SHA-256 digest: " + expectedName);
            }
            return new ReleaseAsset(
                    URI.create(asset.get("browser_download_url").getAsString()),
                    digest.substring("sha256:".length())
            );
        }
        throw new IOException("release asset not found: " + expectedName);
    }

    private String activeLanguageSuffix() {
        return plugin.getLanguageManager().getLanguage().toLowerCase(Locale.ROOT).startsWith("en")
                ? "en.us"
                : "zh.cn";
    }

    private void ensureSuccess(int statusCode) throws IOException {
        if (statusCode < 200 || statusCode >= 300) throw new IOException("GitHub returned HTTP " + statusCode);
    }

    private String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private int compareVersions(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        for (int index = 0; index < Math.max(leftParts.length, rightParts.length); index++) {
            int leftValue = index < leftParts.length ? parseVersionPart(leftParts[index]) : 0;
            int rightValue = index < rightParts.length ? parseVersionPart(rightParts[index]) : 0;
            if (leftValue != rightValue) return Integer.compare(leftValue, rightValue);
        }
        return 0;
    }

    private int parseVersionPart(String value) {
        String numeric = value.replaceFirst("[^0-9].*$", "");
        return numeric.isEmpty() ? 0 : Integer.parseInt(numeric);
    }

    private String normalizeVersion(String version) {
        String normalized = version == null ? "0.0.0" : version.trim();
        return normalized.startsWith("v") || normalized.startsWith("V") ? normalized.substring(1) : normalized;
    }

    private String safeReason(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message.replace('\n', ' ');
    }

    private record ReleaseAsset(URI uri, String sha256) {
    }
}

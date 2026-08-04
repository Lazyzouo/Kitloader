package com.lazyz.kitloader;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ConfigMigrator {
    static final String VERSION_PATH = "config-version";
    static final int CURRENT_VERSION = 1;

    private static final DateTimeFormatter BACKUP_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS", Locale.ROOT);

    private final Kitloader plugin;

    ConfigMigrator(Kitloader plugin) {
        this.plugin = plugin;
    }

    FileMigrationResult migrate() throws ConfigMigrationException {
        Path configPath = plugin.getDataFolder().toPath().resolve("config.yml");
        YamlConfiguration defaults = loadBundledDefaults();
        YamlConfiguration userConfig = loadUserConfig(configPath);
        MigrationResult result = migrateInMemory(userConfig, defaults);

        for (String warning : result.warnings()) {
            plugin.getLogger().warning(warning);
        }
        if (!result.changed()) {
            return new FileMigrationResult(false, null, result);
        }

        Path backupPath;
        try {
            backupPath = createBackup(configPath, result.previousVersion(), result.currentVersion());
            saveAtomically(userConfig, configPath, backupPath);
        } catch (IOException exception) {
            throw new ConfigMigrationException("Unable to back up or replace config.yml safely.", exception);
        }

        String relativeBackup = plugin.getDataFolder().toPath().relativize(backupPath).toString();
        plugin.getLogger().info("config.yml was updated safely from schema v"
                + result.previousVersion() + " to v" + result.currentVersion()
                + ". Backup: " + relativeBackup);
        return new FileMigrationResult(true, backupPath, result);
    }

    static MigrationResult migrateInMemory(YamlConfiguration userConfig, YamlConfiguration defaults)
            throws ConfigMigrationException {
        int bundledVersion = readVersion(defaults, "bundled config.yml");
        if (bundledVersion != CURRENT_VERSION) {
            throw new ConfigMigrationException("Bundled config.yml declares schema v" + bundledVersion
                    + " but this plugin requires v" + CURRENT_VERSION + ".");
        }

        int previousVersion = readVersion(userConfig, "server config.yml");
        if (previousVersion > CURRENT_VERSION) {
            throw new ConfigMigrationException("Server config.yml uses newer schema v" + previousVersion
                    + "; this plugin supports up to v" + CURRENT_VERSION + ". Refusing to downgrade it.");
        }

        List<String> warnings = new ArrayList<>();
        boolean changed = false;
        int workingVersion = previousVersion;
        while (workingVersion < CURRENT_VERSION) {
            changed |= switch (workingVersion) {
                case 0 -> migrateV0ToV1(userConfig, defaults, warnings);
                default -> throw new ConfigMigrationException(
                        "No configuration migration is registered for schema v" + workingVersion + ".");
            };
            workingVersion++;
        }

        changed |= mergeMissingDefaults(userConfig, defaults, "", warnings);

        Object rawVersion = userConfig.get(VERSION_PATH);
        if (!(rawVersion instanceof Integer version) || version != CURRENT_VERSION) {
            boolean versionWasMissing = rawVersion == null;
            userConfig.set(VERSION_PATH, CURRENT_VERSION);
            if (versionWasMissing) copyComments(defaults, VERSION_PATH, userConfig, VERSION_PATH);
            changed = true;
        }

        return new MigrationResult(previousVersion, CURRENT_VERSION, changed, List.copyOf(warnings));
    }

    private static boolean migrateV0ToV1(
            YamlConfiguration config,
            YamlConfiguration defaults,
            List<String> warnings
    ) {
        boolean changed = migrateLegacyRestrictedWorldName(config);
        changed |= migrateLegacyInventoryWhitelist(config, defaults, warnings);
        return changed;
    }

    private static boolean migrateLegacyRestrictedWorldName(YamlConfiguration config) {
        String path = "settings.single-use-worlds";
        if (!config.isList(path)) return false;

        List<String> worlds = new ArrayList<>(config.getStringList(path));
        boolean hasOverworld = worlds.stream().anyMatch(world -> world.equalsIgnoreCase("overworld"));
        if (hasOverworld) return false;

        for (int index = 0; index < worlds.size(); index++) {
            if (!worlds.get(index).equalsIgnoreCase("overworld2")) continue;
            worlds.set(index, "overworld");
            config.set(path, worlds);
            return true;
        }
        return false;
    }

    private static boolean migrateLegacyInventoryWhitelist(
            YamlConfiguration config,
            YamlConfiguration defaults,
            List<String> warnings
    ) {
        String legacyPath = "settings.inventory-editor.whitelist";
        String targetPath = "settings.bypass-whitelist";
        if (!config.contains(legacyPath)) return false;
        if (!config.isList(legacyPath)) {
            warnings.add("Skipped legacy key '" + legacyPath + "' because it is not a list; its value was preserved.");
            return false;
        }

        List<String> merged = new ArrayList<>(config.getStringList(targetPath));
        for (String legacyEntry : config.getStringList(legacyPath)) {
            boolean alreadyPresent = merged.stream().anyMatch(entry -> entry.equalsIgnoreCase(legacyEntry));
            if (!alreadyPresent) merged.add(legacyEntry);
        }

        boolean targetWasMissing = !config.contains(targetPath);
        config.set(targetPath, merged);
        if (targetWasMissing) copyComments(defaults, targetPath, config, targetPath);
        config.set(legacyPath, null);

        ConfigurationSection legacySection = config.getConfigurationSection("settings.inventory-editor");
        if (legacySection != null && legacySection.getKeys(false).isEmpty()) {
            config.set("settings.inventory-editor", null);
        }
        return true;
    }

    private static boolean mergeMissingDefaults(
            ConfigurationSection target,
            ConfigurationSection defaults,
            String parentPath,
            List<String> warnings
    ) {
        boolean changed = false;
        for (String key : defaults.getKeys(false)) {
            String fullPath = parentPath.isEmpty() ? key : parentPath + "." + key;
            if (VERSION_PATH.equals(fullPath)) continue;

            if (defaults.isConfigurationSection(key)) {
                if (!target.contains(key)) {
                    target.createSection(key);
                    copyComments(defaults, key, target, key);
                    changed = true;
                } else if (!target.isConfigurationSection(key)) {
                    warnings.add("Cannot add defaults below '" + fullPath
                            + "' because the server configuration stores a non-section value there; the value was preserved.");
                    continue;
                }

                ConfigurationSection targetSection = target.getConfigurationSection(key);
                ConfigurationSection defaultSection = defaults.getConfigurationSection(key);
                if (targetSection != null && defaultSection != null) {
                    changed |= mergeMissingDefaults(targetSection, defaultSection, fullPath, warnings);
                }
                continue;
            }

            if (target.contains(key)) continue;
            target.set(key, copyValue(defaults.get(key)));
            copyComments(defaults, key, target, key);
            changed = true;
        }
        return changed;
    }

    private static Object copyValue(Object value) {
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object entry : list) copy.add(copyValue(entry));
            return copy;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(entry.getKey(), copyValue(entry.getValue()));
            }
            return copy;
        }
        return value;
    }

    private static void copyComments(
            ConfigurationSection source,
            String sourcePath,
            ConfigurationSection target,
            String targetPath
    ) {
        List<String> comments = source.getComments(sourcePath);
        if (!comments.isEmpty()) target.setComments(targetPath, comments);
        List<String> inlineComments = source.getInlineComments(sourcePath);
        if (!inlineComments.isEmpty()) target.setInlineComments(targetPath, inlineComments);
    }

    private static int readVersion(YamlConfiguration config, String source) throws ConfigMigrationException {
        Object value = config.get(VERSION_PATH);
        if (value == null) return 0;

        int parsed;
        if (value instanceof Number number) {
            double numericValue = number.doubleValue();
            parsed = number.intValue();
            if (numericValue != parsed) {
                throw new ConfigMigrationException(source + " has a non-integer '" + VERSION_PATH + "' value.");
            }
        } else if (value instanceof String text) {
            try {
                parsed = Integer.parseInt(text.trim());
            } catch (NumberFormatException exception) {
                throw new ConfigMigrationException(source + " has an invalid '" + VERSION_PATH + "' value.", exception);
            }
        } else {
            throw new ConfigMigrationException(source + " has an invalid '" + VERSION_PATH + "' value type.");
        }

        if (parsed < 0) {
            throw new ConfigMigrationException(source + " has a negative '" + VERSION_PATH + "' value.");
        }
        return parsed;
    }

    private YamlConfiguration loadBundledDefaults() throws ConfigMigrationException {
        try (InputStream input = plugin.getResource("config.yml")) {
            if (input == null) throw new ConfigMigrationException("The plugin JAR does not contain config.yml.");
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                YamlConfiguration defaults = new YamlConfiguration();
                defaults.options().parseComments(true);
                defaults.load(reader);
                return defaults;
            }
        } catch (IOException | InvalidConfigurationException exception) {
            throw new ConfigMigrationException("Unable to read bundled config.yml.", exception);
        }
    }

    private YamlConfiguration loadUserConfig(Path configPath) throws ConfigMigrationException {
        if (!Files.isRegularFile(configPath)) {
            throw new ConfigMigrationException("Server config.yml does not exist after default creation.");
        }

        try {
            YamlConfiguration config = new YamlConfiguration();
            config.options().parseComments(true);
            config.load(configPath.toFile());
            return config;
        } catch (IOException | InvalidConfigurationException exception) {
            throw new ConfigMigrationException("Unable to parse server config.yml; it was left unchanged.", exception);
        }
    }

    private Path createBackup(Path configPath, int previousVersion, int currentVersion) throws IOException {
        Path backupDirectory = plugin.getDataFolder().toPath().resolve("config-backups");
        Files.createDirectories(backupDirectory);

        String timestamp = BACKUP_TIMESTAMP.format(LocalDateTime.now());
        String prefix = "config-v" + previousVersion + "-to-v" + currentVersion + "-" + timestamp;
        for (int attempt = 0; ; attempt++) {
            String suffix = attempt == 0 ? "" : "-" + attempt;
            Path candidate = backupDirectory.resolve(prefix + suffix + ".yml");
            try {
                return Files.copy(configPath, candidate, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                // Use a numbered suffix if two migrations happen in the same millisecond.
            }
        }
    }

    private void saveAtomically(YamlConfiguration config, Path configPath, Path backupPath) throws IOException {
        Path temporaryPath = Files.createTempFile(plugin.getDataFolder().toPath(), "config-migration-", ".tmp");
        try {
            config.save(temporaryPath.toFile());
            try {
                Files.move(temporaryPath, configPath,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryPath, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            if (!Files.exists(configPath) && Files.isRegularFile(backupPath)) {
                try {
                    Files.copy(backupPath, configPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException restoreFailure) {
                    exception.addSuppressed(restoreFailure);
                }
            }
            throw exception;
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }

    record MigrationResult(
            int previousVersion,
            int currentVersion,
            boolean changed,
            List<String> warnings
    ) {
    }

    record FileMigrationResult(
            boolean changed,
            Path backupPath,
            MigrationResult migration
    ) {
    }

    static final class ConfigMigrationException extends Exception {
        ConfigMigrationException(String message) {
            super(message);
        }

        ConfigMigrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

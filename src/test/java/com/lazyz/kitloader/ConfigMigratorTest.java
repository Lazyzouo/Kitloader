package com.lazyz.kitloader;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigratorTest {
    @Test
    void officialPresetsParseAndDeclareTheSupportedSchema() throws Exception {
        for (String preset : List.of("config.zh_CN.yml", "config.en_US.yml")) {
            YamlConfiguration config = load(Path.of("presets", preset));
            assertEquals(ConfigMigrator.CURRENT_VERSION, config.getInt(ConfigMigrator.VERSION_PATH), preset);
        }
    }

    @Test
    void fillsMissingDefaultsWithoutOverwritingUserValuesOrUnknownKeys() throws Exception {
        YamlConfiguration defaults = yaml("""
                config-version: 1
                language: zh_CN
                settings:
                  max-kits: 9
                  feature:
                    enabled: true
                    modes:
                      - standard
                """);
        YamlConfiguration user = yaml("""
                language: en_US
                settings:
                  max-kits: 42
                  custom-value: keep-me
                private-extension:
                  enabled: true
                """);

        ConfigMigrator.MigrationResult result = ConfigMigrator.migrateInMemory(user, defaults);

        assertTrue(result.changed());
        assertEquals(0, result.previousVersion());
        assertEquals(1, user.getInt("config-version"));
        assertEquals("en_US", user.getString("language"));
        assertEquals(42, user.getInt("settings.max-kits"));
        assertEquals("keep-me", user.getString("settings.custom-value"));
        assertTrue(user.getBoolean("private-extension.enabled"));
        assertTrue(user.getBoolean("settings.feature.enabled"));
        assertEquals(List.of("standard"), user.getStringList("settings.feature.modes"));
    }

    @Test
    void migratesLegacyKeysAndPreservesUnrelatedLegacySectionValues() throws Exception {
        YamlConfiguration defaults = yaml("""
                config-version: 1
                settings:
                  single-use-worlds: []
                  bypass-whitelist: []
                """);
        YamlConfiguration user = yaml("""
                settings:
                  single-use-worlds:
                    - overworld2
                  bypass-whitelist:
                    - Alice
                  inventory-editor:
                    whitelist:
                      - alice
                      - Bob
                    custom-value: keep-me
                """);

        ConfigMigrator.migrateInMemory(user, defaults);

        assertEquals(List.of("overworld"), user.getStringList("settings.single-use-worlds"));
        assertEquals(List.of("Alice", "Bob"), user.getStringList("settings.bypass-whitelist"));
        assertFalse(user.contains("settings.inventory-editor.whitelist"));
        assertEquals("keep-me", user.getString("settings.inventory-editor.custom-value"));
    }

    @Test
    void migrationIsIdempotentAfterTheFirstUpgrade() throws Exception {
        YamlConfiguration defaults = yaml("""
                config-version: 1
                language: zh_CN
                settings:
                  max-kits: 9
                """);
        YamlConfiguration user = yaml("language: en_US\n");

        ConfigMigrator.MigrationResult first = ConfigMigrator.migrateInMemory(user, defaults);
        ConfigMigrator.MigrationResult second = ConfigMigrator.migrateInMemory(user, defaults);

        assertTrue(first.changed());
        assertFalse(second.changed());
        assertEquals(1, second.previousVersion());
    }

    @Test
    void refusesToDowngradeANewerConfiguration() throws Exception {
        YamlConfiguration defaults = yaml("config-version: 1\n");
        YamlConfiguration user = yaml("config-version: 2\n");

        ConfigMigrator.ConfigMigrationException exception = assertThrows(
                ConfigMigrator.ConfigMigrationException.class,
                () -> ConfigMigrator.migrateInMemory(user, defaults));

        assertTrue(exception.getMessage().contains("Refusing to downgrade"));
        assertEquals(2, user.getInt("config-version"));
    }

    @Test
    void preservesAConflictingUserValueAndReportsTheSkippedDefaults() throws Exception {
        YamlConfiguration defaults = yaml("""
                config-version: 1
                settings:
                  feature:
                    enabled: true
                """);
        YamlConfiguration user = yaml("settings: custom-scalar\n");

        ConfigMigrator.MigrationResult result = ConfigMigrator.migrateInMemory(user, defaults);

        assertEquals("custom-scalar", user.getString("settings"));
        assertFalse(user.contains("settings.feature.enabled"));
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("settings"));
    }

    private static YamlConfiguration yaml(String source) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.options().parseComments(true);
        config.loadFromString(source);
        return config;
    }

    private static YamlConfiguration load(Path path) throws IOException, InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.options().parseComments(true);
        config.load(path.toFile());
        return config;
    }
}

package com.lazyz.kitloader;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LanguageManager {
    public static final String CHINESE = "zh_CN";
    public static final String ENGLISH = "en_US";
    private static final String REPLACEMENT_SEPARATOR = "|||";

    private final Kitloader plugin;
    private String language = CHINESE;
    private YamlConfiguration languageConfig;
    private List<Map.Entry<String, String>> forwardReplacements = List.of();
    private List<Map.Entry<String, String>> reverseReplacements = List.of();

    public LanguageManager(Kitloader plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        language = normalize(plugin.getConfig().getString("language", CHINESE));
        languageConfig = null;
        forwardReplacements = List.of();
        reverseReplacements = List.of();
        if (!ENGLISH.equals(language)) return;

        String resourcePath = "lang/en_US.yml";
        File languageFile = new File(plugin.getDataFolder(), resourcePath);
        if (!languageFile.exists()) plugin.saveResource(resourcePath, false);

        YamlConfiguration bundled = loadBundled(resourcePath);
        languageConfig = YamlConfiguration.loadConfiguration(languageFile);
        languageConfig.setDefaults(bundled);

        List<Map.Entry<String, String>> forward = new ArrayList<>();
        List<Map.Entry<String, String>> reverse = new ArrayList<>();
        for (String replacement : languageConfig.getStringList("inline-replacements")) {
            int separator = replacement.indexOf(REPLACEMENT_SEPARATOR);
            if (separator <= 0 || separator + REPLACEMENT_SEPARATOR.length() >= replacement.length()) continue;
            String source = replacement.substring(0, separator);
            String target = replacement.substring(separator + REPLACEMENT_SEPARATOR.length());
            forward.add(Map.entry(source, target));
            reverse.add(Map.entry(target, source));
        }
        forward.sort(Comparator.comparingInt((Map.Entry<String, String> entry) -> entry.getKey().length()).reversed());
        reverse.sort(Comparator.comparingInt((Map.Entry<String, String> entry) -> entry.getKey().length()).reversed());
        forwardReplacements = List.copyOf(forward);
        reverseReplacements = List.copyOf(reverse);
    }

    public Object getMessage(String key) {
        if (languageConfig != null) {
            Object localized = languageConfig.get("messages." + key);
            if (localized != null) return localized;
        }
        return plugin.getConfig().get("messages." + key);
    }

    public String getMessageString(String key, String fallback) {
        Object value = getMessage(key);
        return value instanceof String message ? message : fallback;
    }

    public List<String> getMessageList(String key) {
        if (languageConfig != null && languageConfig.isList("messages." + key)) {
            return languageConfig.getStringList("messages." + key);
        }
        return plugin.getConfig().getStringList("messages." + key);
    }

    public String getGuiString(String key, String fallback) {
        if (languageConfig != null) {
            String localized = languageConfig.getString("gui." + key);
            if (localized != null) return localized;
        }
        return plugin.getConfig().getString("gui." + key, fallback);
    }

    public List<String> getGuiStringList(String key) {
        if (languageConfig != null && languageConfig.isList("gui." + key)) {
            return languageConfig.getStringList("gui." + key);
        }
        return plugin.getConfig().getStringList("gui." + key);
    }

    public String translateInline(String text) {
        if (text == null || !ENGLISH.equals(language)) return text;
        String translated = text;
        for (Map.Entry<String, String> replacement : forwardReplacements) {
            translated = translated.replace(replacement.getKey(), replacement.getValue());
        }
        return translated;
    }

    public String canonicalize(String text) {
        if (text == null || !ENGLISH.equals(language)) return text;
        String canonical = text;
        for (Map.Entry<String, String> replacement : reverseReplacements) {
            canonical = canonical.replace(replacement.getKey(), replacement.getValue());
        }
        return canonical;
    }

    public String getLanguage() {
        return language;
    }

    private YamlConfiguration loadBundled(String resourcePath) {
        try (InputStream input = plugin.getResource(resourcePath)) {
            if (input == null) return new YamlConfiguration();
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            plugin.getLogger().warning("Unable to load bundled language defaults: " + exception.getMessage());
            return new YamlConfiguration();
        }
    }

    private String normalize(String value) {
        if (value == null) return CHINESE;
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.equals("en") || normalized.equals("en_us")) return ENGLISH;
        if (normalized.equals("zh") || normalized.equals("zh_cn")) return CHINESE;
        plugin.getLogger().warning("Unsupported language '" + value + "'; falling back to zh_CN.");
        return CHINESE;
    }
}

# Repository Maintenance Rules

1. Treat `src/main/resources/config.yml` as a local personal runtime file. Never commit it, copy it into release packages, or overwrite it while changing official defaults.
2. Official release presets belong in `presets/config.zh_CN.yml` and `presets/config.en_US.yml`. Preserve messages, comments, and non-parameter content; set only official-safe parameter defaults there.
3. Every functional update increments `version` in `build.gradle` and adds an English-first, Chinese-second entry to `CHANGELOG.md`. Major updates increment the major version.
4. Update `Kitloader.md` whenever administrators need to know about a changed option, limit, command, compatibility baseline, or gameplay logic.
5. Release assets must be exactly `build/libs/Kitloader-en.us.jar` and `build/libs/Kitloader-zh.cn.jar`. Do not upload custom source archives as Release assets.
6. Maintain Paper/Folia 1.21.11 and Java 21 compatibility. Preserve the open-source, no-backdoor, no-telemetry statement and the official Release URL.

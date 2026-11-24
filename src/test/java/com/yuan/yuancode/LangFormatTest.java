package com.yuan.yuancode;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class LangFormatTest {

    @Test
    void allDisplayedMessagesAreValidMiniMessage() throws Exception {
        YamlConfiguration yaml;
        try (var stream = getClass().getResourceAsStream("/lang.yml")) {
            assertNotNull(stream);
            yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        MiniMessage mini = MiniMessage.miniMessage();
        for (String key : yaml.getKeys(true)) {
            if (key.startsWith("gui.prompt.")) {
                // Prompt text is inserted through an escaped placeholder.
                continue;
            }
            Object value = yaml.get(key);
            if (value instanceof String text) {
                mini.deserialize(text);
            } else if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String text) {
                        mini.deserialize(text);
                    }
                }
            }
        }
    }
}

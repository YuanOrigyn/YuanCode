package com.yuan.yuancode;

import com.yuan.yuancode.code.CodeDefinition;
import com.yuan.yuancode.code.CodeParser;
import com.yuan.yuancode.code.CodeType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeParserTest {

    @Test
    void sampleCodesFileParses() throws Exception {
        YamlConfiguration yaml;
        try (var stream = getClass().getResourceAsStream("/codes.yml")) {
            assertNotNull(stream);
            yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        List<String> warnings = new ArrayList<>();
        CodeParser parser = new CodeParser(ZoneId.of("Asia/Shanghai"), "yyyy-MM-dd HH:mm:ss", warnings::add);
        Map<String, CodeDefinition> parsed = parser.parse(yaml.getConfigurationSection("codes"));

        assertEquals(6, parsed.size(), warnings.toString());
        assertTrue(parsed.containsKey("WELCOME-2026"));
        CodeDefinition welcome = parsed.get("WELCOME-2026");
        assertEquals(CodeType.ONE_TIME, welcome.type());
        assertEquals(1, welcome.globalLimit());
        assertEquals(4, welcome.rewards().size());
        assertEquals(1, welcome.branches().size());
        assertEquals("vip", welcome.branches().getFirst().id());

        CodeDefinition playerOnce = parsed.get("PLAYER-ONCE");
        assertEquals(CodeType.PLAYER_LIMIT, playerOnce.type());
        assertEquals(0, playerOnce.globalLimit());
        assertEquals(1, playerOnce.perPlayerLimit());

        CodeDefinition daily = parsed.get("DAILY-BONUS");
        assertEquals(CodeType.PERIODIC, daily.type());
        assertEquals(Duration.ofDays(1), daily.period());

        CodeDefinition flash = parsed.get("FLASH-SALE");
        assertEquals(CodeType.EXPIRING, flash.type());
        assertNotNull(flash.endAt());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }
}

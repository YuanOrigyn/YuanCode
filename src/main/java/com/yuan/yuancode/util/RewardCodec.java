package com.yuan.yuancode.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yuan.yuancode.code.CommandReward;
import com.yuan.yuancode.code.ItemReward;
import com.yuan.yuancode.code.MoneyReward;
import com.yuan.yuancode.code.PermissionReward;
import com.yuan.yuancode.code.RewardDefinition;
import com.yuan.yuancode.code.RewardType;
import org.bukkit.Material;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Compact JSON codec for rewards stored in the pending-delivery table. */
public final class RewardCodec {

    private RewardCodec() {
    }

    public static String encode(RewardDefinition reward) {
        JsonObject json = new JsonObject();
        json.addProperty("id", reward.id());
        json.addProperty("type", reward.type().name());
        json.addProperty("delayMs", reward.delay().toMillis());
        json.add("conditions", strings(reward.conditions()));
        switch (reward) {
            case ItemReward item -> {
                json.addProperty("material", item.material().name());
                json.addProperty("amount", item.amount());
                if (item.displayName() != null) {
                    json.addProperty("name", item.displayName());
                }
                json.add("lore", strings(item.lore()));
            }
            case MoneyReward money -> json.addProperty("amount", money.amount());
            case PermissionReward permission -> {
                json.addProperty("node", permission.node());
                if (!permission.permanent()) {
                    json.addProperty("durationMs", permission.duration().toMillis());
                }
            }
            case CommandReward command -> json.addProperty("command", command.command());
        }
        return json.toString();
    }

    public static RewardDefinition decode(String raw) {
        JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
        RewardType type = RewardType.parse(string(json, "type"));
        String id = string(json, "id");
        Duration delay = Duration.ofMillis(json.has("delayMs") ? json.get("delayMs").getAsLong() : 0L);
        List<String> conditions = strings(json.getAsJsonArray("conditions"));
        return switch (type) {
            case ITEM -> {
                Material material = Material.matchMaterial(string(json, "material"));
                if (material == null || material == Material.AIR) {
                    throw new IllegalArgumentException("Invalid material in pending reward");
                }
                String name = json.has("name") ? json.get("name").getAsString() : null;
                List<String> lore = strings(json.getAsJsonArray("lore"));
                yield new ItemReward(id, material, json.get("amount").getAsInt(), name, lore, delay, conditions);
            }
            case MONEY -> new MoneyReward(id, json.get("amount").getAsDouble(), delay, conditions);
            case PERMISSION -> {
                String node = string(json, "node");
                Duration duration = json.has("durationMs")
                        ? Duration.ofMillis(json.get("durationMs").getAsLong())
                        : null;
                yield new PermissionReward(id, node, duration, delay, conditions);
            }
            case COMMAND -> new CommandReward(id, string(json, "command"), delay, conditions);
        };
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static List<String> strings(JsonArray array) {
        if (array == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            result.add(element.getAsString());
        }
        return List.copyOf(result);
    }

    private static String string(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            throw new IllegalArgumentException("Missing reward field: " + key);
        }
        return json.get(key).getAsString();
    }
}

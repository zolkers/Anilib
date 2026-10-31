package fr.vriege.anilib.platform.desktopextensionhost;

import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonArray;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonElementKt;
import kotlinx.serialization.json.JsonNull;
import kotlinx.serialization.json.JsonObject;
import kotlinx.serialization.json.JsonPrimitive;

import java.util.LinkedHashMap;
import java.util.Map;

/* JSON transfer of script values across the browser adapter. */
public final class ExtensionScriptValues {
    private ExtensionScriptValues() {}

    public static String quote(String text) {
        return JsonElementKt.JsonPrimitive(text).toString();
    }

    public static Object decode(String json) {
        return value(Json.Default.parseToJsonElement(json));
    }

    private static Object value(JsonElement element) {
        if (element instanceof JsonNull) return null;
        if (element instanceof JsonArray array)
            return array.stream().map(ExtensionScriptValues::value).toList();
        if (element instanceof JsonObject object) {
            Map<String, Object> result = new LinkedHashMap<>();
            object.forEach((key, item) -> result.put(key, value(item)));
            return result;
        }
        JsonPrimitive primitive = (JsonPrimitive) element;
        if (primitive.isString()) return primitive.getContent();
        Boolean flag = JsonElementKt.getBooleanOrNull(primitive);
        if (flag != null) return flag;
        Integer integer = JsonElementKt.getIntOrNull(primitive);
        if (integer != null) return integer;
        Long number = JsonElementKt.getLongOrNull(primitive);
        return number != null ? number : JsonElementKt.getDouble(primitive);
    }
}

package dev.caerus.sdk.webhooks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.caerus.sdk.internal.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class JsonFields {

    private final JsonObject object;

    JsonFields(JsonObject object) {
        this.object = object == null ? new JsonObject() : object;
    }

    String string(String name) {
        return optionalString(name).orElse(null);
    }

    Optional<String> optionalString(String name) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull()) {
            return Optional.empty();
        }
        if (value.isJsonPrimitive()) {
            return Optional.of(value.getAsString());
        }
        return Optional.of(value.toString());
    }

    int integer(String name) {
        return optionalInteger(name).orElse(0);
    }

    Optional<Integer> optionalInteger(String name) {
        return number(name).map(Number::intValue);
    }

    long longValue(String name) {
        return number(name).map(Number::longValue).orElse(0L);
    }

    List<String> stringList(String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonArray()) {
            return Collections.emptyList();
        }
        JsonArray array = value.getAsJsonArray();
        List<String> items = new ArrayList<>(array.size());
        for (JsonElement item : array) {
            if (item != null && !item.isJsonNull()) {
                items.add(item.isJsonPrimitive() ? item.getAsString() : item.toString());
            }
        }
        return Collections.unmodifiableList(items);
    }

    Map<String, Object> asMap() {
        return Json.toMap(object);
    }

    private Optional<Number> number(String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            return Optional.empty();
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            return Optional.of(primitive.getAsNumber());
        }
        if (primitive.isString()) {
            try {
                return Optional.of(Double.parseDouble(primitive.getAsString().trim()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}

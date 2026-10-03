package dev.caerus.sdk.internal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.google.gson.Strictness;
import com.google.gson.ToNumberPolicy;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Json {

    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, Object>>() {
    }.getType();

    private static final Gson GSON = new GsonBuilder()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .serializeNulls()
            .disableHtmlEscaping()
            .create();

    private Json() {
    }

    public static Gson gson() {
        return GSON;
    }

    public static String stringify(Object value) {
        return GSON.toJson(value);
    }

    public static JsonElement parse(String raw) {
        try (JsonReader reader = new JsonReader(new StringReader(raw))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement element = GSON.getAdapter(JsonElement.class).read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new JsonSyntaxException("Unexpected content after the JSON value");
            }
            return element;
        } catch (IOException e) {
            throw new JsonSyntaxException(e);
        }
    }

    public static Map<String, Object> parseObject(String raw) {
        JsonElement element = parse(raw);
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("not a JSON object");
        }
        Map<String, Object> map = GSON.fromJson(element, MAP_TYPE);
        return Collections.unmodifiableMap(map);
    }

    public static Map<String, Object> toMap(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return Collections.emptyMap();
        }
        Map<String, Object> map = GSON.fromJson(element, MAP_TYPE);
        return Collections.unmodifiableMap(map);
    }
}

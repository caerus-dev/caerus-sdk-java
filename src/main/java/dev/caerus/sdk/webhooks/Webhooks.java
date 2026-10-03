package dev.caerus.sdk.webhooks;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.caerus.sdk.CaerusErrorOptions;
import dev.caerus.sdk.internal.Json;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class Webhooks {

    public static final long DEFAULT_TOLERANCE_SECONDS = 300;

    private final Clock clock;

    public Webhooks() {
        this(Clock.systemUTC());
    }

    Webhooks(Clock clock) {
        this.clock = clock;
    }

    public CaerusEvent constructEvent(String payload, String signatureHeader, String secret) {
        return constructEvent(payload, signatureHeader, secret, DEFAULT_TOLERANCE_SECONDS);
    }

    public CaerusEvent constructEvent(byte[] payload, String signatureHeader, String secret) {
        return constructEvent(payload, signatureHeader, secret, DEFAULT_TOLERANCE_SECONDS);
    }

    public CaerusEvent constructEvent(String payload, String signatureHeader, String secret, long toleranceSeconds) {
        byte[] body = payload == null ? new byte[0] : payload.getBytes(StandardCharsets.UTF_8);
        return constructEvent(body, signatureHeader, secret, toleranceSeconds);
    }

    public CaerusEvent constructEvent(byte[] payload, String signatureHeader, String secret, long toleranceSeconds) {
        if (signatureHeader == null || signatureHeader.isEmpty()) {
            throw new CaerusSignatureError("No signature header provided.");
        }

        if (secret == null || secret.trim().isEmpty()) {
            throw new CaerusSignatureError(
                    "No signing secret provided: verifying against an empty secret would accept forged events.");
        }

        String timestampText = null;
        List<String> signatures = new ArrayList<>();
        for (String part : signatureHeader.split(",", -1)) {
            String trimmed = part.trim();
            if (timestampText == null && trimmed.startsWith("t=")) {
                timestampText = trimmed.substring(2);
            }
            if (trimmed.startsWith("v1=")) {
                signatures.add(trimmed.substring(3));
            }
        }

        if (timestampText == null || timestampText.isEmpty() || signatures.isEmpty()) {
            throw new CaerusSignatureError("Invalid signature format.");
        }

        Long timestamp = parseLeadingInteger(timestampText);
        if (timestamp == null) {
            throw new CaerusSignatureError("Invalid signature format.");
        }

        long now = Math.floorDiv(clock.millis(), 1000L);
        if (Math.abs(now - timestamp) > toleranceSeconds) {
            throw new CaerusWebhookExpiredError("Webhook timestamp is outside of the tolerance zone.");
        }

        byte[] body = payload == null ? new byte[0] : payload;
        byte[] expected = sign(secret, (timestampText + ".").getBytes(StandardCharsets.UTF_8), body)
                .getBytes(StandardCharsets.US_ASCII);

        boolean valid = false;
        for (String signature : signatures) {
            byte[] candidate = signature.getBytes(StandardCharsets.US_ASCII);
            if (candidate.length == expected.length && MessageDigest.isEqual(candidate, expected)) {
                valid = true;
            }
        }

        if (!valid) {
            throw new CaerusSignatureError("No matching signature found.");
        }

        String rawPayload;
        try {
            rawPayload = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new CaerusWebhookPayloadError(
                    "The webhook signature is valid but the body is not valid UTF-8.",
                    CaerusErrorOptions.builder().cause(e).build());
        }

        JsonElement parsed;
        try {
            parsed = Json.parse(rawPayload);
        } catch (RuntimeException e) {
            throw new CaerusWebhookPayloadError(
                    "The webhook signature is valid but the body is not valid JSON.",
                    CaerusErrorOptions.builder().cause(e).build());
        }

        if (!parsed.isJsonObject()) {
            throw new CaerusWebhookPayloadError("The webhook signature is valid but the body is not a JSON object.");
        }

        return toEvent(parsed.getAsJsonObject());
    }

    static String sign(String secret, String content) {
        return sign(secret, content.getBytes(StandardCharsets.UTF_8), new byte[0]);
    }

    static String sign(String secret, byte[] prefix, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(prefix);
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available in this JVM", e);
        }
    }

    private static CaerusEvent toEvent(JsonObject envelope) {
        JsonFields fields = new JsonFields(envelope);
        String eventType = fields.string("eventType");
        JsonElement data = envelope.get("data");
        JsonFields dataFields = new JsonFields(data != null && data.isJsonObject() ? data.getAsJsonObject() : null);
        return new CaerusEvent(
                fields.string("id"),
                fields.string("eventId"),
                fields.string("product"),
                fields.string("objectType"),
                fields.string("objectId"),
                fields.string("environmentId"),
                fields.string("occurredAt"),
                eventType,
                EventTypes.decode(eventType, dataFields),
                fields.asMap());
    }

    private static Long parseLeadingInteger(String text) {
        int index = 0;
        int length = text.length();
        while (index < length && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        boolean negative = false;
        if (index < length && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
            negative = text.charAt(index) == '-';
            index++;
        }
        int start = index;
        long value = 0;
        while (index < length && Character.isDigit(text.charAt(index)) && text.charAt(index) < 128) {
            if (index - start >= 18) {
                return null;
            }
            value = value * 10 + (text.charAt(index) - '0');
            index++;
        }
        if (index == start) {
            return null;
        }
        return negative ? -value : value;
    }
}

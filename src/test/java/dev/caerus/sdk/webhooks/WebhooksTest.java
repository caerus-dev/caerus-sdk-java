package dev.caerus.sdk.webhooks;

import dev.caerus.sdk.CaerusError;
import dev.caerus.sdk.ErrorCode;
import dev.caerus.sdk.sre.CaerusClientOptions;
import dev.caerus.sdk.sre.InMemoryCaerusClient;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class WebhooksTest {

    private static final String SECRET = "test_secret_key";
    private static final long NOW = 1_790_000_000L;

    private final Webhooks webhooks = new Webhooks(Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));

    static String engineSign(long timestampSeconds, String payload, String secretKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((timestampSeconds + "." + payload).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String header(String payload, long timestamp, String secret) {
        return "t=" + timestamp + ",v1=" + engineSign(timestamp, payload, secret);
    }

    private static String header(String payload, long timestamp) {
        return header(payload, timestamp, SECRET);
    }

    @Test
    void constructsAnEventWithAValidSignatureAndPayload() {
        String payload = "{\"id\":\"evt_123\",\"eventId\":\"evt_123\",\"eventType\":\"resource.taken\",\"product\":\"SRE\","
                + "\"objectType\":\"RESOURCE_HOLDER\",\"objectId\":\"h_123\",\"environmentId\":\"env_abc\","
                + "\"occurredAt\":\"2026-08-29T15:00:00Z\",\"data\":{\"eventId\":\"evt_123\",\"occurredOn\":\"2026-08-29T15:00:00Z\","
                + "\"holderId\":\"h_123\",\"resourceKey\":\"seat_A12\",\"amount\":1,\"expiresAt\":1000,"
                + "\"createdAtMs\":1700000000000,\"idempotencyKey\":null}}";

        CaerusEvent event = webhooks.constructEvent(payload, header(payload, NOW), SECRET);

        assertThat(event.eventType()).isEqualTo("resource.taken");
        assertThat(event.eventId()).isEqualTo("evt_123");
        assertThat(event.product()).isEqualTo("SRE");
        assertThat(event.objectId()).isEqualTo("h_123");
        assertThat(event.data()).isInstanceOf(ResourceTakenData.class);
        ResourceTakenData data = (ResourceTakenData) event.data();
        assertThat(data.holderId()).isEqualTo("h_123");
        assertThat(data.resourceKey()).isEqualTo("seat_A12");
        assertThat(data.amount()).isEqualTo(1);
        assertThat(data.expiresAt()).isEqualTo(1000);
        assertThat(data.createdAtMs()).isEqualTo(1_700_000_000_000L);
        assertThat(data.idempotencyKey()).isEmpty();
        assertThat(data.metadata()).isEmpty();
        assertThat(data.occurredOn()).isEqualTo("2026-08-29T15:00:00Z");
    }

    @Test
    void handlesThePayloadAsBytes() {
        String payload = "{\"eventType\":\"resource.taken\"}";

        CaerusEvent event = webhooks.constructEvent(payload.getBytes(StandardCharsets.UTF_8), header(payload, NOW), SECRET);

        assertThat(event.eventType()).isEqualTo("resource.taken");
    }

    @Test
    void verifiesTheRawUtf8Bytes() {
        String payload = "{\"eventType\":\"resource.created\",\"data\":{\"resourceKey\":\"butaca_ñ_€\"}}";

        CaerusEvent event = webhooks.constructEvent(payload.getBytes(StandardCharsets.UTF_8), header(payload, NOW), SECRET);

        assertThat(((ResourceCreatedData) event.data()).resourceKey()).isEqualTo("butaca_ñ_€");
    }

    @Test
    void verifiesTheSignatureOverTheRawBytesEvenWhenTheyAreNotValidUtf8() throws Exception {
        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        body.write("{\"eventType\":\"x\",\"b\":\"".getBytes(StandardCharsets.UTF_8));
        body.write(0xff);
        body.write("\"}".getBytes(StandardCharsets.UTF_8));
        byte[] raw = body.toByteArray();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((NOW + ".").getBytes(StandardCharsets.UTF_8));
        String signed = "t=" + NOW + ",v1=" + HexFormat.of().formatHex(mac.doFinal(raw));

        assertThatThrownBy(() -> webhooks.constructEvent(raw, signed, SECRET))
                .isInstanceOf(CaerusWebhookPayloadError.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    void throwsASignatureErrorIfTheHeaderIsMissing() {
        assertThatThrownBy(() -> webhooks.constructEvent("{}", "", SECRET)).isInstanceOf(CaerusSignatureError.class);
        assertThatThrownBy(() -> webhooks.constructEvent("{}", (String) null, SECRET)).isInstanceOf(CaerusSignatureError.class);
    }

    @Test
    void throwsASignatureErrorOnAnInvalidFormat() {
        assertThatThrownBy(() -> webhooks.constructEvent("{}", "t=123,v2=invalid", SECRET))
                .isInstanceOf(CaerusSignatureError.class)
                .hasMessage("Invalid signature format.");
        assertThatThrownBy(() -> webhooks.constructEvent("{}", "t=,v1=abc", SECRET))
                .hasMessage("Invalid signature format.");
        assertThatThrownBy(() -> webhooks.constructEvent("{}", "t=nope,v1=abc", SECRET))
                .hasMessage("Invalid signature format.");
        assertThatThrownBy(() -> webhooks.constructEvent("{}", "v1=abc", SECRET))
                .hasMessage("Invalid signature format.");
    }

    @Test
    void throwsAnExpiredErrorOutsideTheToleranceZone() {
        String payload = "{}";

        assertThatThrownBy(() -> webhooks.constructEvent(payload, header(payload, NOW - 360), SECRET, 300))
                .isInstanceOf(CaerusWebhookExpiredError.class);
        assertThatThrownBy(() -> webhooks.constructEvent(payload, header(payload, NOW + 360), SECRET, 300))
                .isInstanceOf(CaerusWebhookExpiredError.class);
    }

    @Test
    void acceptsAnEventWithinTheToleranceZone() {
        String payload = "{\"eventType\":\"test\"}";

        CaerusEvent event = webhooks.constructEvent(payload, header(payload, NOW - 240), SECRET, 300);

        assertThat(event.eventType()).isEqualTo("test");
    }

    @Test
    void theDefaultToleranceIsFiveMinutes() {
        String payload = "{\"eventType\":\"test\"}";

        assertThat(webhooks.constructEvent(payload, header(payload, NOW - 300), SECRET).eventType()).isEqualTo("test");
        assertThatThrownBy(() -> webhooks.constructEvent(payload, header(payload, NOW - 301), SECRET))
                .isInstanceOf(CaerusWebhookExpiredError.class);
    }

    @Test
    void supportsKeyRolloverWithSeveralSignatures() {
        String payload = "{\"eventType\":\"test\"}";
        String oldSignature = engineSign(NOW, payload, "old_secret");
        String newSignature = engineSign(NOW, payload, SECRET);

        CaerusEvent event = webhooks.constructEvent(payload,
                "t=" + NOW + ", v1=" + oldSignature + " , v1=" + newSignature, SECRET);

        assertThat(event.eventType()).isEqualTo("test");
    }

    @Test
    void throwsASignatureErrorForTheWrongSecret() {
        String payload = "{\"eventType\":\"test\"}";

        assertThatThrownBy(() -> webhooks.constructEvent(payload, header(payload, NOW, "wrong_secret"), SECRET))
                .isInstanceOf(CaerusSignatureError.class)
                .hasMessage("No matching signature found.");
    }

    @Test
    void refusesSignaturesOfTheWrongLength() {
        String payload = "{\"eventType\":\"test\"}";

        assertThatThrownBy(() -> webhooks.constructEvent(payload, "t=" + NOW + ",v1=1234567890abcdef", SECRET))
                .isInstanceOf(CaerusSignatureError.class);
        assertThatThrownBy(() -> webhooks.constructEvent(payload, "t=" + NOW + ",v1=" + "a".repeat(100), SECRET))
                .isInstanceOf(CaerusSignatureError.class);
    }

    @Test
    void aTamperedBodyFailsTheSignature() {
        String payload = "{\"eventType\":\"test\",\"amount\":1}";
        String signed = header(payload, NOW);

        assertThatThrownBy(() -> webhooks.constructEvent(payload.replace("1", "9"), signed, SECRET))
                .isInstanceOf(CaerusSignatureError.class);
    }

    @Test
    void decodesEveryKnownEventIntoItsOwnType() {
        Map<String, Class<? extends EventData>> expected = Map.ofEntries(
                Map.entry("resource.created", ResourceCreatedData.class),
                Map.entry("resource.taken", ResourceTakenData.class),
                Map.entry("resource.confirmed", ResourceConfirmedData.class),
                Map.entry("resource.released", ResourceReleasedData.class),
                Map.entry("resource.extended", ResourceExtendedData.class),
                Map.entry("resource.expired", ResourceExpiredData.class),
                Map.entry("resource.updated", ResourceUpdatedData.class),
                Map.entry("resource.deleted", ResourceDeletedData.class),
                Map.entry("resource.queued", ResourceQueuedData.class),
                Map.entry("resource.take_failed", ResourceTakeFailedData.class),
                Map.entry("lock.acquired", LockAcquiredData.class),
                Map.entry("lock.released", LockReleasedData.class),
                Map.entry("lock.acquire_failed", LockAcquireFailedData.class),
                Map.entry("lock.deadlock_detected", DeadlockDetectedData.class),
                Map.entry("lock.abandoned", LockAbandonedData.class),
                Map.entry("transaction.started", TransactionStartedData.class),
                Map.entry("transaction.completed", TransactionCompletedData.class),
                Map.entry("transaction.aborted", TransactionAbortedData.class),
                Map.entry("transaction.renewed", TransactionRenewedData.class),
                Map.entry("transaction.expired", TransactionExpiredData.class));

        for (Map.Entry<String, Class<? extends EventData>> entry : expected.entrySet()) {
            String payload = "{\"eventType\":\"" + entry.getKey() + "\",\"data\":{\"eventId\":\"e1\"}}";
            CaerusEvent event = webhooks.constructEvent(payload, header(payload, NOW), SECRET);
            assertThat(event.data()).as(entry.getKey()).isExactlyInstanceOf(entry.getValue());
            assertThat(event.data().eventId()).isEqualTo("e1");
        }
    }

    @Test
    void readsTheDeadlockCycle() {
        String payload = "{\"eventType\":\"lock.deadlock_detected\",\"data\":{\"victimTransactionId\":\"tx-2\","
                + "\"cycleTransactionIds\":[\"tx-1\",\"tx-2\"],\"resolutionStrategy\":\"ABORT\"}}";

        DeadlockDetectedData data = (DeadlockDetectedData) webhooks.constructEvent(payload, header(payload, NOW), SECRET).data();

        assertThat(data.victimTransactionId()).isEqualTo("tx-2");
        assertThat(data.cycleTransactionIds()).containsExactly("tx-1", "tx-2");
        assertThat(data.resolutionStrategy()).isEqualTo("ABORT");
        assertThat(data.reason()).isEmpty();
    }

    @Test
    void readsAFencingTokenBeyondTheIntRange() {
        String payload = "{\"eventType\":\"lock.acquired\",\"data\":{\"fencingToken\":9007199254740993,\"mode\":\"EXCLUSIVE\"}}";

        LockAcquiredData data = (LockAcquiredData) webhooks.constructEvent(payload, header(payload, NOW), SECRET).data();

        assertThat(data.fencingToken()).isEqualTo(9_007_199_254_740_993L);
        assertThat(data.mode()).isEqualTo("EXCLUSIVE");
    }

    @Test
    void anEventTheSdkDoesNotKnowYetDoesNotBreakTheConsumer() {
        String payload = "{\"eventType\":\"queue.something_new\",\"data\":{\"eventId\":\"e9\",\"whatever\":[1,2]}}";

        CaerusEvent event = webhooks.constructEvent(payload, header(payload, NOW), SECRET);

        assertThat(event.data()).isInstanceOf(UnknownEventData.class);
        UnknownEventData data = (UnknownEventData) event.data();
        assertThat(data.eventId()).isEqualTo("e9");
        assertThat(data.fields()).containsEntry("whatever", List.of(1L, 2L));
    }

    @Test
    void keepsTheWholeEnvelope() {
        String payload = "{\"eventType\":\"test\",\"futureField\":true}";

        assertThat(webhooks.constructEvent(payload, header(payload, NOW), SECRET).raw()).containsEntry("futureField", true);
    }

    @Test
    void aSignedBodyThatIsNotJsonGivesAPackageErrorWithItsCause() {
        String body = "esto no es json";

        CaerusWebhookPayloadError error = catchThrowableOfType(CaerusWebhookPayloadError.class,
                () -> webhooks.constructEvent(body, header(body, NOW), SECRET));

        assertThat(error.getMessage()).isEqualTo("The webhook signature is valid but the body is not valid JSON.");
        assertThat(error.getCause()).isNotNull();
        assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION);
    }

    @Test
    void aSignedBodyThatIsJsonButNotAnObjectIsAPayloadError() {
        String body = "[1,2,3]";

        assertThatThrownBy(() -> webhooks.constructEvent(body, header(body, NOW), SECRET))
                .isInstanceOf(CaerusWebhookPayloadError.class);
    }

    @Test
    void theClientsExposeWebhooks() {
        assertThat(new InMemoryCaerusClient().webhooks()).isNotNull();
        try (dev.caerus.sdk.sre.CaerusClient client = new dev.caerus.sdk.sre.CaerusClient(
                CaerusClientOptions.builder().apiKey("k").endpoint("localhost:9").tls(false).build())) {
            assertThat(client.webhooks()).isNotNull();
        }
    }

    @Test
    void everyWebhookErrorIsAValidationCaerusError() {
        List<Throwable> errors = List.of(
                catchThrowable(() -> webhooks.constructEvent("{}", "", SECRET)),
                catchThrowable(() -> webhooks.constructEvent("{}", "sin formato", SECRET)),
                catchThrowable(() -> webhooks.constructEvent("{}", header("{}", NOW - 1000), SECRET)),
                catchThrowable(() -> webhooks.constructEvent("{}", header("{}", NOW), "")));

        for (Throwable error : errors) {
            assertThat(error).isInstanceOf(CaerusError.class);
            assertThat(((CaerusError) error).code()).isEqualTo(ErrorCode.VALIDATION);
        }
    }
}

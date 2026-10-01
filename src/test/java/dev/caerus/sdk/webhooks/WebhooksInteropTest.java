package dev.caerus.sdk.webhooks;

import dev.caerus.sdk.CaerusError;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class WebhooksInteropTest {

    private static final String ENGINE_HEX = "5e3b7a684499bb93c4402adc4c4c6914c9c19be915d55179ba6b881175fe8775";
    private static final long TS = 1_724_425_200L;
    private static final String PAYLOAD = "{\"event_id\":\"123\",\"eventType\":\"resource.taken\"}";
    private static final String SECRET = "whsec_test_secret_key_123456789";
    private static final long NO_EXPIRY = 1_000_000_000L;

    private final Webhooks webhooks = new Webhooks();

    @Test
    void signsExactlyTheSameAsTheEngineAndTheTypeScriptSdk() {
        assertThat(Webhooks.sign(SECRET, TS + "." + PAYLOAD)).isEqualTo(ENGINE_HEX);
        assertThat(WebhooksTest.engineSign(TS, PAYLOAD, SECRET)).isEqualTo(ENGINE_HEX);
    }

    @Test
    void acceptsASignatureGeneratedByTheEngine() {
        CaerusEvent event = webhooks.constructEvent(PAYLOAD, "t=" + TS + ",v1=" + ENGINE_HEX, SECRET, NO_EXPIRY);

        assertThat(event.raw()).containsEntry("event_id", "123");
        assertThat(event.eventType()).isEqualTo("resource.taken");
        assertThat(event.data()).isInstanceOf(ResourceTakenData.class);
    }

    @Test
    void refusesASignatureThatDoesNotMatch() {
        assertThatThrownBy(() -> webhooks.constructEvent(PAYLOAD, "t=" + TS + ",v1=" + "0".repeat(64), SECRET, NO_EXPIRY))
                .isInstanceOf(CaerusSignatureError.class);
    }

    @Test
    void refusesAnOldEventWithTheDefaultTolerance() {
        assertThatThrownBy(() -> webhooks.constructEvent(PAYLOAD, "t=" + TS + ",v1=" + ENGINE_HEX, SECRET))
                .isInstanceOf(CaerusWebhookExpiredError.class);
    }

    @Test
    void refusesAnEmptySecretInsteadOfVerifyingAgainstNothing() {
        String withEmpty = WebhooksTest.engineSign(TS, PAYLOAD, "x").replace('x', 'y');

        assertThatThrownBy(() -> webhooks.constructEvent(PAYLOAD, "t=" + TS + ",v1=" + withEmpty, "", NO_EXPIRY))
                .isInstanceOf(CaerusSignatureError.class);
        assertThatThrownBy(() -> webhooks.constructEvent(PAYLOAD, "t=" + TS + ",v1=" + withEmpty, "   ", NO_EXPIRY))
                .isInstanceOf(CaerusSignatureError.class);
        assertThatThrownBy(() -> webhooks.constructEvent(PAYLOAD, "t=" + TS + ",v1=" + withEmpty, null, NO_EXPIRY))
                .isInstanceOf(CaerusSignatureError.class);
    }

    @Test
    void aSignedBodyThatIsNotJsonIsAPackageError() {
        String body = "esto no es json";
        String signature = Webhooks.sign(SECRET, TS + "." + body);

        assertThatThrownBy(() -> webhooks.constructEvent(body, "t=" + TS + ",v1=" + signature, SECRET, NO_EXPIRY))
                .isInstanceOf(CaerusWebhookPayloadError.class);
    }

    @Test
    void everyWebhookErrorIsCaughtAsCaerusError() {
        List<Throwable> errors = List.of(
                catchThrowable(() -> webhooks.constructEvent(PAYLOAD, "", SECRET)),
                catchThrowable(() -> webhooks.constructEvent(PAYLOAD, "sin formato", SECRET)),
                catchThrowable(() -> webhooks.constructEvent(PAYLOAD, "t=" + TS + ",v1=" + ENGINE_HEX, SECRET)),
                catchThrowable(() -> webhooks.constructEvent(PAYLOAD, "t=" + TS + ",v1=" + ENGINE_HEX, "", NO_EXPIRY)));

        for (Throwable error : errors) {
            assertThat(error).isInstanceOf(CaerusError.class);
        }
    }
}

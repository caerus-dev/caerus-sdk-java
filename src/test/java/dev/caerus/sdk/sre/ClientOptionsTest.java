package dev.caerus.sdk.sre;

import dev.caerus.sdk.CaerusSdk;
import dev.caerus.sdk.internal.ClientSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientOptionsTest {

    private static final String KEY = "no-es-una-clave";

    private static UnaryOperator<String> env(Map<String, String> values) {
        return values::get;
    }

    private static UnaryOperator<String> noEnv() {
        return name -> null;
    }

    private static CaerusClientOptions.Builder base() {
        return CaerusClientOptions.builder().endpoint("localhost:9090").apiKey("k");
    }

    @Test
    void refusesNoOptionsAtAll() {
        assertThatThrownBy(() -> new CaerusClient((CaerusClientOptions) null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesAnEmptyBuilder() {
        assertThatThrownBy(() -> new CaerusClient(CaerusClientOptions.builder().build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesABlankApiKey() {
        assertThatThrownBy(() -> new CaerusClient(CaerusClientOptions.builder().apiKey("  ").build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saysWhatIsMissingWhenApiKeyIsAbsent() {
        assertThatThrownBy(() -> new CaerusClient(CaerusClientOptions.builder().endpoint("localhost:9090").build()))
                .hasMessageContaining("apiKey");
    }

    @Test
    void keepsTheApiKeyOutOfEverythingThatGetsPrinted() {
        CaerusClientOptions.Builder builder = CaerusClientOptions.builder().endpoint("localhost:9090").apiKey(KEY);
        CaerusClientOptions options = builder.build();
        ClientSettings settings = CaerusClient.resolve(options, noEnv());

        try (CaerusClient client = new CaerusClient(settings)) {
            assertThat(client.toString()).doesNotContain(KEY);
            assertThat(options.toString()).doesNotContain(KEY);
            assertThat(builder.toString()).doesNotContain(KEY);
            assertThat(settings.toString()).doesNotContain(KEY);
        }
    }

    @Test
    void needsNothingButAnApiKeyToReachTheHostedCaerus() {
        ClientSettings settings = CaerusClient.resolve(CaerusClientOptions.builder().apiKey(KEY).build(), noEnv());
        try (CaerusClient client = new CaerusClient(settings)) {
            assertThat(client.endpoint()).isEqualTo(CaerusSdk.DEFAULT_ENDPOINT);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void refusesANonPositiveTimeout(long timeoutMs) {
        assertThatThrownBy(() -> new CaerusClient(CaerusClientOptions.builder().apiKey(KEY).timeoutMs(timeoutMs).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void trimsTheApiKey() {
        ClientSettings settings = CaerusClient.resolve(base().apiKey("  k  ").build(), noEnv());
        assertThat(settings.apiKey()).isEqualTo("k");
    }

    @Test
    void encryptsTheConnectionUnlessToldOtherwise() {
        assertThat(CaerusClient.resolve(base().build(), noEnv()).tls()).isTrue();
        assertThat(CaerusClient.resolve(base().tls(false).build(), noEnv()).tls()).isFalse();
    }

    @Test
    void putsADeadlineOnCallsEvenWhenNoneIsAskedFor() {
        assertThat(CaerusClient.resolve(base().build(), noEnv()).timeoutMs()).isEqualTo(CaerusSdk.DEFAULT_TIMEOUT_MS);
    }

    @Test
    void prefersAnExplicitEndpointThenTheEnvironmentThenTheBuiltInOne() {
        Map<String, String> values = new HashMap<>();
        values.put("CAERUS_ENDPOINT", "del-entorno:9090");

        assertThat(CaerusClient.resolve(
                CaerusClientOptions.builder().apiKey("k").endpoint("explicito:9090").build(), env(values)).endpoint())
                .isEqualTo("explicito:9090");
        assertThat(CaerusClient.resolve(CaerusClientOptions.builder().apiKey("k").build(), env(values)).endpoint())
                .isEqualTo("del-entorno:9090");

        values.remove("CAERUS_ENDPOINT");
        assertThat(CaerusClient.resolve(CaerusClientOptions.builder().apiKey("k").build(), env(values)).endpoint())
                .isEqualTo(CaerusSdk.DEFAULT_ENDPOINT);
    }

    @Test
    void ignoresABlankEndpointInTheEnvironment() {
        Map<String, String> values = Map.of("CAERUS_ENDPOINT", "   ");
        assertThat(CaerusClient.resolve(CaerusClientOptions.builder().apiKey("k").build(), env(values)).endpoint())
                .isEqualTo(CaerusSdk.DEFAULT_ENDPOINT);
    }

    @Test
    void refusesAnExplicitBlankEndpoint() {
        assertThatThrownBy(() -> new CaerusClient(CaerusClientOptions.builder().endpoint("   ").apiKey(KEY).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void honorsEnvironmentOverridesForEndpointAndTls() {
        Map<String, String> values = Map.of("CAERUS_ENDPOINT", "localhost:9090", "CAERUS_TLS", "false");
        ClientSettings resolved = CaerusClient.resolve(CaerusClientOptions.builder().apiKey("k").build(), env(values));
        assertThat(resolved.endpoint()).isEqualTo("localhost:9090");
        assertThat(resolved.tls()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "FALSE", " false ", "0"})
    void letsAnExplicitFalseOrZeroDisableEncryption(String off) {
        assertThat(CaerusClient.resolve(base().build(), env(Map.of("CAERUS_TLS", off))).tls()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE", "True", "1", "yes", "on", "", "flase"})
    void letsNothingElseDisableEncryption(String on) {
        assertThat(CaerusClient.resolve(base().build(), env(Map.of("CAERUS_TLS", on))).tls()).isTrue();
    }

    @Test
    void anExplicitTlsOptionWinsOverTheEnvironment() {
        assertThat(CaerusClient.resolve(base().tls(true).build(), env(Map.of("CAERUS_TLS", "false"))).tls()).isTrue();
    }

    @Test
    void ignoresTheDlsVariables() {
        Map<String, String> values = Map.of("CAERUS_DLS_ENDPOINT", "dls:1", "CAERUS_DLS_TLS", "false");
        ClientSettings resolved = CaerusClient.resolve(CaerusClientOptions.builder().apiKey("k").build(), env(values));
        assertThat(resolved.endpoint()).isEqualTo(CaerusSdk.DEFAULT_ENDPOINT);
        assertThat(resolved.tls()).isTrue();
    }
}

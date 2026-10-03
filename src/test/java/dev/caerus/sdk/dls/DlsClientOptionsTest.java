package dev.caerus.sdk.dls;

import dev.caerus.sdk.CaerusSdk;
import dev.caerus.sdk.internal.ClientSettings;
import dev.caerus.sdk.internal.StderrLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DlsClientOptionsTest {

    private static final String KEY = "no-es-una-clave";

    private static ClientSettings resolve(DlsClientOptions options, Map<String, String> env) {
        return DlsClient.resolve(options, env::get);
    }

    private static DlsClientOptions.Builder base() {
        return DlsClientOptions.builder().endpoint("localhost:9090").apiKey("k");
    }

    @Test
    void refusesNoOptionsAtAll() {
        assertThatThrownBy(() -> new DlsClient((DlsClientOptions) null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesAMissingOrBlankApiKey() {
        assertThatThrownBy(() -> new DlsClient(DlsClientOptions.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
        assertThatThrownBy(() -> new DlsClient(DlsClientOptions.builder().apiKey("  ").build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsTheApiKeyOutOfEverythingThatGetsPrinted() {
        DlsClientOptions options = DlsClientOptions.builder().endpoint("localhost:9090").apiKey(KEY).build();
        try (DlsClient client = new DlsClient(resolve(options, Map.of()))) {
            assertThat(client.toString()).doesNotContain(KEY);
            assertThat(options.toString()).doesNotContain(KEY);
        }
    }

    @Test
    void needsNothingButAnApiKeyToReachTheHostedCaerus() {
        assertThat(resolve(DlsClientOptions.builder().apiKey(KEY).build(), Map.of()).endpoint())
                .isEqualTo(CaerusSdk.DEFAULT_ENDPOINT);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void refusesANonPositiveTimeout(long timeoutMs) {
        assertThatThrownBy(() -> new DlsClient(DlsClientOptions.builder().apiKey(KEY).timeoutMs(timeoutMs).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encryptsAndPutsADeadlineByDefault() {
        ClientSettings settings = resolve(base().build(), Map.of());
        assertThat(settings.tls()).isTrue();
        assertThat(settings.timeoutMs()).isEqualTo(CaerusSdk.DEFAULT_TIMEOUT_MS);
    }

    @Test
    void readsItsOwnVariablesNotTheSreOnes() {
        ClientSettings fromDls = resolve(DlsClientOptions.builder().apiKey("k").build(),
                Map.of("CAERUS_DLS_ENDPOINT", "dls:9090", "CAERUS_DLS_TLS", "0"));
        assertThat(fromDls.endpoint()).isEqualTo("dls:9090");
        assertThat(fromDls.tls()).isFalse();

        ClientSettings fromSre = resolve(DlsClientOptions.builder().apiKey("k").build(),
                Map.of("CAERUS_ENDPOINT", "sre:9090", "CAERUS_TLS", "false"));
        assertThat(fromSre.endpoint()).isEqualTo(CaerusSdk.DEFAULT_ENDPOINT);
        assertThat(fromSre.tls()).isTrue();
    }

    @Test
    void prefersAnExplicitEndpointOverTheEnvironment() {
        assertThat(resolve(DlsClientOptions.builder().apiKey("k").endpoint("explicito:1").build(),
                Map.of("CAERUS_DLS_ENDPOINT", "entorno:1")).endpoint()).isEqualTo("explicito:1");
    }

    @Test
    void refusesAnExplicitBlankEndpoint() {
        assertThatThrownBy(() -> new DlsClient(DlsClientOptions.builder().endpoint(" ").apiKey(KEY).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE", "1", "yes", "", "flase"})
    void letsNothingButFalseOrZeroDisableEncryption(String on) {
        assertThat(resolve(base().build(), Map.of("CAERUS_DLS_TLS", on)).tls()).isTrue();
    }

    @Test
    void anExplicitTlsOptionWinsOverTheEnvironment() {
        assertThat(resolve(base().tls(true).build(), Map.of("CAERUS_DLS_TLS", "false")).tls()).isTrue();
    }

    @Test
    void logsWithItsOwnPrefix() {
        assertThat(((StderrLogger) resolve(base().build(), Map.of()).logger()).prefix()).isEqualTo("[caerus-dls]");
    }
}

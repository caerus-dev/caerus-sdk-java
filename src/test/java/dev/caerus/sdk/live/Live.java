package dev.caerus.sdk.live;

import dev.caerus.sdk.CaerusSdk;
import dev.caerus.sdk.dls.DlsClient;
import dev.caerus.sdk.dls.DlsClientOptions;
import dev.caerus.sdk.sre.CaerusClient;
import dev.caerus.sdk.sre.CaerusClientOptions;
import org.junit.jupiter.api.Assumptions;

final class Live {

    private Live() {
    }

    static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    static String apiKey() {
        String key = System.getenv("CAERUS_API_KEY");
        Assumptions.assumeTrue(key != null && !key.isBlank(), "Falta CAERUS_API_KEY");
        return key;
    }

    static String endpoint() {
        return env("CAERUS_ENDPOINT", CaerusSdk.DEFAULT_ENDPOINT);
    }

    static boolean tls() {
        String raw = env("CAERUS_TLS", "true").toLowerCase(java.util.Locale.ROOT);
        return !(raw.equals("false") || raw.equals("0"));
    }

    static DlsClient dls() {
        return new DlsClient(DlsClientOptions.builder().apiKey(apiKey()).endpoint(endpoint()).tls(tls()).build());
    }

    static DlsClient dls(long timeoutMs) {
        return new DlsClient(DlsClientOptions.builder()
                .apiKey(apiKey()).endpoint(endpoint()).tls(tls()).timeoutMs(timeoutMs).build());
    }

    static CaerusClient sre() {
        return new CaerusClient(CaerusClientOptions.builder().apiKey(apiKey()).endpoint(endpoint()).tls(tls()).build());
    }

    static String key(String name) {
        return "verifjava_" + System.currentTimeMillis() + "_" + name;
    }

    static void log(String check, String detail) {
        System.out.println("[en vivo] " + check + " | " + detail);
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

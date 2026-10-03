package dev.caerus.sdk.internal;

public record ClientProfile(
        String clientName,
        String missingApiKeyMessage,
        String endpointVariable,
        String tlsVariable,
        String loggerPrefix) {

    public static final ClientProfile SRE = new ClientProfile(
            "CaerusClient",
            "CaerusClient requires an apiKey. Create one in the Caerus dashboard",
            "CAERUS_ENDPOINT",
            "CAERUS_TLS",
            "[caerus]");

    public static final ClientProfile DLS = new ClientProfile(
            "DlsClient",
            "DlsClient requires an apiKey",
            "CAERUS_DLS_ENDPOINT",
            "CAERUS_DLS_TLS",
            "[caerus-dls]");
}

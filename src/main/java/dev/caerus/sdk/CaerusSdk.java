package dev.caerus.sdk;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class CaerusSdk {

    public static final String VERSION = readVersion();

    public static final String DEFAULT_ENDPOINT = "caerus.dev.ar.sdk.apps.disilab.ar:443";

    public static final long DEFAULT_TIMEOUT_MS = 10_000L;

    private CaerusSdk() {
    }

    private static String readVersion() {
        try (InputStream in = CaerusSdk.class.getResourceAsStream("version.properties")) {
            if (in == null) {
                return "unknown";
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("version", "unknown");
        } catch (IOException e) {
            return "unknown";
        }
    }
}

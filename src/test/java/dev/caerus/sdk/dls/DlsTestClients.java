package dev.caerus.sdk.dls;

import dev.caerus.sdk.CaerusLogger;
import dev.caerus.sdk.support.FakeDlsEngine;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class DlsTestClients {

    private DlsTestClients() {
    }

    static DlsClient real(FakeDlsEngine engine) {
        return real(engine, null);
    }

    static DlsClient real(FakeDlsEngine engine, CaerusLogger logger) {
        return new DlsClient(DlsClient.resolve(DlsClientOptions.builder()
                .endpoint(engine.endpoint())
                .apiKey("test-key")
                .tls(false)
                .logger(logger)
                .build(), name -> null));
    }

    static final class Recorder implements CaerusLogger {

        private final List<String> messages = new CopyOnWriteArrayList<>();
        private final List<Object[]> details = new CopyOnWriteArrayList<>();

        @Override
        public void error(String message, Object... rest) {
            messages.add(message);
            details.add(rest);
        }

        List<String> messages() {
            return messages;
        }

        List<Object[]> details() {
            return details;
        }
    }
}

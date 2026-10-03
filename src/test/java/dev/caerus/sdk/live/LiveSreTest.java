package dev.caerus.sdk.live;

import dev.caerus.sdk.CaerusError;
import dev.caerus.sdk.sre.AuthenticationError;
import dev.caerus.sdk.sre.CaerusClient;
import dev.caerus.sdk.sre.CaerusClientOptions;
import dev.caerus.sdk.sre.ConfirmOptions;
import dev.caerus.sdk.sre.CreateResourceOptions;
import dev.caerus.sdk.sre.ListResourceHoldersOptions;
import dev.caerus.sdk.sre.OutOfStockError;
import dev.caerus.sdk.sre.Resource;
import dev.caerus.sdk.sre.ResourceHasActiveHoldsError;
import dev.caerus.sdk.sre.ResourceHolder;
import dev.caerus.sdk.sre.ResourceHolderPage;
import dev.caerus.sdk.sre.ResourceHolderStatus;
import dev.caerus.sdk.sre.ResourceNotFoundError;
import dev.caerus.sdk.sre.ResourcePage;
import dev.caerus.sdk.sre.TakeOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Tag("live")
class LiveSreTest {

    private static final String TEMPLATE = System.getenv("SRE_TEMPLATE");

    private static CaerusClient caerus;

    @BeforeAll
    static void connect() {
        Assumptions.assumeTrue(TEMPLATE != null && !TEMPLATE.isBlank(), "Falta SRE_TEMPLATE");
        caerus = Live.sre();
    }

    @AfterAll
    static void disconnect() {
        if (caerus != null) {
            caerus.close();
        }
    }

    private static TakeOptions.Builder idem() {
        return TakeOptions.builder().idempotencyKey(UUID.randomUUID().toString());
    }

    @Test
    void theWholeLifeOfAPooledResource() {
        String key = Live.key("pool");
        String group = Live.key("grupo");

        Resource created = caerus.createMultiple(TEMPLATE, key, 3, CreateResourceOptions.builder()
                .groupKey(group)
                .metadata(Map.of("zona", "platea"))
                .build());
        Live.log("crea el recurso", created.key() + " " + created.availableAmount() + " " + created.groupKey());
        assertThat(created.availableAmount()).isEqualTo(3);
        assertThat(created.groupKey()).contains(group);

        try {
            String idempotencyKey = UUID.randomUUID().toString();
            ResourceHolder first = caerus.pooled(key).takeMany(2, TakeOptions.builder()
                    .idempotencyKey(idempotencyKey)
                    .metadata(Map.of("orden", "A-1", "items", 2))
                    .build());
            ResourceHolder replay = caerus.pooled(key).takeMany(2, TakeOptions.builder()
                    .idempotencyKey(idempotencyKey)
                    .build());
            Live.log("la misma clave devuelve el mismo holder", first.id() + " / " + replay.id());
            assertThat(first.status()).isEqualTo(ResourceHolderStatus.PENDING);
            assertThat(replay.id()).isEqualTo(first.id());
            assertThat(caerus.getResource(key).availableAmount()).isEqualTo(1);

            Instant expiresAt = first.expiresAt();
            Live.log("el vencimiento es una fecha razonable", expiresAt.toString());
            assertThat(expiresAt).isAfter(Instant.now()).isBefore(Instant.now().plus(Duration.ofDays(2)));

            ResourceHolder extended = caerus.extend(first.id(), 1500);
            Live.log("extend redondea a segundos hacia arriba", Duration.between(expiresAt, extended.expiresAt()).toString());
            assertThat(Duration.between(expiresAt, extended.expiresAt())).isEqualTo(Duration.ofSeconds(2));

            ResourceHolder second = caerus.pooled(key).take(idem().build());
            Throwable outOfStock = catchThrowable(() -> caerus.pooled(key).take(idem().build()));
            Live.log("sin stock llega OutOfStockError", String.valueOf(outOfStock));
            assertThat(outOfStock).isInstanceOf(OutOfStockError.class);
            assertThat(((CaerusError) outOfStock).reason()).contains("OUT_OF_STOCK");

            ResourceHolder confirmed = caerus.confirm(first.id(), ConfirmOptions.builder()
                    .metadata(Map.of("pago", "pay_9"))
                    .build());
            Live.log("confirm devuelve CONFIRMED", confirmed.status().name());
            assertThat(confirmed.status()).isEqualTo(ResourceHolderStatus.CONFIRMED);

            Throwable busy = catchThrowable(() -> caerus.deleteResource(key));
            Live.log("no deja borrar con holders activos", String.valueOf(busy));
            assertThat(busy).isInstanceOf(ResourceHasActiveHoldsError.class);

            caerus.release(second.id());
            assertThat(caerus.getResourceHolder(second.id()).status()).isEqualTo(ResourceHolderStatus.RELEASED);
            Throwable replayReleased = catchThrowable(() -> caerus.extend(second.id(), 1000));
            Live.log("un holder liberado no se puede extender", String.valueOf(replayReleased));
            assertThat(replayReleased).isInstanceOf(CaerusError.class);

            ResourceHolderPage holders = caerus.listResourceHolders(ListResourceHoldersOptions.builder()
                    .resourceKey(key)
                    .build());
            Live.log("lista los holders del recurso", String.valueOf(holders.holders().size()));
            assertThat(holders.holders()).extracting(ResourceHolder::id).contains(first.id(), second.id());

            ResourcePage page = waitForGroup(group);
            Live.log("el grupo encuentra el recurso", String.valueOf(page.resources().size()));
            assertThat(page.resources()).extracting(Resource::key).contains(key);

            Resource updated = caerus.updateResource(key, 4,
                    dev.caerus.sdk.sre.UpdateResourceOptions.builder().idempotencyKey(UUID.randomUUID().toString()).build());
            Live.log("updateResource suma stock", String.valueOf(updated.availableAmount()));
            assertThat(updated.availableAmount()).isEqualTo(caerus.getResource(key).availableAmount());
        } finally {
            try {
                caerus.deleteResource(key);
            } catch (CaerusError ignored) {
            }
        }
    }

    @Test
    void aResourceThatDoesNotExistIsNotFound() {
        Throwable error = catchThrowable(() -> caerus.getResource(Live.key("no_existe")));

        Live.log("un recurso inexistente da ResourceNotFoundError", String.valueOf(error));
        assertThat(error).isInstanceOf(ResourceNotFoundError.class);
        assertThat(((CaerusError) error).requestId()).isPresent();
    }

    @Test
    void aWrongApiKeyIsAnAuthenticationError() {
        try (CaerusClient stranger = new CaerusClient(CaerusClientOptions.builder()
                .apiKey("caer_dev_no_existe_" + UUID.randomUUID())
                .endpoint(Live.endpoint())
                .tls(Live.tls())
                .build())) {
            Throwable error = catchThrowable(() -> stranger.getResource("x"));

            Live.log("una API Key que no existe da error de autenticacion", String.valueOf(error));
            assertThat(error).isInstanceOf(AuthenticationError.class);
        }
    }

    private static ResourcePage waitForGroup(String group) {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        ResourcePage page = caerus.getResourcesByGroup(group);
        while (page.resources().isEmpty() && System.nanoTime() < deadline) {
            Live.sleep(500);
            page = caerus.getResourcesByGroup(group);
        }
        return page;
    }
}

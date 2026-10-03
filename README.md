# Caerus SDK para Java

El cliente oficial de Caerus para Java. Reserva stock limitado (asientos, cupos, turnos,
inventario) y coordina locks distribuidos sin escribir la lógica de concurrencia que evita
que dos clientes compren o modifiquen lo mismo al mismo tiempo.

```java
ResourceHolder holder = caerus.unitary("butaca_A12").take();
chargeCard(4500);
caerus.confirm(holder.id());
```

Es el mismo producto que [`@caerus-dev/sdk`](https://github.com/caerus-dev/caerus-sdk-ts):
mismos métodos, mismos errores y mismos bordes. Lo que cambia es lo que tiene que cambiar
para que se sienta Java.

**Corre en tu servidor.** La API Key autentica cada llamada e identifica tu entorno, así
que nunca tiene que llegar a un cliente o a un navegador.

---

## Instalación

Requiere Java 17 o superior.

```xml
<dependency>
    <groupId>io.github.caerus-dev</groupId>
    <artifactId>caerus-sdk</artifactId>
    <version>0.1.0</version>
</dependency>
```

Trae gRPC (con Netty incluido), protobuf y Gson. Nada de eso aparece en la API pública: un
test del build lo hace cumplir.

---

## Conexión

```java
CaerusClient caerus = new CaerusClient(CaerusClientOptions.builder()
        .apiKey(System.getenv("CAERUS_API_KEY"))
        .build());
```

Esa es toda la configuración. La API Key identifica el entorno, así que el cliente ya sabe
adónde conectarse.

Un cliente por proceso: el canal multiplexa todas las llamadas. Implementa `AutoCloseable`;
`close()` es idempotente y después de cerrar toda llamada falla con un `CaerusError`.

### Apuntar a otro motor

```java
CaerusClient caerus = new CaerusClient(CaerusClientOptions.builder()
        .endpoint("localhost:9090")
        .apiKey(apiKey)
        .tls(false)
        .build());
```

| Opción | Default | Regla |
|---|---|---|
| `apiKey` | — | Obligatoria. Nunca se lee del entorno ni aparece en `toString()` |
| `endpoint` | `CAERUS_ENDPOINT`, si no `caerus.dev.ar.sdk.apps.disilab.ar:443` | `host:puerto`, sin esquema |
| `tls` | `CAERUS_TLS`, si no `true` | En la variable solo `false` o `0` lo apagan |
| `timeoutMs` | `10000` | Deadline de cada llamada |
| `logger` | `System.err` con prefijo `[caerus]` | Solo para lo que no conviene lanzar |

El `DlsClient` lee `CAERUS_DLS_ENDPOINT` y `CAERUS_DLS_TLS`, y su prefijo es `[caerus-dls]`.

---

## Shared Resource Engine

```java
caerus.createMultiple("entrada", "general", 500, CreateResourceOptions.builder()
        .groupKey("funcion_20")
        .build());

ResourceHolder holder = caerus.pooled("general").takeMany(2, TakeOptions.builder()
        .idempotencyKey(orderId)
        .ttlSeconds(600)
        .metadata(Map.of("orderId", orderId))
        .build());

try {
    pay(holder);
    caerus.confirm(holder.id(), ConfirmOptions.builder().metadata(Map.of("paymentId", paymentId)).build());
} catch (PaymentFailed e) {
    caerus.release(holder.id());
}
```

| Método | Qué hace |
|---|---|
| `createUnitary(template, key)` / `createMultiple(template, key, amount)` | Declara un recurso |
| `updateResource(key, delta)` | Suma o resta stock |
| `deleteResource(key)` | Lo borra; falla si alguien lo tiene tomado |
| `unitary(key).take()` | Toma la única unidad |
| `pooled(key).take()` / `.takeMany(n)` | Toma una o varias |
| `confirm(id)` | Las deja tomadas para siempre |
| `release(id)` | Las devuelve |
| `extend(id, extraMs)` | Corre el vencimiento, en **milisegundos** |
| `getResourceHolder(id)` | Lee un holder; es el único que devuelve un `EXPIRED` sin lanzar |
| `getResource(key)` / `getResourcesByGroup(group)` / `listResourceHolders()` | Consultas |

Un `take` o un `extend` que vuelven en un estado que no sirve (por ejemplo, una clave de
idempotencia cuyo holder ya terminó) lanzan `ConflictError`: nunca se devuelve un holder que
no podés usar. Un estado que el SDK no conoce también lanza: no se adivina.

`QUEUED` es real: la plantilla encola y no había stock. El SDK no espera a que se asigne;
devuelve el holder `QUEUED` y decidís vos.

## Distributed Lock Service

```java
DlsClient dls = new DlsClient(DlsClientOptions.builder().apiKey(apiKey).build());

String result = dls.withTransaction(tx -> {
    LockHolder lock = tx.acquireLock("cuentas", "cuenta-42", LockMode.EXCLUSIVE,
            AcquireLockOptions.builder().idempotencyKey(requestId).timeoutMs(30_000L).build());
    return transfer(lock.fencingToken().getAsLong());
}, TransactionOptions.builder().timeoutMs(20_000L).build());
```

`withTransaction` abre la transacción, la renueva sola mientras el callback trabaja y suelta
todos los locks al final, salga bien o salga mal. Si la renovación falla sin remedio, la
transacción se da por perdida: se cancela `tx.signal()`, se avisa a `onTransactionLost` y la
llamada lanza ese error aunque el callback haya terminado bien. Las excepciones checked del
callback salen tal cual, sin envolver.

Un lock denegado nunca vuelve como valor: es `LockDeniedError`. Una espera en la cola se
corta con un `AbortSignal` (`AbortController.abort()`), y `onQueued` avisa una sola vez.

## Webhooks

```java
CaerusEvent event = caerus.webhooks().constructEvent(rawBody, request.getHeader("Caerus-Signature"), secret);

if (event.data() instanceof ResourceTakenData taken) {
    log.info("holder {} tomó {} unidades", taken.holderId(), taken.amount());
}
```

Verifica la firma HMAC-SHA256 contra el cuerpo crudo, en tiempo constante, con 5 minutos de
tolerancia. Cada tipo de evento tiene su `record`; un evento que el SDK todavía no conoce
llega como `UnknownEventData` en vez de romper al consumidor.

## Errores

Todo lo que lanza el SDK extiende `CaerusError`, que es unchecked: un solo
`catch (CaerusError e)` los junta a todos, y `e.code()` sirve para un `switch`.
`e.reason()` trae el código del motor (`OUT_OF_STOCK`, `DEADLOCK_DETECTED`...) y
`e.requestId()` el identificador para pedir soporte. Detalle en
[docs/errores.md](docs/errores.md).

El SDK no reintenta nada. Reintentar es decisión de quien llama, con clave de idempotencia.

## Testing sin Caerus

`InMemoryCaerusClient` e `InMemoryDlsClient` implementan `SharedResourceApi` y `DlsApi`, así
que el código bajo test no se entera.

```java
InMemoryCaerusClient caerus = new InMemoryCaerusClient(MockOptions.builder()
        .resources(List.of(MockResourceSeed.of("butaca_A12", 1)))
        .build());

caerus.failNext(MockMethod.CONFIRM, new ConflictError("pago rechazado"));
caerus.advanceTime(600);
```

El tiempo no pasa solo: lo mueve el test. Son thread-safe.

---

## Desarrollo

```bash
mvn verify
```

Genera el cliente gRPC desde `proto/`, compila y corre los tests contra un motor gRPC falso
en `127.0.0.1`. La verificación contra un motor real está en
[docs/verificacion-en-vivo.md](docs/verificacion-en-vivo.md).

## Licencia

MIT

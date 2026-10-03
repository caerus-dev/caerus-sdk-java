# Verificación contra un motor real

Los tests del build corren contra un motor gRPC falso: son rápidos y no necesitan
infraestructura. Lo que no pueden probar es que el motor de verdad se comporte como el SDK
espera. Las dos veces que el SDK de TypeScript tuvo un defecto de contrato, los tests contra
el mock pasaban.

```bash
CAERUS_API_KEY=tu_clave mvn test -Plive
```

Los tests en vivo llevan el tag `live` y quedan fuera de `mvn verify`. Sin `CAERUS_API_KEY`
se saltean. El endpoint sale de `CAERUS_ENDPOINT` (por defecto el motor hospedado) y TLS de
`CAERUS_TLS` (por defecto prendido).

Detrás de un antivirus que intercepta HTTPS (Avast), la JVM tiene que confiar en los
certificados de Windows:

```bash
mvn test -Plive -DargLine="-Djavax.net.ssl.trustStoreType=Windows-ROOT"
```

## DLS (`LiveDlsTest`)

| Escenario | Plantilla | Qué demuestra |
|---|---|---|
| `sharedReadAndLockStatus` | `READ_WRITE` | Dos lectores entran a la vez, `getLockStatus` los lista, un escritor no entra, soltar uno deja al otro |
| `deniedLockInAFailNamespace` | `FAIL` | Un lock denegado llega como `LockDeniedError` con `LOCK_DENIED` |
| `twoWayDeadlockWithKillPriority` | `QUEUE` | Una sola víctima, la más joven, con `DEADLOCK_DETECTED`, en menos de 10 s |
| `triangularDeadlock` | `QUEUE` | Se rompe el ciclo sin matar a las tres |
| `deadlockWithAlertStrategyAbortsNobody` | `ALERT` | No se aborta a nadie |
| `transactionStatusWithALockInTheQueueAndOnQueued` | `QUEUE` | `getTransactionStatus` responde con un lock en cola; `onQueued` avisa una vez y antes de conceder |
| `aQueuedLockWithoutItsOwnTimeoutUsesTheClientDeadline` | `QUEUE` | Sin `timeoutMs` propio, la espera corta con el deadline del cliente |
| `aClosedTransactionCannotTakeLocks` | `QUEUE` | Una transacción cerrada da `TRANSACTION_NOT_ACTIVE` |
| `withTransactionRenewsALongJob` | `QUEUE` | Una transacción de 4 s sigue viva a los 7 s y su vencimiento avanza |
| `fencingTokensGrowAcrossHandoffs` | `QUEUE` | Los fencing tokens crecen de un dueño al siguiente |
| `aWrongApiKeyIsAnAuthenticationError` | — | Una API Key que no existe da `DlsAuthenticationError` |
| `anEngineErrorCarriesARequestId` | — | El error trae el `requestId` |

Los namespaces se eligen por variable de entorno:

| Variable | Default | `lock_type` | `conflict_resolution` | `deadlock_resolution_strategy` |
|---|---|---|---|---|
| `DLS_NS_FAIL` | `cuenta` | `EXCLUSIVE` | `FAIL` | `KILL_PRIORITY` |
| `DLS_NS_QUEUE` | `cuenta_cola` | `EXCLUSIVE` | `QUEUE` | `KILL_PRIORITY` |
| `DLS_NS_ALERT` | `cuenta_alerta` | `EXCLUSIVE` | `QUEUE` | `ALERT` |
| `DLS_NS_READ_WRITE` | `archivo` | `READ_WRITE` | `QUEUE` | `KILL_PRIORITY` |

Cada `acquireLock` manda una clave de idempotencia, así que también sirven plantillas con
fencing token obligatorio. Una plantilla `READ_WRITE` con `QUEUE` sirve como `DLS_NS_QUEUE`:
los escenarios piden `EXCLUSIVE`.

## SRE (`LiveSreTest`)

Necesita `SRE_TEMPLATE` con el nombre de una plantilla de recursos del mismo entorno que la
key. Sin ella se saltea.

| Escenario | Qué demuestra |
|---|---|
| `theWholeLifeOfAPooledResource` | Crear, tomar con idempotencia y metadata, repetir la clave, extender (redondeo a segundos), quedarse sin stock, confirmar, no poder borrar con holders activos, liberar, listar, buscar por grupo, actualizar stock y borrar |
| `aResourceThatDoesNotExistIsNotFound` | `ResourceNotFoundError` con `requestId` |
| `aWrongApiKeyIsAnAuthenticationError` | `AuthenticationError` |

`getResourcesByGroup` lee de PostgreSQL, que se sincroniza por write-behind: el test
reintenta hasta 15 s antes de dar por perdido un recurso recién creado.

## No medir tiempos con otra corrida en marcha

Dos verificaciones contra el mismo motor se reparten el motor y los tiempos cambian. Los
umbrales de tiempo de estos tests asumen que corren solos.

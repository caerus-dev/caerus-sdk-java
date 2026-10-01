# Paridad con el SDK de TypeScript

Este SDK copia el comportamiento de `@caerus-dev/sdk` 0.5.0 (`caerus-sdk-ts@367e0d1`). Lo que
sigue es lo que cambia, y por qué.

## Lo que cambia por ser Java

| TypeScript | Java | Por qué |
|---|---|---|
| `Promise<T>` | `T`, bloqueante | Etapa 1. La API asíncrona (`CompletableFuture`) llega antes de 1.0 sobre el mismo núcleo |
| Objeto de opciones literal | `Options.builder()...build()` | Java no tiene parámetros con nombre. Cada método sin opciones tiene su sobrecarga sin ellas |
| `field?: T` | `Optional<T>` / `OptionalLong` | La ausencia queda en el tipo |
| `Date` | `Instant` | |
| `number` (fencing token) | `long` | Es el `czxid` de ZooKeeper: no entra en un `int` |
| `AbortSignal` de la plataforma | `dev.caerus.sdk.AbortController` / `AbortSignal` | Java no tiene uno estándar; mismos nombres |
| `onTransactionLost: (error: Error) => void` | `Consumer<DlsError>` | |
| Callback `async (tx) => T` | `TransactionCallback<T, E>` | Una excepción checked del callback sale tal cual, sin envolver |
| `CaerusError extends Error` | `CaerusError extends RuntimeException` | Unchecked: un solo `catch` |
| `code: 'CONFLICT'` (string) | `ErrorCode.CONFLICT` (enum) | Mismos nombres |
| `TypeError` en la configuración | `IllegalArgumentException` | Lo equivalente en Java |
| `InMemory*` de un hilo | `InMemory*` thread-safe | En Java los tests usan hilos de verdad |
| `MockMethod` `'takeMany'` | `MockMethod.TAKE_MANY` | Convención de enums |
| `close()` solo en `CaerusClient` y `DlsClient` | `SharedResourceApi` y `DlsApi` extienden `AutoCloseable` | `try-with-resources` |
| Renovación con `setInterval` | `ScheduledExecutorService` del cliente | Java 17 no tiene virtual threads. Un hilo daemon por cliente, que se apaga en `close()`. Las renovaciones no se superponen |
| `CaerusEvent` = JSON parseado | `CaerusEvent` con un `record` por tipo de evento y `raw()` con todo el sobre | `UnknownEventData` para tipos nuevos |

## Inconsistencias del SDK de TS: qué se decidió (Thomas, 30/09/2026)

| # | En TS | En Java |
|---|---|---|
| 1 | SRE lee `CAERUS_ENDPOINT`/`CAERUS_TLS`, DLS lee `CAERUS_DLS_*`; prefijos `[caerus]` y `[caerus-dls]` | Igual |
| 2 | Usar un contexto cerrado lanza un `Error` pelado | `IllegalStateException`, lo equivalente en Java |
| 3 | `InMemoryDlsClient.releaseLock` de un lock inexistente lanza `DlsNotFoundError` | No hace nada, como el motor (`DistributedLockEngineService.releaseLock` responde OK aunque no haya nada que soltar). Se propone el mismo cambio para TS |
| 4 | Sufijo en castellano en el mensaje de todo error con `requestId` | Igual |
| 5 | `docs/webhooks.md` dice que los errores de webhooks extienden `Error` | Extienden `CaerusError` con `VALIDATION`, como el código |
| 6 | `LockAcquisitionCancelledError` con `code` `UNKNOWN` | Igual |
| 7 | `renewTransaction` devuelve solo el `transactionId` | Igual |
| 8 | `webhooks` solo en `CaerusClient` (y el mock del SRE) | Igual |

## Otras diferencias chicas, a favor de parecerse al motor

- `InMemoryDlsClient.getLockStatus` devuelve el fencing token real de cada holder; el de TS
  devuelve `0`, que el cliente real nunca devolvería.
- `InMemoryDlsClient.acquireLock` con una señal ya cancelada lanza `DlsError` ("AcquireLock
  aborted by user"), como el cliente real; el de TS lanza un `Error` pelado.
- `TransactionStatusResponse.abortReason()` vacío cuando el motor manda `""`; TS devuelve
  `""`.
- `constructEvent` con un cuerpo firmado que es JSON pero no un objeto lanza
  `CaerusWebhookPayloadError`; TS lo devuelve como si fuera un evento.
- El mensaje de "estado desconocido" dice `Update io.github.caerus-dev:caerus-sdk.` en vez de
  `@caerus-dev/sdk`, y `docUrl()` apunta a la documentación de este repo.

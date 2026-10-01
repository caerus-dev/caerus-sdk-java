# Errores

Todo lo que lanza el SDK extiende `CaerusError`, que extiende `RuntimeException`. Un solo
`catch` los junta a todos, y cada uno lleva un `code()` para usar en un `switch` sin
depender de `instanceof`.

```java
try {
    caerus.pooled("curso-3").takeMany(1, TakeOptions.builder().idempotencyKey(k).build());
} catch (ConflictError e) {
    return "no hay lugar";
}
```

Las excepciones de configuración (una API Key vacía, un `timeoutMs` negativo) son
`IllegalArgumentException` y salen al construir el cliente, no en la primera llamada. Usar
un `TransactionContext` después de que terminó `withTransaction` es
`IllegalStateException`.

## El mapeo

Refleja lo que hace el servidor en `GrpcGlobalExceptionHandler`, no una convención general
de gRPC.

| Estado gRPC | Error del SDK | `code()` | Cuándo |
|---|---|---|---|
| `NOT_FOUND` | `ResourceNotFoundError` | `RESOURCE_NOT_FOUND` | El recurso, la plantilla o el holder no existen |
| `FAILED_PRECONDITION` | `ConflictError` | `CONFLICT` | El estado no permite la operación |
| `INVALID_ARGUMENT` | `ValidationError` | `VALIDATION` | El pedido está mal formado |
| `UNAUTHENTICATED` | `AuthenticationError` | `AUTHENTICATION` | API Key ausente, desconocida o revocada |
| `DEADLINE_EXCEEDED` | `TimeoutError` | `TIMEOUT` | La llamada pasó su deadline |
| cualquier otro | `CaerusError` | `UNKNOWN` | Incluye `INTERNAL` |

## `ConflictError` junta varias cosas, y se distinguen

| Subclase | `reason()` | Cuándo |
|---|---|---|
| `OutOfStockError` | `OUT_OF_STOCK` | No quedan unidades libres |
| `HolderNotActiveError` | `HOLDER_NOT_ACTIVE` | El holder está `RELEASED`, `CONFIRMED` o `EXPIRED` |
| `ResourceHasActiveHoldsError` | `RESOURCE_HAS_ACTIVE_HOLDS` | No se puede borrar: alguien lo tiene tomado |
| `ResourceHasQueuedRequestsError` | `RESOURCE_HAS_QUEUED_REQUESTS` | No se puede borrar: hay gente en la cola |

Las cuatro siguen siendo `ConflictError`. Cualquier error trae `reason()` con el código tal
cual lo mandó el motor (`Optional` vacío si no mandó ninguno). El mensaje está escrito para
personas y puede cambiar; `reason()` es contrato.

## Errores del DLS

Todos extienden `DlsError`, que extiende `CaerusError`. Primero decide el `reason`, después
el estado gRPC.

| Subclase | `reason()` | Estado gRPC sin `reason` | Cuándo |
|---|---|---|---|
| `DeadlockAbortedError` | `DEADLOCK_DETECTED` | — | El detector eligió esta transacción como víctima |
| `LockDeniedError` | `LOCK_DENIED` | — | El lock lo tiene otro y la plantilla no encola |
| `LockAlreadyHeldError` | `LOCK_ALREADY_HELD_EXCLUSIVELY` | `ALREADY_EXISTS` | La transacción ya tiene ese lock |
| `TransactionNotActiveError` | `TRANSACTION_NOT_ACTIVE` | — | La transacción terminó o venció |
| `LockModeMismatchError` | `LOCK_MODE_MISMATCH` | — | El modo no corresponde a la plantilla |
| `LockAcquisitionCancelledError` | `LOCK_ACQUISITION_CANCELLED` | `CANCELLED` | Se canceló la espera |
| `DlsConflictError` | — | `ABORTED`, `FAILED_PRECONDITION` | Otro conflicto |
| `DlsNotFoundError` | — | `NOT_FOUND` | La transacción o el lock no existen |
| `DlsValidationError` | — | `INVALID_ARGUMENT` | Pedido mal formado |
| `DlsAuthenticationError` | — | `UNAUTHENTICATED` | API Key inválida |
| `DlsTimeoutError` | — | `DEADLINE_EXCEEDED` | Se pasó el deadline |
| `DlsError` | — | otro | `UNKNOWN` |

El motor manda un `DENIED` como respuesta exitosa del stream, no como error gRPC. El SDK no
lo devuelve como valor: `acquireLock` lanza `LockDeniedError` con `reason()` `LOCK_DENIED`.

Un `acquireLock` sin `timeoutMs` propio usa el deadline del cliente (10 s por defecto),
también dentro de `withTransaction`. Un lock en cola más tiempo que eso termina en
`DlsTimeoutError`.

## `requestId` y soporte

Cada llamada manda un `x-request-id` (`req_` + UUID) y el motor lo devuelve en los trailers.
Todo error lo trae en `requestId()` y lo agrega al mensaje:

```java
try {
    caerus.unitary("butaca_1").take();
} catch (CaerusError e) {
    log.error("Caerus {} ({}) request {}", e.code(), e.reason().orElse("-"), e.requestId().orElse("-"));
}
```

Con ese ID el equipo encuentra la traza exacta en los logs del motor sin que tengas que
compartir claves ni datos. `docUrl()` apunta a este documento.

## `TimeoutError` no significa que no pasó nada

Significa que la llamada pasó su deadline. Si el servidor llegó a hacer el trabajo, no se
sabe. Reintentar con la misma clave de idempotencia devuelve el holder que ya existía; mirá
su `status()`, porque puede volver terminado.

## El SDK no reintenta

Ninguna operación se reintenta sola, ni siquiera las lecturas. En un sistema de reservas los
reintentos silenciosos son exactamente cómo se entrega la misma butaca dos veces.

## Llamadas interrumpidas

Si el hilo que espera una llamada se interrumpe, el SDK cancela la llamada gRPC, vuelve a
marcar la interrupción del hilo y lanza `CaerusError` (o `DlsError`) con código `UNKNOWN`.

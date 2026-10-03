# El contrato

`sre_service.proto` y `dls_service.proto` son **copias**. Los originales viven en
`caerus-back`, en `grpc-contracts/src/main/proto/`, y de ahí se construye el motor. Estas
copias existen solo para generar el cliente.

## Por qué una copia

`caerus-back` es privado y este repositorio es público: un repositorio público que nadie
puede compilar no le sirve a nadie. La copia es byte a byte igual al original, así que se
puede comparar con un checksum.

## De dónde salió esta copia

| | SRE | DLS |
|---|---|---|
| Repositorio | `caerus-dev/caerus-back` | `caerus-dev/caerus-back` |
| Ruta | `grpc-contracts/src/main/proto/sre_service.proto` | `grpc-contracts/src/main/proto/dls_service.proto` |
| Commit | `d37216d12b6a25ec411c08ec69cf9c23d0148fbf` | `d37216d12b6a25ec411c08ec69cf9c23d0148fbf` |
| SHA-256 | `632ec22f01e56ebb537caec43fa189351e0b797212cb5b166a7f00d23439d0f7` | `b0f1af5a4ae3abf85e8b4ca0ee0d6b82db6a7b45d970df6b907107a01f199928` |

> `.gitattributes` marca estos archivos `-text` para que git nunca les cambie el fin de
> línea. Sin eso, una copia hecha en Windows vuelve con CRLF, los checksums dejan de
> coincidir y la comparación avisa de un cambio que no existe.

## Por qué el paquete Java no es el del `.proto`

El `.proto` declara `java_package = "dev.caerus.proto.sre"` (y `.dls`), que es el paquete
que usa el motor. El build copia los archivos a `target/proto-sdk`, cambia ese paquete por
`dev.caerus.sdk.internal.proto.*` y genera desde ahí. Así las clases generadas quedan
marcadas como internas y no chocan con las del motor si alguien tiene las dos en el
classpath. Las copias de este directorio no se tocan.

## Ver si sigue vigente

Con un checkout de `caerus-back` al lado de este:

```bash
sha256sum proto/sre_service.proto
git -C ../caerus-back show HEAD:grpc-contracts/src/main/proto/sre_service.proto | sha256sum
```

Mismo hash, nada que hacer. Distinto, el contrato se movió y esta copia quedó vieja.
Comparar siempre lo que guarda git, o ignorando los fines de línea (`tr -d '\r'`): una
diferencia de CRLF no es un cambio de contrato.

## Actualizarla

Copiar el archivo desde `caerus-back`, correr `mvn verify` y dejar que el compilador
encuentre lo que se rompió. Después actualizar el commit y el checksum de la tabla:
`GeneratedContractTest` falla si la tabla no coincide con los archivos.

El SDK se versiona aparte del motor, así que un cambio de contrato obliga a decidir qué le
hace a la API pública: un campo opcional nuevo es una versión menor, una firma que cambia es
un cambio que rompe.

## Una advertencia

El build no puede decir que esta copia está vieja: genera el cliente con lo que haya en
este directorio y compila contra eso, igual de contento con un contrato viejo que con uno
actual. Lo que detecta la diferencia es la verificación contra el motor real, o la
comparación de arriba.

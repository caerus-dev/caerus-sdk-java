# Publicar en Maven Central

Se publica desde GitHub Actions al crear un tag `vX.Y.Z`
(`.github/workflows/release.yml`). No se publica desde una máquina Windows: el plugin de
Sonatype arma el bundle con `\` como separador dentro del zip y Central espera `/`.

## Lo que se hace una sola vez

### 1. Namespace

`io.github.caerus-dev` está verificado en central.sonatype.com (03/10/2026), en la cuenta en
la que Thomas entró con **Google**. Cada forma de entrar es una cuenta distinta: el token
tiene que salir de esa.

### 2. Token de publicación

central.sonatype.com → usuario → **View Account** → **Generate User Token**. Da un usuario y
una clave que no son los de la cuenta.

### 3. Clave GPG

Central exige que cada archivo vaya firmado. Git for Windows trae `gpg`, así que alcanza con
Git Bash:

```bash
gpg --full-generate-key
```

RSA, 4096 bits, con vencimiento (2 años está bien), nombre `Caerus` y un mail del equipo.
Después:

```bash
gpg --list-secret-keys --keyid-format=long
```

El ID es lo que va después de `rsa4096/` en la línea `sec`. Publicarla para que Central pueda
comprobar las firmas:

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys ID_DE_LA_CLAVE
gpg --keyserver keys.openpgp.org --send-keys ID_DE_LA_CLAVE
```

### 4. Secrets del repositorio

En `caerus-dev/caerus-sdk-java` → Settings → Secrets and variables → Actions, o con `gh`
(pide el valor sin mostrarlo):

| Secret | Valor |
|---|---|
| `CENTRAL_USERNAME` | El usuario del token |
| `CENTRAL_PASSWORD` | La clave del token |
| `GPG_PRIVATE_KEY` | `gpg --armor --export-secret-keys ID_DE_LA_CLAVE` |
| `GPG_PASSPHRASE` | La frase de la clave GPG |

```bash
gh secret set CENTRAL_USERNAME --repo caerus-dev/caerus-sdk-java
gh secret set CENTRAL_PASSWORD --repo caerus-dev/caerus-sdk-java
gh secret set GPG_PASSPHRASE --repo caerus-dev/caerus-sdk-java
gpg --armor --export-secret-keys ID_DE_LA_CLAVE | gh secret set GPG_PRIVATE_KEY --repo caerus-dev/caerus-sdk-java
```

La clave privada no se guarda en ningún archivo ni se pega en un chat.

## Cada versión

1. Que `main` tenga lo que se publica y el CI esté verde.
2. Crear y subir el tag:

   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```

3. El workflow pone esa versión en el `pom.xml` (no se commitea), corre los tests, firma y
   sube el bundle. Central lo valida.
4. En central.sonatype.com → **Deployments** aparece como **Validated**. Revisarlo y apretar
   **Publish**. A partir de ahí no se puede borrar ni pisar esa versión.
5. Aparece en Maven Central en unos 30 minutos.

Si la validación falla, el deployment queda en el portal con el motivo y se puede descartar
con **Drop**; nada se publicó.

## Qué va en el bundle

El jar, el `.pom`, el jar de fuentes, el de javadoc (solo con los paquetes públicos; el
código no tiene Javadoc, así que es la lista de clases y firmas), las firmas `.asc` y los
checksums. Se puede armar localmente sin firmar ni subir para revisarlo con
`mvn -Prelease package -Dgpg.skip`.

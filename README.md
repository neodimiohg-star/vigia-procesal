# Vigía Procesal

App Android para seguir procesos judiciales colombianos por número de radicación. Consulta la
**Consulta de Procesos Nacional Unificada (CPNU)** de la Rama Judicial, revisa automáticamente
cada cierto tiempo y avisa con una notificación cuando un proceso tiene actuaciones nuevas.

## Qué hace

- **Agregar procesos** con el radicado de 23 dígitos (con o sin guiones). Se pueden pegar varios
  radicados a la vez, uno por línea.
- **Revisión automática** en segundo plano cada 3, 6, 12 o 24 horas (por defecto, cada 6), con
  opción de revisar solo con Wi-Fi.
- **Aviso** en el teléfono cuando aparece una actuación nueva; al tocarlo se abre el proceso.
- **Detalle del proceso**: despacho, ponente, tipo/clase/subclase, ubicación, sujetos procesales y
  la lista completa de actuaciones (fecha, actuación, anotación, términos y fecha de registro). Las
  nuevas aparecen resaltadas.
- Las actuaciones quedan guardadas en el teléfono para consultarlas sin conexión.
- Compartir la lista de radicados seguidos.

## Instalar

1. En el teléfono, abre la página **Releases** de este repositorio y descarga el último
   `VigiaProcesal-vN.apk`.
2. Ábrelo. Android pedirá permitir la instalación de apps desde el navegador o el gestor de
   archivos: acéptalo solo para esta instalación.
3. Al abrir la app, permite las notificaciones.

Cada vez que se sube un cambio a la rama `main`, GitHub Actions compila un APK nuevo y lo publica
en Releases. Las versiones nuevas se instalan encima de la anterior sin perder los procesos.

## Cómo detecta cambios

Para cada proceso guarda los identificadores (`idRegActuacion`) de las actuaciones ya conocidas.
En cada revisión trae la primera página de actuaciones; si el total cambió o aparece un
identificador desconocido, descarga la lista completa y marca como nuevas las que no conocía.
Las consultas se hacen de una en una, con una pausa entre ellas, porque el servidor de la Rama es
lento y bloquea las ráfagas de consultas.

API usada (pública, sin clave): `https://consultaprocesos.ramajudicial.gov.co:448/api/v2`

- `Procesos/Consulta/NumeroRadicacion?numero={23 dígitos}&SoloActivos=false&pagina=1`
- `Proceso/Detalle/{idProceso}`
- `Proceso/Actuaciones/{idProceso}?pagina={n}`
- `Proceso/Sujetos/{idProceso}?pagina=1`

## Límites

- La CPNU **no es una fuente oficial de notificación**. La app no reemplaza la revisión de estados
  electrónicos, traslados y notificaciones del despacho; antes de actuar sobre un término hay que
  verificar en el portal de la Rama Judicial.
- Los procesos reservados no muestran actuaciones en la CPNU.
- Los despachos que no registran sus actuaciones en la CPNU (por ejemplo, los que publican solo en
  otros sistemas) no generarán avisos.
- Android decide la hora exacta de las revisiones en segundo plano. En algunos teléfonos (Xiaomi,
  Huawei, Oppo, Samsung) conviene quitar la optimización de batería para la app; hay un acceso
  directo en *Ajustes*.

## Compilar

El APK se compila en GitHub Actions (`.github/workflows/compilar-apk.yml`) con Gradle 8.10.2,
Android Gradle Plugin 8.7.3, Kotlin 2.1 y Jetpack Compose. Para compilar en un computador con el
SDK de Android: `gradle assembleRelease`.

La llave de firma **no** está en el repositorio. GitHub Actions la toma de dos *secrets* del
repositorio (`KEYSTORE_BASE64` y `KEYSTORE_PASSWORD`), de modo que todas las compilaciones firman
igual y las actualizaciones se instalan sobre la versión anterior. Si se pierden esos secrets, los
APK nuevos no podrán instalarse encima de los anteriores (habría que desinstalar la app).

# Modelo de lectura

> Borrador de la sección del informe (Entrega 6). Describe lo que hay en la rama `feature/consultas-completo`.

El Servicio de Consultas mantiene en MongoDB una copia desnormalizada del estado de los cuatro módulos, armada solo a partir de los eventos que publican en el exchange topic `donatrack.exchange`. Consume la cola `donatrack.eventos`, atada con la routing key `#`. Las consultas no llaman a ningún módulo de escritura: leen estas colecciones.

Se combinan dos estrategias:

- **Datos materializados al recibir el evento.** Es lo que se consulta por id o con filtros simples: la ficha del donador, las necesidades y el estado de los depósitos. Cada evento actualiza los documentos afectados, y los campos derivados (conteos, entidades beneficiadas, estadísticas de depósito) se **recalculan** a partir de las colecciones base (`donaciones`, `asignaciones`). Por eso reprocesar un evento no cuenta dos veces.
- **Datos calculados al consultar, con pipelines de agregación.** Es lo que agrupa sobre todo el universo de datos: la calidad de lo donado y el ranking de quejas (consulta 4), y el estado de los depósitos en un rango de fechas (consulta 3).

## Colecciones

### `donadores`

Un documento por donador. Junta los datos de Donadores y Entidades, de Incentivos y lo que se calcula a partir de Donaciones y Logística.

```json
{
  "_id": "12",
  "nombre": "Lucía", "apellido": "Fernández",
  "estado": "VERIFICADO",
  "categoria": "COLABORADOR",
  "historialCategorias": [ { "categoria": "OCASIONAL", "desde": "2026-03-02" },
                           { "categoria": "COLABORADOR", "desde": "2026-06-18" } ],
  "insignias": [ "Completitud", "Solidaridad" ],
  "misionEnCurso": { "nombre": "Donaciones Exitosas", "progreso": 14, "objetivo": 20 },
  "misionesCompletadas": [ ],
  "donacionesPorEstado": { "INGRESADA": 2, "ACEPTADA": 14, "CONQUEJA": 3 },
  "quejasRecibidas": 3,
  "quejaIds": [ "q1", "q2", "q3" ],
  "entidadesBeneficiadas": [ { "entidadId": "4", "razonSocial": "Comedor Los Girasoles", "unidades": 120 } ],
  "ultimaActualizacion": "2026-10-20T14:32:10Z",
  "ultimaActualizacionIncentivos": "2026-10-20T14:30:00Z"
}
```

| Campos | Los actualiza | Cómo |
| :--- | :--- | :--- |
| `nombre`, `apellido`, `estado`, `categoria` | `DONADOR_CREADO`, `DONADOR_ESTADO_MODIFICADO`, `DONADOR_CATEGORIA_MODIFICADA` (Donadores) | Reemplazo, si el evento no es más viejo que `ultimaActualizacion`. |
| `quejasRecibidas`, `quejaIds` | `QUEJA_REGISTRADA` (Donadores) | Suma 1 si `queja.id` no estaba en `quejaIds`. En la 5.ª queja pasa a `SOSPECHOSO` y en la 10.ª a `BANEADO`, como en Donadores, solo si la queja no es más vieja que el último evento de Donadores. |
| `categoria`, `historialCategorias`, `insignias`, `misionEnCurso`, `misionesCompletadas` | `DONADOR_ACTUALIZADO` (Incentivos); `BASE_LIMPIADA` con `idReferencia = incentivos` | Incentivos manda el estado completo y se reemplaza. Se compara contra `ultimaActualizacionIncentivos`. |
| `donacionesPorEstado` | `DONACION_CREADA`, `DONACION_ESTADO_MODIFICADO`, `DONACION_ELIMINADA` (Donaciones) | Se recalcula contando las donaciones del donador en `donaciones`. |
| `entidadesBeneficiadas` | `ENTREGA_COMPLETADA`, `ASIGNACION_CREADA` (Logística); `NECESIDAD_REGISTRADA`, `ENTIDAD_RAZON_MODIFICADA` (Donadores); `BASE_LIMPIADA` de Logística | Se recalcula: asignaciones `COMPLETADA` de las donaciones del donador, sumando unidades por entidad. |
| todo el documento | `DONADOR_ELIMINADO`, `DONADORES_TODOS_ELIMINADOS` | Se borra. |

`quejaIds` es interno y no sale en la respuesta.

**Resuelve:** consulta 1 (`GET /consultas/donador/{id}`) y la segunda parte de la consulta 4 (ranking de quejas).

### `necesidades`

Un documento por necesidad material.

```json
{
  "_id": "n1",
  "entidadId": "4", "entidadRazonSocial": "Comedor Los Girasoles",
  "urgencia": 9, "tipoNecesidad": "EXTRAORDINARIA",
  "productoSolicitadoId": "5", "productoNombre": null,
  "categoriaProducto": null, "subcategoriaProducto": null,
  "cantidadObjetivo": 200, "cantidadFaltante": 80, "satisfecha": false,
  "unidadesAsignadasPendientes": 20,
  "stockPorDeposito": { "3": 50, "7": 30 },
  "ultimaActualizacion": "2026-11-06T11:00:00Z"
}
```

| Campos | Los actualiza | Cómo |
| :--- | :--- | :--- |
| `entidadId`, `productoSolicitadoId`, `tipoNecesidad`, `urgencia` (`nivelDeUrgencia`) | `NECESIDAD_REGISTRADA` | Son datos del alta y no cambian después, así que se completan aunque el evento llegue tarde. |
| `cantidadObjetivo`, `cantidadFaltante`, `satisfecha` | `NECESIDAD_REGISTRADA`, `NECESIDAD_MODIFICADA` (`extra.cantidadObjetivo`), `NECESIDAD_SATISFECHA` (`extra.cantidadObjetivoRestante`) | Solo si el evento no es más viejo que `ultimaActualizacion`. |
| `entidadRazonSocial` | `ENTIDAD_CREADA`, `ENTIDAD_RAZON_MODIFICADA`, o el registro de la necesidad | Se toma de `entidades`. |
| `stockPorDeposito` | `DEPOSITO_*` con `stock` (Logística); `DEPOSITO_ELIMINADO`; `BASE_LIMPIADA` de Logística | Unidades del producto solicitado en cada depósito. Si la necesidad se registra después del evento de stock, se inicializa desde `depositos.stockPorProducto`. |
| `unidadesAsignadasPendientes` | `ASIGNACION_CREADA`, `ENTREGA_COMPLETADA` (Logística); `NECESIDAD_REGISTRADA` | Suma de las asignaciones `ASIGNADA` de la necesidad en `asignaciones`. |
| todo el documento | `NECESIDAD_ELIMINADA` | Se borra. |

`NECESIDAD_REGISTRADA` no pisa los campos de Logística. Hace falta porque Donadores le pide a Logística que asigne **antes** de publicar la necesidad, así que la asignación suele llegar primero. `ultimaActualizacion` lo escriben solo los eventos de Donadores, de modo que un evento de Logística más nuevo no hace descartar uno de Donadores.

**Resuelve:** consulta 2 (`GET /consultas/necesidades-urgentes`): `satisfecha != true`, filtros opcionales por urgencia mínima, tipo, categoría y subcategoría, ordenadas por urgencia descendente.

### `depositos`

```json
{
  "_id": "3",
  "nombre": "Depósito Medrano",
  "capacidadTotal": 1000, "stockActual": 750, "ocupacionPorcentaje": 75.0,
  "algoritmoMatchmaking": "SUB_ATENDIDOS",
  "asignacionesPorEstado": { "ASIGNADA": 3, "COMPLETADA": 42 },
  "tiempoPromedioEntregaMinutos": 145.5,
  "entidadesDistintasAtendidas": [ "1", "4", "8" ], "cantidadEntidadesDistintas": 3,
  "stockPorProducto": { "5": 50 },
  "ultimaActualizacion": "2026-11-06T10:00:00Z"
}
```

| Campos | Los actualiza | Cómo |
| :--- | :--- | :--- |
| `nombre`, `capacidadTotal` (`capacidadMaxima`), `stockActual` (`unidadesOcupadas`), `ocupacionPorcentaje`, `algoritmoMatchmaking`, `stockPorProducto` | `DEPOSITO_CREADO`, `DEPOSITO_MODIFICADO`, `DEPOSITO_ALGORITMO_CONFIGURADO`, `DEPOSITO_STOCK_ACTUALIZADO` | Logística manda el estado completo; se aplica si es más nuevo que `ultimaActualizacion`. |
| `asignacionesPorEstado`, `tiempoPromedioEntregaMinutos`, `entidadesDistintasAtendidas` | `DONACION_*`, `ASIGNACION_CREADA`, `ENTREGA_COMPLETADA`, `NECESIDAD_REGISTRADA`, `ENTIDAD_RAZON_MODIFICADA` | Se recalcula desde las donaciones del depósito y sus asignaciones. El tiempo de entrega va de `donaciones.fechaIngreso` a `asignaciones.fechaEntrega`. |
| todo el documento | `DEPOSITO_ELIMINADO`, `BASE_LIMPIADA` de Logística | Se borra. |

`stockPorProducto` es interno.

**Resuelve:** consulta 3 (`GET /consultas/depositos`, `/depositos/{id}`). Con rango de fechas (`/depositos/estado?desde&hasta`) las estadísticas se recalculan al consultar, a partir de `donaciones` y `asignaciones`.

### `donaciones`

Copia de cada donación tal como la publica Donaciones. Es la colección base de los conteos y de la consulta 4.

```json
{
  "_id": "7", "donadorId": "12", "depositoId": "3",
  "descripcion": "Fideos secos 500g", "estado": "CONQUEJA", "fechaRegistro": "2026-11-06",
  "fechaIngreso": "2026-11-06T10:00:00Z",
  "productos": [ { "productoId": "5", "cantidad": 10, "categoriaId": null, "subcategoriaId": null } ],
  "ultimaActualizacion": "2026-11-06T12:00:00Z"
}
```

La actualizan `DONACION_CREADA` y `DONACION_ESTADO_MODIFICADO` (reemplazo si el evento es más nuevo), y `DONACION_ELIMINADA` (borrado). `fechaIngreso` es el timestamp del primer evento de la donación y no se pisa.

**Resuelve:** consulta 1 (`donacionesPorEstado`), consulta 3 (tiempo de entrega) y consulta 4, primera parte. Para esta última, `GET /consultas/calidad-donaciones?categoria=&subcategoria=` corre este pipeline:

1. `$unwind` de `productos` con `preserveNullAndEmptyArrays`.
2. `$group` por donación + categoría + subcategoría: una donación cuenta una vez por subcategoría aunque tenga varios productos de ella.
3. `$match` opcional por categoría y/o subcategoría.
4. `$group` por categoría + subcategoría: total de donaciones y cuántas están `CONQUEJA`.
5. `$project` con `proporcionQuejas = donacionesConQueja / totalDonaciones`.
6. `$sort` por proporción descendente.

Hoy Donaciones publica los productos **sin** categoría ni subcategoría (`detallesProductosDTO` solo trae `productoID` y `cantidadProducto`), así que todo se informa como `SIN_CATEGORIA` / `general`. Cuando Donaciones agregue `categoriaID` y `subcategoriaID` al detalle, Consultas ya las toma sin cambios. Esto reemplaza a la colección `calidad_donaciones`, que se borraba y se volvía a escribir completa en cada evento.

### `asignaciones`

```json
{
  "_id": "a1", "paqueteId": "40", "donacionId": "7", "productoId": "5", "cantidad": 10,
  "necesidadId": "n1", "entidadId": "4", "entidadRazonSocial": "Comedor Los Girasoles",
  "origen": "SOLICITUD_DONADORES", "estado": "COMPLETADA",
  "fechaAsignacion": "2026-11-06T11:00:00Z", "fechaEntrega": "2026-11-06T13:00:00Z",
  "ultimaActualizacion": "2026-11-06T13:00:00Z"
}
```

| Campos | Los actualiza | Cómo |
| :--- | :--- | :--- |
| datos de la asignación | `ASIGNACION_CREADA`, `ENTREGA_COMPLETADA` | Logística manda el estado completo; se reemplaza si es más nuevo. |
| `entidadId`, `entidadRazonSocial` | `NECESIDAD_REGISTRADA`, `ENTIDAD_*` | Se toman de la necesidad y de la entidad. Si la asignación llega antes que la necesidad, se completan cuando llega la necesidad. |
| todo el documento | `BASE_LIMPIADA` de Logística | Se borra. |

**Resuelve:** es colección base. De ella salen `entidadesBeneficiadas` (C1), `unidadesAsignadasPendientes` (C2) y las estadísticas de cada depósito (C3).

### `entidades`

`{ "_id": "4", "razonSocial": "Comedor Los Girasoles", "ultimaActualizacion": "…" }`

La actualizan `ENTIDAD_CREADA` y `ENTIDAD_RAZON_MODIFICADA` (que se propaga a necesidades, asignaciones y donadores), y `ENTIDAD_ELIMINADA` / `ENTIDADES_TODAS_ELIMINADAS` (borrado). Al borrar una entidad, las necesidades y asignaciones conservan la razón social: las entregas que ya recibió siguen en la historia de cada donador.

**Resuelve:** la razón social en las consultas 1 y 2.

### `eventos_procesados`

`{ "_id": "<id del evento>", "procesadoEn": "…" }`. Se guarda uno por cada evento aplicado, y un evento con un id ya guardado se ignora.

## Resumen: consulta → colecciones

| Consulta | Endpoint | Colecciones | Cuándo se calcula |
| :--- | :--- | :--- | :--- |
| C1 Ficha del donador | `GET /consultas/donador/{id}` | `donadores` (derivada de `donaciones`, `asignaciones`, `entidades`) | Al recibir el evento |
| C2 Necesidades urgentes | `GET /consultas/necesidades-urgentes` | `necesidades` | Al recibir el evento |
| C3 Estado de depósitos | `GET /consultas/depositos[/{id}]`, `/depositos/estado` | `depositos` (derivada de `donaciones`, `asignaciones`) | Al recibir el evento; con rango, al consultar |
| C4 Calidad de lo donado | `GET /consultas/calidad-donaciones` | `donaciones` | Al consultar (pipeline) |
| C4 Ranking de quejas | `GET /consultas/donadores-con-mas-quejas?limite=` | `donadores` | Al consultar (pipeline: `$match`, `$sort`, `$limit` por defecto 10, `$project` de id, nombre, estado, categoría y quejas) |

## Idempotencia y orden de los eventos

RabbitMQ entrega al menos una vez y no garantiza el orden entre módulos. Hay tres defensas:

1. **Id del evento** (`eventos_procesados`): un reenvío del mismo mensaje no se aplica dos veces.
2. **Timestamp por fuente.** Cada documento guarda el timestamp del último evento aplicado *de cada módulo que lo escribe*, y un evento más viejo se descarta:

   | Documento | Campo | Módulo |
   | :--- | :--- | :--- |
   | `donadores` | `ultimaActualizacion` | Donadores y Entidades |
   | `donadores` | `ultimaActualizacionIncentivos` | Incentivos |
   | `necesidades` | `ultimaActualizacion` | Donadores y Entidades |
   | `entidades` | `ultimaActualizacion` | Donadores y Entidades |
   | `depositos` | `ultimaActualizacion` | Logística |
   | `asignaciones` | `ultimaActualizacion` | Logística |
   | `donaciones` | `ultimaActualizacion` | Donaciones |

   Los campos que aporta otro módulo (por ejemplo, el stock de Logística en una necesidad) se actualizan sin tocar ese timestamp. Así un módulo no hace descartar los eventos de otro.
3. **Claves naturales y recálculo.** Las quejas se cuentan por `queja.id`, no se descartan por timestamp porque se suman y no reemplazan nada. Los conteos y estadísticas se recalculan desde las colecciones base. En los dos casos, reaplicar un evento da el mismo resultado.

## Limitaciones conocidas

- Donaciones no publica la categoría ni la subcategoría de los productos, así que la consulta 4 agrupa todo en `SIN_CATEGORIA`. Por la misma razón (y porque Donadores no publica el nombre ni la categoría del producto solicitado), las necesidades no tienen `productoNombre`, `categoriaProducto` ni `subcategoriaProducto`, y los filtros de la consulta 2 por categoría no devuelven nada.
- `DONACION_ELIMINADA` no deja marca: si después llega un `DONACION_CREADA` atrasado de la misma donación, se vuelve a crear.
- `eventos_procesados` crece sin límite. Un índice TTL sobre `procesadoEn` lo acotaría.
- En las bases ya desplegadas puede quedar la colección vieja `calidad_donaciones`, que ya no se usa y se puede borrar a mano.

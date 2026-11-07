# Eventos por módulo

Relevado del código el 2026-11-06. Commits: Donaciones `53b54fe`, DonadoresYEntidades `acdb5c9`, Logística `ed0641a`, Incentivos `187df9a`. La columna "¿Consultas lo consume?" refleja ServicioDeConsultas `main` con los cambios de productos, C4 por nombre, `BASE_LIMPIADA` por origen, cascada de entidades y recálculo de depósitos.
Exchange común: `donatrack.exchange` (topic). Rutas sin el prefijo del gateway (`/api/<módulo>`).

## 1. Sobres

| Módulo | Campos del sobre (tipo) | Serialización / propiedades AMQP |
|---|---|---|
| Donaciones | `id` String (UUID), `tipo` TipoEventoDonaciones, `timestamp` Instant, `idReferencia` String, `donacion` DonacionEvento \| `producto` ProductoEvento | `Jackson2JsonMessageConverter`, `NON_NULL`, header `__TypeId__` |
| DonadoresYEntidades | `id` String (UUID), `tipo` TipoEvento, `timestamp` Instant, `idReferencia` String, `donador` \| `entidad` \| `necesidad` \| `queja`, `extra` Map<String,Object> (siempre presente, `{}` por defecto) | `Jackson2JsonMessageConverter`, `NON_NULL`, header `__TypeId__` |
| Logística | `id` String (UUID), `tipo` TipoEventoLogistica, `timestamp` Instant, `idReferencia` String, `deposito` \| `asignacion`, `extra` Map<String,Object> (`traceId`) | bytes JSON, `messageId`=`id`, `timestamp`, PERSISTENT, sin `__TypeId__` |
| Incentivos | `id` String (UUID), `tipo` TipoEventoIncentivos, `timestamp` Instant, `idReferencia` String, `donador` \| `insignia` \| `mision`, `extra` Map<String,Object> (`traceId`, `motivo`) | bytes JSON, `messageId`=`id`, `timestamp`, PERSISTENT, sin `__TypeId__` |

## 2. Payloads

| Tipo de payload | Campos (tipo) |
|---|---|
| DonacionEvento | `id` String, `donadorID` String, `depositoID` String, `descripcion` String, `detallesProductosDTO` List<DetalleProductoEvento>, `estado` EstadoDonacionEnum (INGRESADA\|ACEPTADA\|CONQUEJA), `fechaRegistro` LocalDate |
| DetalleProductoEvento | `id` String, `productoID` String, `cantidadProducto` Integer, `categoriaID` String, `categoria` String, `subcategoriaID` String, `subcategoria` String |
| ProductoEvento | `id` String, `nombre` String, `descripcion` String, `subcategoriaID` String, `subcategoria` String, `categoriaID` String, `categoria` String |
| DonadorDTO | `id` String, `nombre` String, `apellido` String, `edad` Integer, `email` String, `nroDocumento` String, `domicilio` String, `estado` EstadoDonadorEnum (VERIFICADO\|SOSPECHOSO\|BANEADO), `categoria` String |
| EntidadBeneficaDTO | `id` String, `razonSocial` String, `domicilio` String, `telefono` String, `correo` String |
| NecesidadMaterialDTO | `id` String, `entidadID` String, `nivelDeUrgencia` Integer, `descripcion` String, `cantidadObjetivo` Integer, `productoSolicitadoID` String, `tipo` TipoNecesidadMaterialEnum (EXTRAORDINARIA\|RECURRENTE) |
| QuejaDTO | `id` String, `donacionID` String, `donadorID` String, `fecha` LocalDate, `descripcion` String |
| DepositoEvento | `id` String, `nombre` String, `direccion` String, `capacidadMaxima` Integer, `unidadesOcupadas` int, `algoritmo` String (SUB_ATENDIDOS\|PRIORIDAD\|PRIORIDAD_POR_SCORE), `stock` List<StockEvento> |
| StockEvento | `paqueteId` String, `donacionId` String, `productoId` String, `cantidad` Integer |
| AsignacionEvento | `id` String, `paqueteId` String, `donacionId` String, `productoId` String, `cantidad` Integer, `necesidadId` String, `origen` String (MATCHMAKING\|SOLICITUD_DONADORES), `estado` String (ASIGNADA\|COMPLETADA), `fechaAsignacion` Instant, `fechaEntrega` Instant (solo COMPLETADA) |
| DonadorEvento (Inc.) | `id` String, `categoriaActual` String, `historialCategorias` List<{`categoria` String, `desde` Instant}>, `insignias` List<InsigniaEvento>, `misionEnCurso` {`id` String, `nombre` String, `tipo` String, `progreso` Integer, `objetivo` Integer} \| null, `misionesCompletadas` List<{`id` String, `nombre` String}>, `fechaEvento` Instant |
| InsigniaEvento | `id` String, `nombre` String, `descripcion` String |
| MisionEvento | `id` String, `nombre` String, `insigniaId` String, `categoriaInicio` String, `categoriaFin` String, `tipo` String, `cantidadRequerida` Integer, `objetivo` Integer |

Categorías del donador (Inc.): OCASIONAL\|COLABORADOR\|TRANSFORMADOR\|SALVADOR\|REVOLUCIONARIO. Tipos de misión: COMPLETITUD\|DONACIONES_EXITOSAS\|DONACIONES_ASCENDENTES\|REVOLUCION_DONADORA.

## 3. Eventos

### Donaciones (`DonacionesEventPublisher`)

Condición: siempre. No hay flag; si el broker falla, se loguea un warn y la operación sigue.

| Tipo | Routing key | Disparador (endpoint → método) | idReferencia | Payload | Commit |
|---|---|---|---|---|---|
| `DONACION_CREADA` | `donaciones.donacion.creada` | `POST /donaciones` → `Fachada.registrarDonacion` (`@Transactional`) | `donacion.id` | `donacion` | Después (`afterCommit`) |
| `DONACION_ESTADO_MODIFICADO` | `donaciones.donacion.estado_modificado` | `PATCH /donaciones/{id}/estado` → `cambiarEstadoDeDonacion`; `POST /donaciones/{id}/queja` → `registrarQuejaEnDonacion` (lo llaman Donadores al registrar una queja y Logística al entregar) | `donacion.id` | `donacion` | Después (`afterCommit`) |
| `DONACION_ELIMINADA` | `donaciones.donacion.eliminada` | `DELETE /donaciones/{id}` → `deleteDonacion` (sin `@Transactional`) | `donacion.id` | `donacion` (estado previo) | Después (el `delete` del repo ya confirmó) |
| `PRODUCTO_CREADO` | `donaciones.producto.creado` | `POST /productos` → `agregarProducto` (sin tx); `POST /admin/republicar-productos` → `republicarProductos` (uno por producto existente, responde `{republicados, total}`) | `producto.id` | `producto` | Después (el `save` ya confirmó; la republicación no tiene tx) |
| `PRODUCTO_MODIFICADO` | `donaciones.producto.modificado` | `PUT /productos/{id}` → `modificarProducto` (sin tx) | `producto.id` | `producto` | Después (el `save` ya confirmó) |
| `PRODUCTO_ELIMINADO` | `donaciones.producto.eliminado` | `DELETE /productos/{id}` → `eliminarProducto` (sin tx) | `producto.id` | `producto` (estado previo) | Después (el `delete` ya confirmó) |
| `BASE_LIMPIADA` | `donaciones.base.limpiada` | `DELETE /admin/db` → `vaciarBaseDeDatos` (sin tx), después de vaciar | `"donaciones"` | — | Después (cada `deleteAll` ya confirmó) |

### DonadoresYEntidades (`DonadoresEventPublisher`)

Condición: `eventPublisher != null` (lo inyecta `Application.fachada`). Sin flag; si el broker falla, warn. Todos los métodos de la Fachada son `@Transactional` y publican en forma síncrona, dentro de la transacción.

| Tipo | Routing key | Disparador (endpoint → método) | idReferencia | Payload | Commit |
|---|---|---|---|---|---|
| `DONADOR_CREADO` | `donadores.donador.creado` | `POST /donadores` → `agregarDonador` | `donador.id` | `donador` DonadorDTO | **Antes** |
| `DONADOR_ESTADO_MODIFICADO` | `donadores.donador.estado` | `PATCH /donadores/{id}/estado` → `modificarEstado`; también desde `agregarQueja` (5.ª queja → SOSPECHOSO, 10.ª → BANEADO) | donadorId | `extra.estado` String | **Antes** |
| `DONADOR_CATEGORIA_MODIFICADA` | `donadores.donador.categoria` | `PATCH /donadores/{id}/categoria` → `modifcarCategoria` | donadorId | `extra.categoria` String | **Antes** |
| `DONADOR_ELIMINADO` | `donadores.donador.eliminado` | `DELETE /donadores/{id}` → `quitarDonador` | donadorId | — | **Antes** (el `remove` se ejecuta en el flush del commit) |
| `DONADORES_TODOS_ELIMINADOS` | `donadores.donador.todos_eliminados` | `DELETE /donadores` → `quitarTodosLosDonadores` | — (null) | — | **Antes** |
| `ENTIDAD_CREADA` | `donadores.entidad.creada` | `POST /entidades` → `agregarEntidad` | `entidad.id` | `entidad` EntidadBeneficaDTO | **Antes** |
| `ENTIDAD_RAZON_MODIFICADA` | `donadores.entidad.razonsocial` | `PATCH /entidades/{id}/razon-social` → `modificarRazonSocial` | entidadId | `extra.razonSocial` String | **Antes** |
| `ENTIDAD_ELIMINADA` | `donadores.entidad.eliminada` | `DELETE /entidades/{id}` → `quitarEntidad` | entidadId | — | **Antes** |
| `ENTIDADES_TODAS_ELIMINADAS` | `donadores.entidad.todas_eliminadas` | `DELETE /entidades` → `quitarTodasLasEntidades` | — (null) | — | **Antes** |
| `NECESIDAD_REGISTRADA` | `donadores.necesidad.registrada` | `POST /necesidades` → `registrarNecesidad` (después de pedirle la asignación a Logística) | `necesidad.id` | `necesidad` NecesidadMaterialDTO | **Antes** |
| `NECESIDAD_MODIFICADA` | `donadores.necesidad.modificada` | `PATCH /necesidades/{id}/cantidad-objetivo` → `modificarNecesidad` | necesidadId | `extra.cantidadObjetivo` Integer | **Antes** |
| `NECESIDAD_SATISFECHA` | `donadores.necesidad.satisfecha` | `POST /necesidades/{id}/satisfaccion` → `satisfacerNecesidad` (también lo llama Logística al entregar) | necesidadId | `extra.cantidadSatisfecha` Integer, `extra.cantidadObjetivoRestante` Integer | **Antes** |
| `NECESIDAD_ELIMINADA` | `donadores.necesidad.eliminada` | `DELETE /necesidades/{id}` → `quitarNecesidad` | necesidadId | — | **Antes** |
| `QUEJA_REGISTRADA` | `donadores.queja.registrada` | `POST /quejas` → `agregarQueja` | `queja.donadorID` | `queja` QuejaDTO | **Antes** |

### Logística (`PublicadorEventos`)

Condición: `EVENTOS_ENABLED=true` (por defecto `false`) y que haya un `RabbitTemplate`. El envío es asíncrono, en un solo hilo, con una cola de 1000 eventos; si la cola se llena, el evento se descarta. Todos los services son `@Transactional`.

| Tipo | Routing key | Disparador (endpoint → método) | idReferencia | Payload | Commit |
|---|---|---|---|---|---|
| `DEPOSITO_CREADO` | `logistica.deposito.creado` | `POST /depositos` → `DepositoService.crear` | depósito id | `deposito` | Después (`afterCommit`) |
| `DEPOSITO_MODIFICADO` | `logistica.deposito.modificado` | `PUT /depositos/{id}` → `DepositoService.modificar` | depósito id | `deposito` | Después |
| `DEPOSITO_ELIMINADO` | `logistica.deposito.eliminado` | `DELETE /depositos/{id}` → `DepositoService.eliminar` | depósito id | `deposito` (estado previo) | Después |
| `DEPOSITO_ALGORITMO_CONFIGURADO` | `logistica.deposito.algoritmo` | `PATCH /depositos/{id}/algoritmo` → `DepositoService.configurarAlgoritmo` | depósito id | `deposito` | Después |
| `DEPOSITO_STOCK_ACTUALIZADO` | `logistica.deposito.stock` | `StockService.guardar` (desde `POST /donaciones` → `RecepcionDonacionService.recibir`, y desde `POST /depositos/{id}/stock` del Worker); `StockService.asignarDesdeStock` (`POST /stock/{productoID}/asignaciones`, que llama Donadores), uno por depósito consumido | depósito id | `deposito` | Después |
| `ASIGNACION_CREADA` | `logistica.asignacion.creada` | `AsignacionService.crear` (desde `asignarPorMatchmaking`: `POST /donaciones` o `POST /asignaciones` del Worker; desde `asignarPorSolicitud`: `POST /stock/{productoID}/asignaciones`); `asignarPaqueteExistente` (`Fachada.ejecutarMatchmaking`, interfaz de cátedra, sin endpoint) | asignación id | `asignacion` (ASIGNADA) | Después |
| `ENTREGA_COMPLETADA` | `logistica.entrega.completada` | `POST /entregas` → `EntregaService.reportar` → `AsignacionService.completar` | asignación id | `asignacion` (COMPLETADA + `fechaEntrega`) | Después |
| `BASE_LIMPIADA` | `logistica.base.limpiada` | `DELETE /testing/reset` → `MantenimientoService.limpiarBaseDeDatos` | `"logistica"` | — | Después |

### Incentivos (`PublicadorEventosIncentivos`)

Condición: que exista `CLOUDAMQP_URL`; sin esa variable no hay `RabbitTemplate` y no se publica nada. Solo `procesarDonador` es `@Transactional`; el resto publica enseguida después del `save`/`delete` del repo, que ya confirmó.

| Tipo | Routing key | Disparador (endpoint → método) | idReferencia | Payload / `extra.motivo` | Commit |
|---|---|---|---|---|---|
| `DONADOR_ACTUALIZADO` | `incentivos.donador.actualizado` | `POST /incentivos-donador/{id}` → `registrarNuevoDonador` | donadorId | `donador` / REGISTRADO | Después (sin tx) |
| `DONADOR_ACTUALIZADO` | ídem | `POST /incentivos-donador/{id}/insignias` → `asignarInsignia` | donadorId | `donador` / INSIGNIA_ASIGNADA | Después (sin tx) |
| `DONADOR_ACTUALIZADO` | ídem | `POST /incentivos-donador/{id}/misiones` → `asignarMision` | donadorId | `donador` / MISION_ASIGNADA | Después (sin tx) |
| `DONADOR_ACTUALIZADO` | ídem | `POST /incentivos-donador/{id}/procesar` y el cron `ProcesadorMisionesScheduler` → `procesarDonador` (`@Transactional`) | donadorId | `donador` / MISION_CUMPLIDA, PROGRESO_PERDIDO, MISION_AVANZADA | Después (`afterCommit`) |
| `INSIGNIA_CREADA` | `incentivos.insignia.creada` | `POST /insignias` → `guardarInsignia` (también desde `POST /admin/agregar-misiones-base`) | insignia id | `insignia` | Después (sin tx) |
| `INSIGNIA_ELIMINADA` | `incentivos.insignia.eliminada` | `DELETE /insignias/{id}` → `eliminarInsignia` | insignia id | `insignia` (estado previo, o solo el `id`) | Después (sin tx) |
| `MISION_CREADA` | `incentivos.mision.creada` | `POST /misiones` → `guardarMision` (también desde `agregar-misiones-base`) | misión id | `mision` | Después (sin tx) |
| `MISION_MODIFICADA` | `incentivos.mision.modificada` | `PUT /misiones/{id}/cantidad-requerida/{n}` → `configurarCantidadRequerida` | misión id | `mision` | Después (sin tx) |
| `MISION_ELIMINADA` | `incentivos.mision.eliminada` | `DELETE /misiones/{id}` → `eliminarMision` | misión id | `mision` (estado previo, o solo el `id`) | Después (sin tx) |
| `BASE_LIMPIADA` | `incentivos.base.limpiada` | `DELETE /admin/db` → `reset` | `"incentivos"` | — | Después (sin tx) |

## 4. Operaciones que cambian estado y no publican

### Donaciones

| Operación | Endpoint → método | Efecto | Evento |
|---|---|---|---|
| Vaciar la base | `DELETE /admin/db` → `vaciarBaseDeDatos` | Borra donaciones, productos, detalles, historial, categorías, subcategorías e identificadores | Solo `BASE_LIMPIADA`; los productos borrados quedan SIN EVENTO (no sale `PRODUCTO_ELIMINADO` por cada uno) |
| Alta de categoría | `POST /categorias` → `agregarCategoria` | Nueva categoría | SIN EVENTO |
| Baja de categoría | `DELETE /categorias/{id}` → `eliminarCategoria` | Los productos de esa categoría quedan sin clasificar | SIN EVENTO |
| Alta de subcategoría | `POST /subcategorias` → `agregarSubcategoria` | Nueva subcategoría | SIN EVENTO |
| Baja de subcategoría | `DELETE /subcategorias/{id}` → `deleteSubcategoriaById` | Los productos de esa subcategoría quedan sin clasificar | SIN EVENTO |
| Alta y baja de identificador | `POST`/`DELETE /identificadores` | Identificadores | SIN EVENTO |
| Productos anteriores a `e5f62f1` | — | Nunca se publicaron al crearse | Se publican a demanda con `POST /admin/republicar-productos` |

### DonadoresYEntidades

| Operación | Endpoint → método | Efecto | Evento |
|---|---|---|---|
| Baja de entidad, en cascada | `DELETE /entidades/{id}` → `quitarEntidad` | `cascade=ALL, orphanRemoval` borra las necesidades de la entidad | Solo `ENTIDAD_ELIMINADA`; las necesidades quedan SIN EVENTO |
| Baja de donador, en cascada | `DELETE /donadores/{id}` → `quitarDonador` | `cascade=ALL` borra las quejas del donador | Solo `DONADOR_ELIMINADO`; las quejas quedan SIN EVENTO |
| Borrado total de entidades | `DELETE /entidades` → `quitarTodasLasEntidades` | `DELETE FROM NecesidadMaterial` y `DELETE FROM EntidadBenefica` | Solo `ENTIDADES_TODAS_ELIMINADAS`; las necesidades quedan SIN EVENTO |
| Borrado total de donadores | `DELETE /donadores` → `quitarTodosLosDonadores` | `DELETE FROM Queja` y `DELETE FROM Donador` | Solo `DONADORES_TODOS_ELIMINADOS`; las quejas quedan SIN EVENTO |
| Cualquier operación con rollback | todas | El evento ya salió antes del commit | Evento sin cambio real |

### Logística

| Operación | Endpoint → método | Efecto | Evento |
|---|---|---|---|
| Alta de paquete | `PaqueteService.crear` (desde `crear` asignación) | Nuevo paquete | SIN EVENTO propio (viaja en `ASIGNACION_CREADA`) |
| Encolar la donación (mensajería activa) | `POST /donaciones` → `RecepcionDonacionService.encolar` | Va a la cola del Worker; el stock cambia después | SIN EVENTO hasta que el Worker llama a `/asignaciones` o a `/depositos/{id}/stock` |
| Baja de depósito | `DELETE /depositos/{id}` | Sus paquetes y asignaciones | Solo `DEPOSITO_ELIMINADO`; sus paquetes y asignaciones quedan SIN EVENTO |
| Cualquier evento con `EVENTOS_ENABLED=false` o la cola llena | todas | — | SIN EVENTO |

### Incentivos

| Operación | Endpoint → método | Efecto | Evento |
|---|---|---|---|
| Baja de insignia | `DELETE /insignias/{id}` | Los donadores que la tenían | Solo `INSIGNIA_ELIMINADA`; los donadores quedan SIN EVENTO (no se republica `DONADOR_ACTUALIZADO`) |
| Baja de misión | `DELETE /misiones/{id}` | Los donadores que la tenían en curso | Solo `MISION_ELIMINADA`; los donadores quedan SIN EVENTO |
| Cualquier evento sin `CLOUDAMQP_URL` | todas | — | SIN EVENTO |

## 5. Cruce con C1 a C4

Fuente de los campos: el texto del enunciado citado en `ConsultaController`. `enunciado-entrega-6.pdf` no está en el repositorio.

| Consulta | Campo | Evento que lo alimenta | ¿Consultas lo consume hoy? |
|---|---|---|---|
| C1 | estado | `DONADOR_CREADO`, `DONADOR_ESTADO_MODIFICADO` | Sí |
| C1 | categoría actual | `DONADOR_CREADO`, `DONADOR_CATEGORIA_MODIFICADA`, `DONADOR_ACTUALIZADO.categoriaActual` | Sí |
| C1 | historial de categorías | `DONADOR_ACTUALIZADO.historialCategorias` | Sí |
| C1 | insignias | `DONADOR_ACTUALIZADO.insignias` | Sí |
| C1 | misión en curso y progreso | `DONADOR_ACTUALIZADO.misionEnCurso` | Sí |
| C1 | donaciones por estado | `DONACION_CREADA`, `DONACION_ESTADO_MODIFICADO`, `DONACION_ELIMINADA`, `BASE_LIMPIADA` (`donaciones`) | Sí |
| C1 | quejas recibidas | `QUEJA_REGISTRADA` | Sí |
| C1 | entidades beneficiadas y unidades | `ENTREGA_COMPLETADA` (`donacionId`, `cantidad`, `necesidadId`) + `DONACION_*` (`donadorID`) + `NECESIDAD_REGISTRADA` (`entidadID`) + `ENTIDAD_CREADA` (`razonSocial`) | Sí, con un join en Consultas |
| C2 | necesidades insatisfechas | `NECESIDAD_REGISTRADA`, `NECESIDAD_MODIFICADA`, `NECESIDAD_SATISFECHA`, `NECESIDAD_ELIMINADA`, `ENTIDAD_ELIMINADA` (borra las de la entidad) | Sí |
| C2 | urgencia | `NECESIDAD_REGISTRADA.nivelDeUrgencia` | Sí |
| C2 | tipo de necesidad | `NECESIDAD_REGISTRADA.tipo` | Sí |
| C2 | categoría y subcategoría del producto | `PRODUCTO_*` (`categoria`, `subcategoria`) unidos por `productoSolicitadoID` | Sí (colección `productos`; por nombre) |
| C2 | nombre del producto | `PRODUCTO_*.nombre` | Sí |
| C3 | ocupación (stock / capacidad) | `DEPOSITO_*` (`unidadesOcupadas`, `capacidadMaxima`) | Sí |
| C3 | algoritmo de matchmaking | `DEPOSITO_*.algoritmo` | Sí |
| C3 | asignaciones por estado | `ASIGNACION_CREADA`, `ENTREGA_COMPLETADA` + `DONACION_*.depositoID`; se recalcula también con `DEPOSITO_*` | Sí, con un join |
| C3 | tiempo promedio de ingreso a entrega | `DONACION_CREADA` (`timestamp` / `fechaRegistro`) + `ENTREGA_COMPLETADA.fechaEntrega` | Sí (usa el timestamp del primer evento) |
| C3 | entidades distintas atendidas | `ENTREGA_COMPLETADA` + `NECESIDAD_REGISTRADA.entidadID` | Sí, con un join |
| C4 | proporción con queja por categoría y subcategoría | `DONACION_*` (`estado`, `detallesProductosDTO[].categoria` y `.subcategoria`); `PRODUCTO_*` completa y reclasifica | Sí: agrupa y filtra por nombre; guarda los ids aparte |
| C4 | donadores con más quejas, estado y categoría | `QUEJA_REGISTRADA`, `DONADOR_*`, `DONADOR_ACTUALIZADO` | Sí |

### Datos sin ningún evento que los alimente

| Consulta | Dato | Motivo |
|---|---|---|
| C2 / C4 | Categoría y nombre de los productos creados antes de `e5f62f1` | No hay evento automático; se cubren llamando a `POST /admin/republicar-productos` de Donaciones |
| C2 | Baja de las necesidades borradas en cascada con la entidad | `quitarEntidad` no publica `NECESIDAD_ELIMINADA`; Consultas las borra al recibir `ENTIDAD_ELIMINADA` |
| C2 / C4 | Reclasificación al borrar una categoría o subcategoría | Sin evento de categorías ni subcategorías |
| C3 | Depósito de la asignación | `AsignacionEvento` no trae `depositoId`; se deduce de `donacion.depositoID` |
| C1 | Insignias y misión de los donadores tras borrar una insignia o una misión | No se republica `DONADOR_ACTUALIZADO` |

package ar.edu.utn.dds.k3003.consultas.support;

import ar.edu.utn.dds.k3003.consultas.config.RabbitMQConfig;
import ar.edu.utn.dds.k3003.consultas.messaging.EventoGenerico;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

/**
 * Eventos con el mismo JSON que publica cada módulo, leídos con el mismo conversor que usa el consumidor.
 *
 * <ul>
 *   <li>Donaciones: {@code DonacionesEventPublisher} → record {@code EventoDonaciones} con {@code DonacionDTO}.
 *       Se envía con {@code Jackson2JsonMessageConverter}, que agrega el header {@code __TypeId__}.
 *   <li>Donadores y Entidades: {@code DonadoresEventPublisher} → {@code EventoDonadores} (Lombok,
 *       NON_NULL, siempre con {@code extra}). También con {@code __TypeId__}.
 *   <li>Logística: {@code PublicadorEventos} → record {@code EventoLogistica}, bytes JSON sin
 *       {@code __TypeId__}, {@code extra} con el traceId.
 *   <li>Incentivos: {@code PublicadorEventosIncentivos} → record {@code EventoIncentivos}, igual que Logística.
 * </ul>
 */
public final class EventosReales {

  private static final MessageConverter CONVERSOR = new RabbitMQConfig().jacksonMessageConverter();

  private EventosReales() {}

  /** Lo que hace el listener: el tipo destino se infiere del parámetro del método (EventoGenerico). */
  public static EventoGenerico leer(String json, String typeIdHeader) {
    MessageProperties propiedades = new MessageProperties();
    propiedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    propiedades.setContentEncoding("UTF-8");
    if (typeIdHeader != null) propiedades.setHeader("__TypeId__", typeIdHeader);
    propiedades.setInferredArgumentType(EventoGenerico.class);
    return (EventoGenerico) CONVERSOR.fromMessage(new Message(json.getBytes(StandardCharsets.UTF_8), propiedades));
  }

  private static String uuid() {
    return UUID.randomUUID().toString();
  }

  // ---------------- Donaciones ----------------

  public static EventoGenerico donacion(String tipo, String timestamp, String id, String donadorId,
      String depositoId, String estado, String productoId, int cantidad) {
    String json = """
        {"id":"%s","tipo":"%s","timestamp":"%s","idReferencia":"%s",
         "donacion":{"id":"%s","donadorID":"%s","depositoID":"%s","descripcion":"Fideos secos 500g",
           "detallesProductosDTO":[{"id":"90","productoID":"%s","cantidadProducto":%d}],
           "estado":"%s","fechaRegistro":"2026-11-06"}}
        """.formatted(uuid(), tipo, timestamp, id, id, donadorId, depositoId, productoId, cantidad, estado);
    return leer(json, "ar.edu.utn.dds.k3003.messaging.EventoDonaciones");
  }

  /** Como publicaría Donaciones si agregara la categoría y la subcategoría al detalle (pedido al grupo). */
  public static EventoGenerico donacionConCategoria(String tipo, String timestamp, String id, String donadorId,
      String estado, String categoriaId, String subcategoriaId) {
    String json = """
        {"id":"%s","tipo":"%s","timestamp":"%s","idReferencia":"%s",
         "donacion":{"id":"%s","donadorID":"%s","depositoID":"3","descripcion":"Donación",
           "detallesProductosDTO":[{"id":"91","productoID":"5","cantidadProducto":1,
             "categoriaID":"%s","subcategoriaID":"%s"}],
           "estado":"%s","fechaRegistro":"2026-11-06"}}
        """.formatted(uuid(), tipo, timestamp, id, id, donadorId, categoriaId, subcategoriaId, estado);
    return leer(json, "ar.edu.utn.dds.k3003.messaging.EventoDonaciones");
  }

  /**
   * DONACION_* como lo publica hoy Donaciones ({@code DetalleProductoEvento}: ids y nombres de la categoría y
   * la subcategoría; con {@code NON_NULL}, un null no viaja).
   */
  public static EventoGenerico donacionClasificada(String tipo, String timestamp, String id, String estado,
      String productoId, String categoriaId, String categoria, String subcategoriaId, String subcategoria) {
    StringBuilder detalle = new StringBuilder("\"id\":\"92\",\"productoID\":\"" + productoId + "\",\"cantidadProducto\":4");
    if (categoriaId != null) detalle.append(",\"categoriaID\":\"").append(categoriaId).append('"');
    if (categoria != null) detalle.append(",\"categoria\":\"").append(categoria).append('"');
    if (subcategoriaId != null) detalle.append(",\"subcategoriaID\":\"").append(subcategoriaId).append('"');
    if (subcategoria != null) detalle.append(",\"subcategoria\":\"").append(subcategoria).append('"');
    String json = """
        {"id":"%s","tipo":"%s","timestamp":"%s","idReferencia":"%s",
         "donacion":{"id":"%s","donadorID":"12","depositoID":"3","descripcion":"Donación",
           "detallesProductosDTO":[{%s}],"estado":"%s","fechaRegistro":"2026-11-06"}}
        """.formatted(uuid(), tipo, timestamp, id, id, detalle, estado);
    return leer(json, "ar.edu.utn.dds.k3003.messaging.EventoDonaciones");
  }

  /**
   * PRODUCTO_CREADO / PRODUCTO_MODIFICADO / PRODUCTO_ELIMINADO tal como los arma {@code DonacionesEventPublisher}
   * (record {@code ProductoEvento}, {@code NON_NULL}: un null no viaja).
   */
  public static EventoGenerico producto(String tipo, String timestamp, String id, String nombre,
      String categoriaId, String categoria, String subcategoriaId, String subcategoria) {
    StringBuilder producto = new StringBuilder("\"id\":\"" + id + "\",\"nombre\":\"" + nombre + "\","
        + "\"descripcion\":\"Paquete de 500g\"");
    if (subcategoriaId != null) producto.append(",\"subcategoriaID\":\"").append(subcategoriaId).append('"');
    if (subcategoria != null) producto.append(",\"subcategoria\":\"").append(subcategoria).append('"');
    if (categoriaId != null) producto.append(",\"categoriaID\":\"").append(categoriaId).append('"');
    if (categoria != null) producto.append(",\"categoria\":\"").append(categoria).append('"');
    String json = """
        {"id":"%s","tipo":"%s","timestamp":"%s","idReferencia":"%s","producto":{%s}}
        """.formatted(uuid(), tipo, timestamp, id, producto);
    return leer(json, "ar.edu.utn.dds.k3003.messaging.EventoDonaciones");
  }

  // ---------------- Donadores y Entidades ----------------

  private static final String TYPE_ID_DONADORES = "ar.edu.utn.dds.k3003.messaging.events.EventoDonadores";

  public static EventoGenerico donadorCreado(String timestamp, String id, String estado, String categoria) {
    String json = """
        {"id":"%s","tipo":"DONADOR_CREADO","timestamp":"%s","idReferencia":"%s",
         "donador":{"id":"%s","nombre":"Lucía","apellido":"Fernández","edad":34,"email":"lucia@mail.com",
           "nroDocumento":"30111222","domicilio":"Medrano 951","estado":"%s","categoria":"%s"},
         "extra":{}}
        """.formatted(uuid(), timestamp, id, id, estado, categoria);
    return leer(json, TYPE_ID_DONADORES);
  }

  public static EventoGenerico donadorEstado(String timestamp, String id, String estado) {
    String json = """
        {"id":"%s","tipo":"DONADOR_ESTADO_MODIFICADO","timestamp":"%s","idReferencia":"%s",
         "extra":{"estado":"%s"}}
        """.formatted(uuid(), timestamp, id, estado);
    return leer(json, TYPE_ID_DONADORES);
  }

  public static EventoGenerico donadorCategoria(String timestamp, String id, String categoria) {
    String json = """
        {"id":"%s","tipo":"DONADOR_CATEGORIA_MODIFICADA","timestamp":"%s","idReferencia":"%s",
         "extra":{"categoria":"%s"}}
        """.formatted(uuid(), timestamp, id, categoria);
    return leer(json, TYPE_ID_DONADORES);
  }

  public static EventoGenerico donadoresTodosEliminados(String timestamp) {
    String json = """
        {"id":"%s","tipo":"DONADORES_TODOS_ELIMINADOS","timestamp":"%s","extra":{}}
        """.formatted(uuid(), timestamp);
    return leer(json, TYPE_ID_DONADORES);
  }

  public static EventoGenerico entidadCreada(String timestamp, String id, String razonSocial) {
    String json = """
        {"id":"%s","tipo":"ENTIDAD_CREADA","timestamp":"%s","idReferencia":"%s",
         "entidad":{"id":"%s","razonSocial":"%s","domicilio":"Av. Siempreviva 742","telefono":"1144445555",
           "correo":"contacto@girasoles.org"},
         "extra":{}}
        """.formatted(uuid(), timestamp, id, id, razonSocial);
    return leer(json, TYPE_ID_DONADORES);
  }

  public static EventoGenerico entidadRazonModificada(String timestamp, String id, String razonSocial) {
    String json = """
        {"id":"%s","tipo":"ENTIDAD_RAZON_MODIFICADA","timestamp":"%s","idReferencia":"%s",
         "extra":{"razonSocial":"%s"}}
        """.formatted(uuid(), timestamp, id, razonSocial);
    return leer(json, TYPE_ID_DONADORES);
  }

  public static EventoGenerico entidadesTodasEliminadas(String timestamp) {
    String json = """
        {"id":"%s","tipo":"ENTIDADES_TODAS_ELIMINADAS","timestamp":"%s","extra":{}}
        """.formatted(uuid(), timestamp);
    return leer(json, TYPE_ID_DONADORES);
  }

  public static EventoGenerico necesidadRegistrada(String timestamp, String id, String entidadId,
      int urgencia, int cantidadObjetivo, String productoId) {
    String json = """
        {"id":"%s","tipo":"NECESIDAD_REGISTRADA","timestamp":"%s","idReferencia":"%s",
         "necesidad":{"id":"%s","entidadID":"%s","nivelDeUrgencia":%d,"descripcion":"Fideos para el comedor",
           "cantidadObjetivo":%d,"productoSolicitadoID":"%s","tipo":"EXTRAORDINARIA"},
         "extra":{}}
        """.formatted(uuid(), timestamp, id, id, entidadId, urgencia, cantidadObjetivo, productoId);
    return leer(json, TYPE_ID_DONADORES);
  }

  public static EventoGenerico necesidadSatisfecha(String timestamp, String id, int satisfecha, int restante) {
    String json = """
        {"id":"%s","tipo":"NECESIDAD_SATISFECHA","timestamp":"%s","idReferencia":"%s",
         "extra":{"cantidadSatisfecha":%d,"cantidadObjetivoRestante":%d}}
        """.formatted(uuid(), timestamp, id, satisfecha, restante);
    return leer(json, TYPE_ID_DONADORES);
  }

  public static EventoGenerico necesidadModificada(String timestamp, String id, int cantidadObjetivo) {
    String json = """
        {"id":"%s","tipo":"NECESIDAD_MODIFICADA","timestamp":"%s","idReferencia":"%s",
         "extra":{"cantidadObjetivo":%d}}
        """.formatted(uuid(), timestamp, id, cantidadObjetivo);
    return leer(json, TYPE_ID_DONADORES);
  }

  /** {@code eventoId} explícito para poder mandar la misma queja en dos eventos distintos. */
  public static EventoGenerico quejaRegistrada(String eventoId, String timestamp, String quejaId,
      String donacionId, String donadorId) {
    String json = """
        {"id":"%s","tipo":"QUEJA_REGISTRADA","timestamp":"%s","idReferencia":"%s",
         "queja":{"id":"%s","donacionID":"%s","donadorID":"%s","fecha":"2026-11-06",
           "descripcion":"Producto vencido"},
         "extra":{}}
        """.formatted(eventoId, timestamp, donadorId, quejaId, donacionId, donadorId);
    return leer(json, TYPE_ID_DONADORES);
  }

  // ---------------- Logística ----------------

  public static EventoGenerico depositoStock(String timestamp, String depositoId, String productoId,
      int cantidad, String donacionId) {
    String json = """
        {"id":"%s","tipo":"DEPOSITO_STOCK_ACTUALIZADO","timestamp":"%s","idReferencia":"%s",
         "deposito":{"id":"%s","nombre":"Depósito Medrano","direccion":"Medrano 951","capacidadMaxima":1000,
           "unidadesOcupadas":%d,"algoritmo":"SUB_ATENDIDOS",
           "stock":[{"paqueteId":"40","donacionId":"%s","productoId":"%s","cantidad":%d}]},
         "extra":{"traceId":"4bf92f3577b34da6a3ce929d0e0e4736"}}
        """.formatted(uuid(), timestamp, depositoId, depositoId, cantidad, donacionId, productoId, cantidad);
    return leer(json, null);
  }

  public static EventoGenerico asignacionCreada(String timestamp, String id, String donacionId,
      String productoId, int cantidad, String necesidadId) {
    String json = """
        {"id":"%s","tipo":"ASIGNACION_CREADA","timestamp":"%s","idReferencia":"%s",
         "asignacion":{"id":"%s","paqueteId":"40","donacionId":"%s","productoId":"%s","cantidad":%d,
           "necesidadId":"%s","origen":"SOLICITUD_DONADORES","estado":"ASIGNADA","fechaAsignacion":"%s"},
         "extra":{"traceId":"4bf92f3577b34da6a3ce929d0e0e4736"}}
        """.formatted(uuid(), timestamp, id, id, donacionId, productoId, cantidad, necesidadId, timestamp);
    return leer(json, null);
  }

  public static EventoGenerico entregaCompletada(String timestamp, String id, String donacionId,
      String productoId, int cantidad, String necesidadId, String fechaAsignacion) {
    String json = """
        {"id":"%s","tipo":"ENTREGA_COMPLETADA","timestamp":"%s","idReferencia":"%s",
         "asignacion":{"id":"%s","paqueteId":"40","donacionId":"%s","productoId":"%s","cantidad":%d,
           "necesidadId":"%s","origen":"SOLICITUD_DONADORES","estado":"COMPLETADA",
           "fechaAsignacion":"%s","fechaEntrega":"%s"},
         "extra":{}}
        """.formatted(uuid(), timestamp, id, id, donacionId, productoId, cantidad, necesidadId,
        fechaAsignacion, timestamp);
    return leer(json, null);
  }

  // ---------------- Incentivos ----------------

  public static EventoGenerico donadorActualizado(String timestamp, String id, String categoria) {
    String json = """
        {"id":"%s","tipo":"DONADOR_ACTUALIZADO","timestamp":"%s","idReferencia":"%s",
         "donador":{"id":"%s","categoriaActual":"%s",
           "historialCategorias":[{"categoria":"OCASIONAL","desde":"2026-03-02T00:00:00Z"},
                                  {"categoria":"%s","desde":"2026-06-18T00:00:00Z"}],
           "insignias":[{"id":"1","nombre":"Completitud","descripcion":"Completó su perfil"}],
           "misionEnCurso":{"id":"7","nombre":"Donaciones Exitosas","tipo":"DONACIONES_EXITOSAS",
             "progreso":14,"objetivo":20},
           "misionesCompletadas":[],"fechaEvento":"%s"},
         "extra":{"motivo":"MISION_AVANZADA"}}
        """.formatted(uuid(), timestamp, id, id, categoria, categoria, timestamp);
    return leer(json, null);
  }
}

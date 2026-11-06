package ar.edu.utn.dds.k3003.consultas.service;

import ar.edu.utn.dds.k3003.consultas.documents.*;
import ar.edu.utn.dds.k3003.consultas.messaging.EventoGenerico;
import ar.edu.utn.dds.k3003.consultas.metrics.ConsultasMetrics;
import ar.edu.utn.dds.k3003.consultas.repositories.AsignacionDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonacionDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.EntidadDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.EventoProcesadoRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DepositoDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonadorDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.NecesidadDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.ProductoDocumentRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EventProcessingService {

  /** idReferencia con el que Incentivos publica BASE_LIMPIADA. */
  private static final String ORIGEN_INCENTIVOS = "incentivos";

  private final DonadorDocumentRepository donadorRepository;
  private final NecesidadDocumentRepository necesidadRepository;
  private final DepositoDocumentRepository depositoRepository;

  // Se inyectan por campo (y son opcionales) para no cambiar el constructor que usan los tests.
  @Autowired(required = false)
  private AsignacionDocumentRepository asignacionRepository;

  @Autowired(required = false)
  private EventoProcesadoRepository eventosProcesados;

  @Autowired(required = false)
  private DonacionDocumentRepository donacionRepository;

  @Autowired(required = false)
  private EntidadDocumentRepository entidadRepository;

  @Autowired(required = false)
  private ProductoDocumentRepository productoRepository;

  /** Sin Spring (tests) se cuentan en memoria; con Spring se reemplaza por el bean que exporta a Datadog. */
  private ConsultasMetrics metricas = ConsultasMetrics.enMemoria();

  static final String MOTIVO_REPETIDO = "repetido";
  static final String MOTIVO_OBSOLETO = "obsoleto";
  static final String MOTIVO_NO_MAPEADO = "no_mapeado";
  static final String MOTIVO_CATALOGO = "catalogo";
  static final String MOTIVO_INVALIDO = "invalido";

  /**
   * Por qué el handler no aplicó el evento que se está procesando (null si lo aplicó). Lo marcan los
   * handlers con {@link #descartar} y lo lee {@link #procesar} para contar procesados e ignorados sin cambiar
   * la firma de cada handler. Es por hilo porque el listener puede tener varios consumidores.
   */
  private static final ThreadLocal<String> DESCARTE = new ThreadLocal<>();

  @Autowired(required = false)
  void setMetricas(ConsultasMetrics metricas) {
    this.metricas = metricas;
  }

  public EventProcessingService(
      DonadorDocumentRepository donadorRepository,
      NecesidadDocumentRepository necesidadRepository,
      DepositoDocumentRepository depositoRepository) {
    this.donadorRepository = donadorRepository;
    this.necesidadRepository = necesidadRepository;
    this.depositoRepository = depositoRepository;
  }

  public void procesar(EventoGenerico evento) {
    if (evento == null || evento.getTipo() == null) {
      metricas.eventoRecibido(null);
      metricas.eventoIgnorado(null, MOTIVO_INVALIDO);
      return;
    }

    String tipo = evento.getTipo().toUpperCase();
    metricas.eventoRecibido(tipo);
    if (yaProcesado(evento)) {
      log.info("Evento repetido '{}' [ID: {}], se ignora", tipo, evento.getId());
      metricas.eventoIgnorado(tipo, MOTIVO_REPETIDO);
      return;
    }
    log.info("Procesando evento CQRS '{}' para referencia '{}' [ID: {}]", tipo, evento.getIdReferencia(), evento.getId());

    try {
      DESCARTE.remove();
      if (tipo.equals("DONADOR_ACTUALIZADO")) {
        handleDonadorActualizado(evento);
      } else if (tipo.startsWith("INSIGNIA_") || tipo.startsWith("MISION_")) {
        // Cambios del catálogo de Incentivos: no son de un donador. Lo que cada donador tiene llega
        // completo en DONADOR_ACTUALIZADO.
        log.info("Evento de catálogo de Incentivos '{}' [{}] ignorado: no modifica donadores",
            tipo, evento.getIdReferencia());
        descartar(MOTIVO_CATALOGO);
      } else if (tipo.equals("BASE_LIMPIADA") && ORIGEN_INCENTIVOS.equalsIgnoreCase(evento.getIdReferencia())) {
        handleBaseLimpiadaIncentivos(evento);
      } else if (tipo.equals("DONADORES_TODOS_ELIMINADOS")) {
        handleDonadoresTodosEliminados();
      } else if (tipo.startsWith("PRODUCTO_")) {
        handleProducto(evento);
      } else if (tipo.startsWith("ENTIDAD")) {
        handleEntidad(evento);
      } else if (tipo.contains("DONADOR_CREADO")) {
        handleDonadorCreado(evento);
      } else if (tipo.contains("DONADOR_ESTADO")) {
        handleDonadorEstado(evento);
      } else if (tipo.contains("DONADOR_CATEGORIA")) {
        handleDonadorCategoria(evento);
      } else if (tipo.contains("DONADOR_ELIMINADO")) {
        handleDonadorEliminado(evento);
      } else if (tipo.contains("QUEJA_REGISTRADA") || tipo.contains("QUEJA")) {
        handleQuejaRegistrada(evento);
      } else if (tipo.contains("DONACION")) {
        handleDonacion(evento);
      } else if (tipo.contains("NECESIDAD_REGISTRADA")) {
        handleNecesidadRegistrada(evento);
      } else if (tipo.contains("NECESIDAD_MODIFICADA") || tipo.contains("NECESIDAD_SATISFECHA")) {
        handleNecesidadModificada(evento);
      } else if (tipo.contains("NECESIDAD_ELIMINADA")) {
        handleNecesidadEliminada(evento);
      } else if (tipo.contains("ASIGNACION") || tipo.equals("ENTREGA_COMPLETADA")) {
        handleAsignacion(evento);
      } else if (tipo.equals("BASE_LIMPIADA")) {
        handleBaseLimpiada();
      } else if (tipo.contains("DEPOSITO")) {
        handleDeposito(evento);
      } else {
        log.warn("Evento no mapeado explícitamente: {}", tipo);
        descartar(MOTIVO_NO_MAPEADO);
      }
      marcarProcesado(evento);
      String motivo = DESCARTE.get();
      if (motivo == null) {
        metricas.eventoProcesado(tipo);
      } else {
        metricas.eventoIgnorado(tipo, motivo);
      }
    } catch (Exception e) {
      log.error("Error al procesar evento '{}' en MongoDB: {}", tipo, e.getMessage(), e);
      metricas.errorDeProcesamiento(tipo);
    } finally {
      DESCARTE.remove();
    }
  }

  private void handleDonadorCreado(EventoGenerico evento) {
    Map<String, Object> data = evento.getDonador() != null ? evento.getDonador() : evento.getExtra();
    String donadorId = evento.getIdReferencia() != null ? evento.getIdReferencia() : (String) data.get("id");
    if (donadorId == null) return;

    Optional<DonadorDocument> opt = donadorRepository.findById(donadorId);
    if (opt.isPresent() && esObsoleto(opt.get().getUltimaActualizacion(), evento)) {
      return;
    }

    DonadorDocument doc = opt.orElseGet(() -> DonadorDocument.builder().id(donadorId).build());
    if (data.containsKey("nombre")) doc.setNombre((String) data.get("nombre"));
    if (data.containsKey("apellido")) doc.setApellido((String) data.get("apellido"));
    if (data.containsKey("estado")) doc.setEstado((String) data.get("estado"));
    if (data.containsKey("categoria")) {
      String cat = (String) data.get("categoria");
      doc.setCategoria(cat);
      if (doc.getHistorialCategorias().isEmpty() && cat != null) {
        doc.getHistorialCategorias().add(HistorialCategoria.builder().categoria(cat).desde(diaDe(evento)).build());
      }
    }
    doc.setUltimaActualizacion(evento.getTimestamp());
    donadorRepository.save(doc);
    log.info("Donador guardado en MongoDB: {}", donadorId);
  }

  private void handleDonadorEstado(EventoGenerico evento) {
    String donadorId = evento.getIdReferencia();
    if (donadorId == null) return;

    DonadorDocument doc = donadorRepository.findById(donadorId)
        .orElseGet(() -> DonadorDocument.builder().id(donadorId).build());

    if (esObsoleto(doc.getUltimaActualizacion(), evento)) return;

    String nuevoEstado = (String) evento.getExtra().get("estado");
    if (nuevoEstado != null) {
      doc.setEstado(nuevoEstado);
      doc.setUltimaActualizacion(evento.getTimestamp());
      donadorRepository.save(doc);
    }
  }

  private void handleDonadorCategoria(EventoGenerico evento) {
    String donadorId = evento.getIdReferencia();
    if (donadorId == null) return;

    DonadorDocument doc = donadorRepository.findById(donadorId)
        .orElseGet(() -> DonadorDocument.builder().id(donadorId).build());

    if (esObsoleto(doc.getUltimaActualizacion(), evento)) return;

    String nuevaCategoria = (String) evento.getExtra().get("categoria");
    if (nuevaCategoria != null) {
      doc.setCategoria(nuevaCategoria);
      // El historial también lo manda completo Incentivos: si ya termina en esta categoría no se repite.
      List<HistorialCategoria> historial = doc.getHistorialCategorias();
      boolean yaEsLaUltima = !historial.isEmpty()
          && nuevaCategoria.equals(historial.get(historial.size() - 1).getCategoria());
      if (!yaEsLaUltima) {
        historial.add(HistorialCategoria.builder().categoria(nuevaCategoria).desde(diaDe(evento)).build());
      }
      doc.setUltimaActualizacion(evento.getTimestamp());
      donadorRepository.save(doc);
    }
  }

  private void handleDonadorEliminado(EventoGenerico evento) {
    String donadorId = evento.getIdReferencia();
    if (donadorId != null) {
      donadorRepository.deleteById(donadorId);
      log.info("Donador eliminado de MongoDB: {}", donadorId);
    }
  }

  /**
   * Las quejas se suman, no reemplazan nada: por eso no se descartan por timestamp (una queja que llega
   * fuera de orden igual cuenta) sino por su id ({@code queja.id}), que evita contarla dos veces. El
   * timestamp de Donadores solo decide si la queja puede cambiar el estado: Donadores pasa al donador a
   * SOSPECHOSO en la 5.ª queja y a BANEADO en la 10.ª, y eso no debe pisar un cambio de estado posterior.
   */
  private void handleQuejaRegistrada(EventoGenerico evento) {
    Map<String, Object> queja = evento.getQueja() != null ? evento.getQueja() : evento.getExtra();
    String donadorId = evento.getIdReferencia() != null ? evento.getIdReferencia() : texto(queja, "donadorID", "donadorId");

    if (donadorId != null) {
      DonadorDocument donador = donadorRepository.findById(donadorId)
          .orElseGet(() -> DonadorDocument.builder().id(donadorId).build());

      String quejaId = texto(queja, "id");
      if (quejaId != null && donador.getQuejaIds().contains(quejaId)) {
        log.info("Queja '{}' ya contada para el donador '{}' [ID: {}], se ignora", quejaId, donadorId, evento.getId());
        descartar(MOTIVO_REPETIDO);
        return;
      }
      if (quejaId != null) donador.getQuejaIds().add(quejaId);

      int q = (donador.getQuejasRecibidas() != null ? donador.getQuejasRecibidas() : 0) + 1;
      donador.setQuejasRecibidas(q);
      if (!esAnterior(donador.getUltimaActualizacion(), evento)) {
        if (q == 10) donador.setEstado("BANEADO");
        else if (q == 5) donador.setEstado("SOSPECHOSO");
        donador.setUltimaActualizacion(evento.getTimestamp());
      }
      donadorRepository.save(donador);
    }
    // La calidad de lo donado (consulta 4) no se toca acá: sale del estado CONQUEJA de cada donación, que
    // Donaciones publica en DONACION_ESTADO_MODIFICADO cuando Donadores le registra la queja.
  }

  // ----- Incentivos -----

  /**
   * Incentivos manda el estado completo del donador (categoría, historial, insignias, misión en curso y
   * misiones completadas): esos campos se reemplazan, no se mezclan. Se descarta si es más viejo que el
   * último evento de Incentivos aplicado. No toca {@code ultimaActualizacion}, que es de los otros módulos.
   */
  private void handleDonadorActualizado(EventoGenerico evento) {
    Map<String, Object> data = evento.getDonador();
    if (data == null) {
      log.warn("DONADOR_ACTUALIZADO sin el campo 'donador' [ID: {}], se ignora", evento.getId());
      descartar(MOTIVO_INVALIDO);
      return;
    }
    String donadorId = evento.getIdReferencia() != null ? evento.getIdReferencia() : texto(data, "id");
    if (donadorId == null) return;

    DonadorDocument doc = donadorRepository.findById(donadorId)
        .orElseGet(() -> DonadorDocument.builder().id(donadorId).build());
    if (esObsoleto(doc.getUltimaActualizacionIncentivos(), evento)) {
      log.info("DONADOR_ACTUALIZADO más viejo que lo guardado para '{}' [ID: {}], se ignora", donadorId, evento.getId());
      return;
    }

    doc.setCategoria(texto(data, "categoriaActual"));

    List<HistorialCategoria> historial = new ArrayList<>();
    for (Map<String, Object> cambio : mapas(data.get("historialCategorias"))) {
      historial.add(HistorialCategoria.builder()
          .categoria(texto(cambio, "categoria"))
          .desde(fecha(cambio.get("desde")))
          .build());
    }
    doc.setHistorialCategorias(historial);

    doc.setInsignias(nombres(data.get("insignias")));
    doc.setMisionesCompletadas(nombres(data.get("misionesCompletadas")));

    Object enCurso = data.get("misionEnCurso");
    if (enCurso instanceof Map<?, ?>) {
      @SuppressWarnings("unchecked")
      Map<String, Object> mision = (Map<String, Object>) enCurso;
      doc.setMisionEnCurso(MisionEnCurso.builder()
          .nombre(texto(mision, "nombre"))
          .progreso(numero(mision, "progreso"))
          .objetivo(numero(mision, "objetivo"))
          .build());
    } else {
      doc.setMisionEnCurso(null);
    }

    doc.setUltimaActualizacionIncentivos(evento.getTimestamp());
    donadorRepository.save(doc);
    log.info("Incentivos del donador {} actualizados en MongoDB", donadorId);
  }

  /**
   * Se vació la base de Incentivos: se borran las insignias y las misiones de los donadores, sin borrar
   * los documentos. La categoría y su historial se conservan porque también los informa Donadores.
   */
  private void handleBaseLimpiadaIncentivos(EventoGenerico evento) {
    List<DonadorDocument> donadores = donadorRepository.findAll();
    for (DonadorDocument d : donadores) {
      d.setInsignias(new ArrayList<>());
      d.setMisionEnCurso(null);
      d.setMisionesCompletadas(new ArrayList<>());
      // Un DONADOR_ACTUALIZADO anterior al reinicio que llegue tarde queda descartado.
      d.setUltimaActualizacionIncentivos(evento.getTimestamp());
    }
    donadorRepository.saveAll(donadores);
    log.info("Datos de Incentivos limpiados en {} donadores", donadores.size());
  }

  /** Los elementos de una lista del evento que son objetos JSON. */
  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> mapas(Object lista) {
    List<Map<String, Object>> resultado = new ArrayList<>();
    if (lista instanceof List<?> items) {
      for (Object item : items) {
        if (item instanceof Map<?, ?> m) resultado.add((Map<String, Object>) m);
      }
    }
    return resultado;
  }

  /** El nombre de cada objeto de la lista (o su id, si no tiene nombre). */
  private static List<String> nombres(Object lista) {
    List<String> resultado = new ArrayList<>();
    for (Map<String, Object> item : mapas(lista)) {
      String nombre = texto(item, "nombre", "id");
      if (nombre != null) resultado.add(nombre);
    }
    return resultado;
  }

  /** Un instante ISO-8601 pasado a fecha UTC (yyyy-MM-dd); si no se puede leer, el texto tal cual. */
  private static String fecha(Object valor) {
    if (valor == null) return null;
    try {
      return LocalDate.ofInstant(Instant.parse(String.valueOf(valor)), ZoneOffset.UTC).toString();
    } catch (Exception e) {
      return String.valueOf(valor);
    }
  }

  /**
   * Donaciones publica DONACION_CREADA, DONACION_ESTADO_MODIFICADO y DONACION_ELIMINADA con la donación
   * completa. Cada donación se guarda en su propio documento y los conteos se recalculan desde ahí, de modo
   * que un cambio de estado mueve la donación de un estado a otro y no la cuenta dos veces.
   */
  private void handleDonacion(EventoGenerico evento) {
    if (donacionRepository == null) return;
    Map<String, Object> don = evento.getDonacion() != null ? evento.getDonacion() : evento.getExtra();
    String id = evento.getIdReferencia() != null ? evento.getIdReferencia() : texto(don, "id");
    if (id == null) return;
    String tipo = evento.getTipo().toUpperCase();

    if (tipo.contains("ELIMINADA")) {
      donacionRepository.findById(id).ifPresent(existente -> {
        donacionRepository.deleteById(id);
        recalcularDonador(existente.getDonadorId());
        recalcularDeposito(existente.getDepositoId());
      });
      return;
    }

    DonacionDocument doc = donacionRepository.findById(id).orElseGet(() -> DonacionDocument.builder().id(id).build());
    if (noEsMasNuevo(doc.getUltimaActualizacion(), evento)) return;

    doc.setDonadorId(texto(don, "donadorID", "donadorId"));
    doc.setDepositoId(texto(don, "depositoID", "depositoId"));
    doc.setDescripcion(texto(don, "descripcion"));
    doc.setEstado(texto(don, "estado"));
    doc.setFechaRegistro(texto(don, "fechaRegistro"));
    if (doc.getFechaIngreso() == null) doc.setFechaIngreso(evento.getTimestamp());
    doc.setProductos(productosDe(don));
    doc.setUltimaActualizacion(evento.getTimestamp());
    donacionRepository.save(doc);
    log.info("Donación guardada en MongoDB: {} ({})", id, doc.getEstado());

    recalcularDonador(doc.getDonadorId());
    recalcularDeposito(doc.getDepositoId());
    // La calidad de lo donado (consulta 4) se calcula al consultar, con un pipeline sobre esta colección.
  }

  @SuppressWarnings("unchecked")
  private List<ProductoDonado> productosDe(Map<String, Object> don) {
    Object lista = don.get("detallesProductosDTO") != null ? don.get("detallesProductosDTO") : don.get("productos");
    List<ProductoDonado> productos = new ArrayList<>();
    if (lista instanceof List<?> items) {
      for (Object item : items) {
        if (item instanceof Map<?, ?> m) {
          Map<String, Object> p = (Map<String, Object>) m;
          ProductoDonado donado = ProductoDonado.builder()
              .productoId(texto(p, "productoID", "productoId"))
              .cantidad(numero(p, "cantidadProducto", "cantidad"))
              .categoriaId(texto(p, "categoria", "categoriaNombre", "categoriaID", "categoriaId"))
              .subcategoriaId(texto(p, "subcategoria", "subcategoriaNombre", "subcategoriaID", "subcategoriaId"))
              .build();
          clasificarConElProducto(donado);
          productos.add(donado);
        }
      }
    }
    return productos;
  }

  /**
   * Eventos de producto de Donaciones (PRODUCTO_CREADO, PRODUCTO_MODIFICADO, PRODUCTO_ELIMINADO): traen la
   * subcategoría y la categoría del producto. Con eso se clasifican las necesidades de ese producto (para poder
   * filtrarlas por categoría) y las donaciones que ya lo incluyen (para la proporción de quejas por categoría).
   */
  private void handleProducto(EventoGenerico evento) {
    if (productoRepository == null) {
      descartar(MOTIVO_CATALOGO);
      return;
    }
    Map<String, Object> datos = evento.getProducto() != null ? evento.getProducto() : evento.getExtra();
    String productoId = evento.getIdReferencia() != null ? evento.getIdReferencia() : texto(datos, "id");
    if (productoId == null) {
      descartar(MOTIVO_INVALIDO);
      return;
    }

    if (evento.getTipo().toUpperCase().contains("ELIMINADO")) {
      productoRepository.deleteById(productoId);
      return;
    }

    ProductoDocument doc = productoRepository.findById(productoId)
        .orElseGet(() -> ProductoDocument.builder().id(productoId).build());
    if (noEsMasNuevo(doc.getUltimaActualizacion(), evento)) return;
    doc.setNombre(texto(datos, "nombre"));
    doc.setCategoriaId(texto(datos, "categoriaID", "categoriaId"));
    doc.setCategoria(texto(datos, "categoria"));
    doc.setSubcategoriaId(texto(datos, "subcategoriaID", "subcategoriaId"));
    doc.setSubcategoria(texto(datos, "subcategoria"));
    doc.setUltimaActualizacion(evento.getTimestamp());
    productoRepository.save(doc);

    for (NecesidadUrgenteDocument necesidad : necesidadRepository.findByProductoSolicitadoId(productoId)) {
      aplicarProducto(necesidad, doc);
      necesidadRepository.save(necesidad);
    }
    if (donacionRepository != null) {
      for (DonacionDocument donacion : donacionRepository.findByProductosProductoId(productoId)) {
        for (ProductoDonado donado : donacion.getProductos()) {
          if (productoId.equals(donado.getProductoId())) {
            if (doc.etiquetaCategoria() != null) donado.setCategoriaId(doc.etiquetaCategoria());
            if (doc.etiquetaSubcategoria() != null) donado.setSubcategoriaId(doc.etiquetaSubcategoria());
          }
        }
        donacionRepository.save(donacion);
      }
    }
  }

  /** Copia el nombre y la clasificación del producto a la necesidad (solo lo que el producto sabe). */
  private static void aplicarProducto(NecesidadUrgenteDocument necesidad, ProductoDocument producto) {
    if (producto.getNombre() != null) necesidad.setProductoNombre(producto.getNombre());
    if (producto.etiquetaCategoria() != null) necesidad.setCategoriaProducto(producto.etiquetaCategoria());
    if (producto.etiquetaSubcategoria() != null) necesidad.setSubcategoriaProducto(producto.etiquetaSubcategoria());
  }

  /** Si el detalle de la donación no trae categoría o subcategoría, se usa la del producto si ya se conoce. */
  private void clasificarConElProducto(ProductoDonado donado) {
    if ((donado.getCategoriaId() != null && donado.getSubcategoriaId() != null)
        || productoRepository == null || donado.getProductoId() == null) {
      return;
    }
    productoRepository.findById(donado.getProductoId()).ifPresent(conocido -> {
      if (donado.getCategoriaId() == null) donado.setCategoriaId(conocido.etiquetaCategoria());
      if (donado.getSubcategoriaId() == null) donado.setSubcategoriaId(conocido.etiquetaSubcategoria());
    });
  }

  /** Donaciones por estado y entidades beneficiadas de un donador, calculadas desde sus donaciones. */
  private void recalcularDonador(String donadorId) {
    if (donadorId == null || donacionRepository == null) return;
    DonadorDocument donador = donadorRepository.findById(donadorId)
        .orElseGet(() -> DonadorDocument.builder().id(donadorId).build());
    List<DonacionDocument> donaciones = donacionRepository.findByDonadorId(donadorId);

    Map<String, Integer> porEstado = new LinkedHashMap<>();
    porEstado.put("INGRESADA", 0);
    porEstado.put("ACEPTADA", 0);
    porEstado.put("CONQUEJA", 0);
    for (DonacionDocument d : donaciones) {
      if (d.getEstado() != null) porEstado.merge(d.getEstado().toUpperCase(), 1, Integer::sum);
    }
    donador.setDonacionesPorEstado(porEstado);

    if (asignacionRepository != null) {
      Map<String, Integer> unidades = new LinkedHashMap<>();
      Map<String, String> razones = new HashMap<>();
      if (!donaciones.isEmpty()) {
        List<String> ids = donaciones.stream().map(DonacionDocument::getId).toList();
        for (AsignacionDocument a : asignacionRepository.findByDonacionIdIn(ids)) {
          if ("COMPLETADA".equalsIgnoreCase(a.getEstado()) && a.getEntidadId() != null) {
            unidades.merge(a.getEntidadId(), a.getCantidad() == null ? 0 : a.getCantidad(), Integer::sum);
            if (a.getEntidadRazonSocial() != null) razones.put(a.getEntidadId(), a.getEntidadRazonSocial());
          }
        }
      }
      List<EntidadBeneficiadaResumen> beneficiadas = new ArrayList<>();
      unidades.forEach((entidad, total) -> beneficiadas.add(EntidadBeneficiadaResumen.builder()
          .entidadId(entidad).razonSocial(razones.get(entidad)).unidades(total).build()));
      donador.setEntidadesBeneficiadas(beneficiadas);
    }
    donadorRepository.save(donador);
  }

  /** Asignaciones por estado, tiempo promedio de entrega y entidades atendidas de un depósito. */
  private void recalcularDeposito(String depositoId) {
    if (depositoId == null || donacionRepository == null || asignacionRepository == null) return;
    depositoRepository.findById(depositoId).ifPresent(deposito -> {
      List<DonacionDocument> donaciones = donacionRepository.findByDepositoId(depositoId);
      List<AsignacionDocument> asignaciones = donaciones.isEmpty()
          ? List.of()
          : asignacionRepository.findByDonacionIdIn(donaciones.stream().map(DonacionDocument::getId).toList());
      EstadisticasDeposito e = EstadisticasDeposito.calcular(donaciones, asignaciones, null, null);
      deposito.setAsignacionesPorEstado(e.getAsignacionesPorEstado());
      deposito.setTiempoPromedioEntregaMinutos(e.getTiempoPromedioEntregaMinutos());
      deposito.setEntidadesDistintasAtendidas(e.getEntidadesDistintas());
      depositoRepository.save(deposito);
    });
  }

  /**
   * Alta de una necesidad (Donadores publica {@code necesidad{id, entidadID, nivelDeUrgencia, descripcion,
   * cantidadObjetivo, productoSolicitadoID, tipo}}). Actualiza solo sus campos: el stock por depósito y las
   * unidades asignadas pendientes son de Logística y no se pisan.
   *
   * <p>En la práctica el evento llega después de la asignación (Donadores le pide a Logística que asigne
   * antes de publicarlo), así que al registrar la necesidad se completan las asignaciones que ya estaban
   * guardadas con la entidad, se calculan los pendientes y, si la necesidad no tenía stock, se toma del
   * último stock conocido de cada depósito.
   *
   * <p>Los datos de alta (entidad, producto, tipo, urgencia) no cambian después, así que se completan aunque
   * el evento llegue tarde. Las cantidades sí cambian (NECESIDAD_MODIFICADA / SATISFECHA): solo se toman si
   * el evento no es más viejo que el último de Donadores ya aplicado.
   */
  private void handleNecesidadRegistrada(EventoGenerico evento) {
    Map<String, Object> nec = evento.getNecesidad() != null ? evento.getNecesidad() : evento.getExtra();
    String necesidadId = evento.getIdReferencia() != null ? evento.getIdReferencia() : texto(nec, "id");
    if (necesidadId == null) return;

    NecesidadUrgenteDocument doc = necesidadRepository.findById(necesidadId)
        .orElseGet(() -> NecesidadUrgenteDocument.builder().id(necesidadId).build());

    String entidadId = texto(nec, "entidadID", "entidadId");
    if (entidadId != null) doc.setEntidadId(entidadId);
    String productoId = texto(nec, "productoSolicitadoID", "productoSolicitadoId");
    if (productoId != null) doc.setProductoSolicitadoId(productoId);
    String tipoNecesidad = texto(nec, "tipo", "tipoNecesidad");
    if (tipoNecesidad != null) doc.setTipoNecesidad(tipoNecesidad);
    Integer urgencia = numero(nec, "nivelDeUrgencia", "urgencia");
    if (urgencia != null) doc.setUrgencia(urgencia);
    // Hoy Donadores no los publica; se toman si algún día vienen en el evento.
    if (texto(nec, "productoNombre") != null) doc.setProductoNombre(texto(nec, "productoNombre"));
    if (texto(nec, "categoriaProducto") != null) doc.setCategoriaProducto(texto(nec, "categoriaProducto"));
    if (texto(nec, "subcategoriaProducto") != null) doc.setSubcategoriaProducto(texto(nec, "subcategoriaProducto"));
    if (productoRepository != null && doc.getProductoSolicitadoId() != null) {
      productoRepository.findById(doc.getProductoSolicitadoId()).ifPresent(p -> aplicarProducto(doc, p));
    }
    String razonSocial = texto(nec, "entidadRazonSocial");
    doc.setEntidadRazonSocial(razonSocial != null ? razonSocial : razonSocialDe(doc.getEntidadId(), doc.getEntidadRazonSocial()));

    Integer cantidad = numero(nec, "cantidadObjetivo");
    if (!esAnterior(doc.getUltimaActualizacion(), evento)) {
      if (cantidad != null) {
        doc.setCantidadObjetivo(cantidad);
        doc.setCantidadFaltante(cantidad);
        doc.setSatisfecha(cantidad <= 0);
      }
      doc.setUltimaActualizacion(evento.getTimestamp());
    } else if (doc.getCantidadObjetivo() == null) {
      doc.setCantidadObjetivo(cantidad);
    }

    if (doc.getStockPorDeposito() == null || doc.getStockPorDeposito().isEmpty()) {
      doc.setStockPorDeposito(stockConocidoDe(doc.getProductoSolicitadoId()));
    }
    if (asignacionRepository != null) {
      doc.setUnidadesAsignadasPendientes(pendientesDe(necesidadId));
    }
    necesidadRepository.save(doc);
    log.info("Necesidad urgente guardada en MongoDB: {}", necesidadId);

    completarAsignacionesDeLaNecesidad(doc);
  }

  /**
   * NECESIDAD_SATISFECHA trae {@code extra.cantidadObjetivoRestante} y NECESIDAD_MODIFICADA trae
   * {@code extra.cantidadObjetivo}. En Donadores la cantidad objetivo es lo que todavía falta (satisfacer
   * la descuenta), así que las dos son la cantidad faltante; la modificación además es el nuevo objetivo.
   */
  private void handleNecesidadModificada(EventoGenerico evento) {
    String necesidadId = evento.getIdReferencia();
    if (necesidadId == null) return;

    NecesidadUrgenteDocument doc = necesidadRepository.findById(necesidadId)
        .orElseGet(() -> NecesidadUrgenteDocument.builder().id(necesidadId).build());

    if (esObsoleto(doc.getUltimaActualizacion(), evento)) return;

    Integer restante = numero(evento.getExtra(), "cantidadObjetivoRestante");
    Integer nuevoObjetivo = numero(evento.getExtra(), "cantidadObjetivo");
    Integer faltante = restante != null ? restante : nuevoObjetivo;
    if (restante == null && nuevoObjetivo != null) {
      doc.setCantidadObjetivo(nuevoObjetivo);
    }
    if (faltante != null) {
      doc.setCantidadFaltante(faltante);
      doc.setSatisfecha(faltante <= 0);
    }
    doc.setUltimaActualizacion(evento.getTimestamp());
    necesidadRepository.save(doc);
  }

  /** Unidades de un producto en cada depósito, según el último stock que publicó Logística. */
  private Map<String, Integer> stockConocidoDe(String productoId) {
    Map<String, Integer> stock = new HashMap<>();
    if (productoId == null) return stock;
    for (DepositoDocument deposito : depositoRepository.findAll()) {
      Integer cantidad = deposito.getStockPorProducto() == null ? null : deposito.getStockPorProducto().get(productoId);
      if (cantidad != null && cantidad > 0) stock.put(deposito.getId(), cantidad);
    }
    return stock;
  }

  /**
   * Las asignaciones que llegaron antes que su necesidad quedaron sin entidad: se completan y se recalculan
   * las entidades beneficiadas de los donadores y las entidades atendidas de los depósitos afectados.
   */
  private void completarAsignacionesDeLaNecesidad(NecesidadUrgenteDocument necesidad) {
    if (asignacionRepository == null) return;
    Set<String> donaciones = new LinkedHashSet<>();
    for (AsignacionDocument a : asignacionRepository.findByNecesidadId(necesidad.getId())) {
      boolean cambia = !Objects.equals(a.getEntidadId(), necesidad.getEntidadId())
          || !Objects.equals(a.getEntidadRazonSocial(), necesidad.getEntidadRazonSocial());
      if (cambia) {
        a.setEntidadId(necesidad.getEntidadId());
        a.setEntidadRazonSocial(necesidad.getEntidadRazonSocial());
        asignacionRepository.save(a);
        if (a.getDonacionId() != null) donaciones.add(a.getDonacionId());
      }
    }
    recalcularPorDonaciones(donaciones);
  }

  /** Recalcula los donadores y los depósitos de esas donaciones. */
  private void recalcularPorDonaciones(Collection<String> donacionIds) {
    if (donacionRepository == null || donacionIds.isEmpty()) return;
    Set<String> donadores = new LinkedHashSet<>();
    Set<String> depositos = new LinkedHashSet<>();
    for (String id : donacionIds) {
      donacionRepository.findById(id).ifPresent(d -> {
        if (d.getDonadorId() != null) donadores.add(d.getDonadorId());
        if (d.getDepositoId() != null) depositos.add(d.getDepositoId());
      });
    }
    donadores.forEach(this::recalcularDonador);
    depositos.forEach(this::recalcularDeposito);
  }

  // ----- Entidades (Donadores y Entidades) -----

  /**
   * ENTIDAD_CREADA trae {@code entidad{id, razonSocial, ...}}; ENTIDAD_RAZON_MODIFICADA trae
   * {@code extra.razonSocial}. La razón social se propaga a las necesidades y asignaciones de la entidad.
   * Al eliminar una entidad se borra su documento pero las asignaciones conservan la razón social: las
   * entregas que ya recibió siguen siendo parte de la historia de cada donador.
   *
   * <p>ENTIDADES_TODAS_ELIMINADAS borra también todas las necesidades: en Donadores el borrado total de
   * entidades se lleva sus necesidades (FK) y no publica NECESIDAD_ELIMINADA por cada una.
   */
  private void handleEntidad(EventoGenerico evento) {
    String tipo = evento.getTipo().toUpperCase();
    if (tipo.equals("ENTIDADES_TODAS_ELIMINADAS")) {
      if (entidadRepository != null) entidadRepository.deleteAll();
      necesidadRepository.deleteAll();
      log.info("Todas las entidades y sus necesidades eliminadas del modelo de lectura");
      return;
    }
    if (entidadRepository == null) return;
    Map<String, Object> datos = evento.getEntidad() != null ? evento.getEntidad() : evento.getExtra();
    String entidadId = evento.getIdReferencia() != null ? evento.getIdReferencia() : texto(datos, "id");
    if (entidadId == null) return;

    if (tipo.contains("ELIMINADA")) {
      entidadRepository.deleteById(entidadId);
      log.info("Entidad eliminada de MongoDB: {}", entidadId);
      return;
    }

    EntidadDocument doc = entidadRepository.findById(entidadId)
        .orElseGet(() -> EntidadDocument.builder().id(entidadId).build());
    if (esObsoleto(doc.getUltimaActualizacion(), evento)) return;

    String razonSocial = texto(datos, "razonSocial");
    if (razonSocial != null) doc.setRazonSocial(razonSocial);
    doc.setUltimaActualizacion(evento.getTimestamp());
    entidadRepository.save(doc);
    log.info("Entidad guardada en MongoDB: {}", entidadId);

    propagarRazonSocial(entidadId, doc.getRazonSocial());
  }

  private void propagarRazonSocial(String entidadId, String razonSocial) {
    if (razonSocial == null) return;
    for (NecesidadUrgenteDocument n : necesidadRepository.findByEntidadId(entidadId)) {
      if (!razonSocial.equals(n.getEntidadRazonSocial())) {
        n.setEntidadRazonSocial(razonSocial);
        necesidadRepository.save(n);
      }
    }
    if (asignacionRepository == null) return;
    Set<String> donaciones = new LinkedHashSet<>();
    for (AsignacionDocument a : asignacionRepository.findByEntidadId(entidadId)) {
      if (!razonSocial.equals(a.getEntidadRazonSocial())) {
        a.setEntidadRazonSocial(razonSocial);
        asignacionRepository.save(a);
        if (a.getDonacionId() != null) donaciones.add(a.getDonacionId());
      }
    }
    recalcularPorDonaciones(donaciones);
  }

  /** La razón social guardada de la entidad; si no está, la que ya se tenía. */
  private String razonSocialDe(String entidadId, String actual) {
    if (entidadId == null || entidadRepository == null) return actual;
    return entidadRepository.findById(entidadId)
        .map(EntidadDocument::getRazonSocial)
        .filter(Objects::nonNull)
        .orElse(actual);
  }

  /** Donadores vació su base: se borran los donadores del modelo de lectura. */
  private void handleDonadoresTodosEliminados() {
    donadorRepository.deleteAll();
    log.info("Donadores eliminados del modelo de lectura");
  }

  private void handleNecesidadEliminada(EventoGenerico evento) {
    String necesidadId = evento.getIdReferencia();
    if (necesidadId != null) {
      necesidadRepository.deleteById(necesidadId);
      log.info("Necesidad eliminada de MongoDB: {}", necesidadId);
    }
  }

  /**
   * Depósito tal como lo publica Logística: id, nombre, direccion, capacidadMaxima, unidadesOcupadas,
   * algoritmo y la lista stock de {paqueteId, donacionId, productoId, cantidad}. También acepta los
   * nombres anteriores (capacidadTotal, stockActual, algoritmoMatchmaking).
   */
  private void handleDeposito(EventoGenerico evento) {
    String tipo = evento.getTipo().toUpperCase();
    Map<String, Object> dep = evento.getDeposito() != null ? evento.getDeposito() : evento.getExtra();
    String depositoId = evento.getIdReferencia() != null ? evento.getIdReferencia() : texto(dep, "id");
    if (depositoId == null) return;

    if (tipo.contains("ELIMINADO")) {
      depositoRepository.deleteById(depositoId);
      quitarDepositoDeLasNecesidades(depositoId);
      log.info("Depósito eliminado de MongoDB: {}", depositoId);
      return;
    }

    DepositoDocument doc = depositoRepository.findById(depositoId)
        .orElseGet(() -> DepositoDocument.builder().id(depositoId).build());
    if (noEsMasNuevo(doc.getUltimaActualizacion(), evento)) return;

    if (texto(dep, "nombre") != null) doc.setNombre(texto(dep, "nombre"));
    Integer capacidad = numero(dep, "capacidadMaxima", "capacidadTotal");
    if (capacidad != null) doc.setCapacidadTotal(capacidad);
    Integer ocupadas = numero(dep, "unidadesOcupadas", "stockActual");
    if (ocupadas != null) doc.setStockActual(ocupadas);
    if (dep.containsKey("algoritmo") || dep.containsKey("algoritmoMatchmaking")) {
      doc.setAlgoritmoMatchmaking(texto(dep, "algoritmo", "algoritmoMatchmaking"));
    }
    if (doc.getCapacidadTotal() != null && doc.getCapacidadTotal() > 0 && doc.getStockActual() != null) {
      doc.setOcupacionPorcentaje((double) doc.getStockActual() / doc.getCapacidadTotal() * 100.0);
    }
    if (dep.containsKey("entidadAtendidaId")) {
      doc.getEntidadesDistintasAtendidas().add((String) dep.get("entidadAtendidaId"));
    }

    Map<String, Integer> porProducto = null;
    if (dep.get("stock") instanceof List<?> stock) {
      porProducto = agruparStock(stock);
      doc.setStockPorProducto(porProducto);
    }

    doc.setUltimaActualizacion(evento.getTimestamp());
    depositoRepository.save(doc);

    if (porProducto != null) {
      actualizarStockPorDeposito(depositoId, porProducto);
    }
  }

  /** Las necesidades guardan cuánto stock de su producto hay en cada depósito (consulta 2). */
  private void actualizarStockPorDeposito(String depositoId, Map<String, Integer> porProducto) {
    for (NecesidadUrgenteDocument necesidad : necesidadRepository.findAll()) {
      Integer actual = necesidad.getStockPorDeposito().get(depositoId);
      Integer nuevo = porProducto.get(necesidad.getProductoSolicitadoId());
      if (nuevo == null && actual != null) {
        necesidad.getStockPorDeposito().remove(depositoId);
        necesidadRepository.save(necesidad);
      } else if (nuevo != null && !nuevo.equals(actual)) {
        necesidad.getStockPorDeposito().put(depositoId, nuevo);
        necesidadRepository.save(necesidad);
      }
    }
  }

  /** Suma las unidades de cada producto de la lista de paquetes que trae el evento del depósito. */
  private static Map<String, Integer> agruparStock(List<?> stock) {
    Map<String, Integer> porProducto = new HashMap<>();
    for (Object item : stock) {
      if (item instanceof Map<?, ?> paquete) {
        Object producto = paquete.get("productoId");
        Object cantidad = paquete.get("cantidad");
        if (producto != null && cantidad instanceof Number n) {
          porProducto.merge(String.valueOf(producto), n.intValue(), Integer::sum);
        }
      }
    }
    return porProducto;
  }

  private void quitarDepositoDeLasNecesidades(String depositoId) {
    for (NecesidadUrgenteDocument necesidad : necesidadRepository.findAll()) {
      if (necesidad.getStockPorDeposito().remove(depositoId) != null) {
        necesidadRepository.save(necesidad);
      }
    }
  }

  /**
   * ASIGNACION_CREADA y ENTREGA_COMPLETADA traen el estado completo de la asignación: se reemplaza
   * el documento, salvo que el evento sea más viejo que lo guardado.
   */
  private void handleAsignacion(EventoGenerico evento) {
    if (asignacionRepository == null) return;
    Map<String, Object> a = evento.getAsignacion() != null ? evento.getAsignacion() : evento.getExtra();
    String id = evento.getIdReferencia() != null ? evento.getIdReferencia() : texto(a, "id");
    if (id == null) return;

    AsignacionDocument doc = asignacionRepository.findById(id)
        .orElseGet(() -> AsignacionDocument.builder().id(id).build());
    if (noEsMasNuevo(doc.getUltimaActualizacion(), evento)) return;

    doc.setPaqueteId(texto(a, "paqueteId"));
    doc.setDonacionId(texto(a, "donacionId"));
    doc.setProductoId(texto(a, "productoId"));
    doc.setCantidad(numero(a, "cantidad"));
    doc.setNecesidadId(texto(a, "necesidadId"));
    doc.setOrigen(texto(a, "origen"));
    doc.setEstado(texto(a, "estado"));
    doc.setFechaAsignacion(instante(a, "fechaAsignacion"));
    doc.setFechaEntrega(instante(a, "fechaEntrega"));
    if (doc.getNecesidadId() != null) {
      // Si la necesidad todavía no llegó, la entidad se completa cuando llegue NECESIDAD_REGISTRADA.
      necesidadRepository.findById(doc.getNecesidadId()).ifPresent(n -> {
        doc.setEntidadId(n.getEntidadId());
        doc.setEntidadRazonSocial(razonSocialDe(n.getEntidadId(), n.getEntidadRazonSocial()));
      });
    }
    doc.setUltimaActualizacion(evento.getTimestamp());
    asignacionRepository.save(doc);
    log.info("Asignación guardada en MongoDB: {} ({})", id, doc.getEstado());

    actualizarPendientesDeLaNecesidad(doc.getNecesidadId());
    if (donacionRepository != null && doc.getDonacionId() != null) {
      donacionRepository.findById(doc.getDonacionId()).ifPresent(d -> {
        recalcularDonador(d.getDonadorId());
        recalcularDeposito(d.getDepositoId());
      });
    }
  }

  /** Unidades ya asignadas a la necesidad que todavía no se entregaron (consulta 2). */
  private void actualizarPendientesDeLaNecesidad(String necesidadId) {
    if (necesidadId == null) return;
    int pendientes = pendientesDe(necesidadId);
    necesidadRepository.findById(necesidadId).ifPresent(n -> {
      n.setUnidadesAsignadasPendientes(pendientes);
      necesidadRepository.save(n);
    });
  }

  private int pendientesDe(String necesidadId) {
    return asignacionRepository.findByNecesidadId(necesidadId).stream()
        .filter(x -> "ASIGNADA".equalsIgnoreCase(x.getEstado()))
        .mapToInt(x -> x.getCantidad() == null ? 0 : x.getCantidad())
        .sum();
  }

  /** Se reinició la base de Logística: se borra lo que Logística había aportado al modelo de lectura. */
  private void handleBaseLimpiada() {
    depositoRepository.deleteAll();
    if (asignacionRepository != null) asignacionRepository.deleteAll();
    for (NecesidadUrgenteDocument necesidad : necesidadRepository.findAll()) {
      necesidad.getStockPorDeposito().clear();
      necesidad.setUnidadesAsignadasPendientes(0);
      necesidadRepository.save(necesidad);
    }
    if (donacionRepository != null) {
      for (DonacionDocument d : donacionRepository.findAll()) {
        recalcularDonador(d.getDonadorId());
      }
    }
    log.info("Modelo de lectura de Logística reiniciado");
  }

  // ----- idempotencia: un evento repetido no se aplica dos veces -----

  private boolean yaProcesado(EventoGenerico evento) {
    return eventosProcesados != null && evento.getId() != null && eventosProcesados.existsById(evento.getId());
  }

  private void marcarProcesado(EventoGenerico evento) {
    if (eventosProcesados != null && evento.getId() != null) {
      eventosProcesados.save(
          EventoProcesado.builder().id(evento.getId()).procesadoEn(Instant.now()).build());
    }
  }

  /** Igual o más viejo que lo guardado: no aporta nada (cubre repetidos y desordenados). */
  private boolean noEsMasNuevo(Instant ultimaActualizacion, EventoGenerico evento) {
    boolean noAporta = ultimaActualizacion != null
        && evento.getTimestamp() != null
        && !evento.getTimestamp().isAfter(ultimaActualizacion);
    if (noAporta) descartar(MOTIVO_OBSOLETO);
    return noAporta;
  }

  // ----- lectura segura de los mapas que llegan en el evento -----

  private static String texto(Map<String, Object> mapa, String... claves) {
    for (String clave : claves) {
      Object valor = mapa.get(clave);
      if (valor != null) return String.valueOf(valor);
    }
    return null;
  }

  private static Integer numero(Map<String, Object> mapa, String... claves) {
    for (String clave : claves) {
      if (mapa.get(clave) instanceof Number n) return n.intValue();
    }
    return null;
  }

  private static Instant instante(Map<String, Object> mapa, String clave) {
    Object valor = mapa.get(clave);
    if (valor == null) return null;
    try {
      return Instant.parse(String.valueOf(valor));
    } catch (Exception e) {
      return null;
    }
  }

  /** Día UTC (yyyy-MM-dd) del evento, para el historial de categorías. */
  private static String diaDe(EventoGenerico evento) {
    Instant cuando = evento.getTimestamp() != null ? evento.getTimestamp() : Instant.now();
    return LocalDate.ofInstant(cuando, ZoneOffset.UTC).toString();
  }

  /** Más viejo que lo guardado: se descarta (y se cuenta como ignorado por obsoleto). */
  private boolean esObsoleto(java.time.Instant ultimaActualizacion, EventoGenerico evento) {
    boolean obsoleto = esAnterior(ultimaActualizacion, evento);
    if (obsoleto) descartar(MOTIVO_OBSOLETO);
    return obsoleto;
  }

  /**
   * Solo compara: para los handlers que, con un evento atrasado, igual aplican una parte (la queja se cuenta
   * aunque no cambie el estado; la necesidad completa sus datos de alta aunque no toque las cantidades).
   */
  private static boolean esAnterior(Instant ultimaActualizacion, EventoGenerico evento) {
    return ultimaActualizacion != null
        && evento.getTimestamp() != null
        && ultimaActualizacion.isAfter(evento.getTimestamp());
  }

  /** El evento que se está procesando no se aplicó; {@link #procesar} lo cuenta como ignorado. */
  private static void descartar(String motivo) {
    DESCARTE.set(motivo);
  }
}

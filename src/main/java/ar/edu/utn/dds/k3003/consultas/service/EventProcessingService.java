package ar.edu.utn.dds.k3003.consultas.service;

import ar.edu.utn.dds.k3003.consultas.documents.*;
import ar.edu.utn.dds.k3003.consultas.messaging.EventoGenerico;
import ar.edu.utn.dds.k3003.consultas.repositories.AsignacionDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonacionDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.EntidadDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.EventoProcesadoRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.CalidadDonacionRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DepositoDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonadorDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.NecesidadDocumentRepository;
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
  private final CalidadDonacionRepository calidadRepository;

  // Se inyectan por campo (y son opcionales) para no cambiar el constructor que usan los tests.
  @Autowired(required = false)
  private AsignacionDocumentRepository asignacionRepository;

  @Autowired(required = false)
  private EventoProcesadoRepository eventosProcesados;

  @Autowired(required = false)
  private DonacionDocumentRepository donacionRepository;

  @Autowired(required = false)
  private EntidadDocumentRepository entidadRepository;

  public EventProcessingService(
      DonadorDocumentRepository donadorRepository,
      NecesidadDocumentRepository necesidadRepository,
      DepositoDocumentRepository depositoRepository,
      CalidadDonacionRepository calidadRepository) {
    this.donadorRepository = donadorRepository;
    this.necesidadRepository = necesidadRepository;
    this.depositoRepository = depositoRepository;
    this.calidadRepository = calidadRepository;
  }

  public void procesar(EventoGenerico evento) {
    if (evento == null || evento.getTipo() == null) {
      return;
    }

    String tipo = evento.getTipo().toUpperCase();
    if (yaProcesado(evento)) {
      log.info("Evento repetido '{}' [ID: {}], se ignora", tipo, evento.getId());
      return;
    }
    log.info("Procesando evento CQRS '{}' para referencia '{}' [ID: {}]", tipo, evento.getIdReferencia(), evento.getId());

    try {
      if (tipo.equals("DONADOR_ACTUALIZADO")) {
        handleDonadorActualizado(evento);
      } else if (tipo.startsWith("INSIGNIA_") || tipo.startsWith("MISION_")) {
        // Cambios del catálogo de Incentivos: no son de un donador. Lo que cada donador tiene llega
        // completo en DONADOR_ACTUALIZADO.
        log.info("Evento de catálogo de Incentivos '{}' [{}] ignorado: no modifica donadores",
            tipo, evento.getIdReferencia());
      } else if (tipo.equals("BASE_LIMPIADA") && ORIGEN_INCENTIVOS.equalsIgnoreCase(evento.getIdReferencia())) {
        handleBaseLimpiadaIncentivos(evento);
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
      } else if (tipo.startsWith("ENTIDAD_")) {
        handleEntidad(evento);
      } else if (tipo.equals("ENTIDADES_TODAS_ELIMINADAS")) {
        handleEntidadesTodasEliminadas();
      } else if (tipo.equals("DONADORES_TODOS_ELIMINADOS")) {
        donadorRepository.deleteAll();
        log.info("Todos los donadores eliminados del modelo de lectura");
      } else {
        log.warn("Evento no mapeado explícitamente: {}", tipo);
      }
      marcarProcesado(evento);
    } catch (Exception e) {
      log.error("Error al procesar evento '{}' en MongoDB: {}", tipo, e.getMessage(), e);
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
        doc.getHistorialCategorias().add(HistorialCategoria.builder().categoria(cat).desde(LocalDate.now().toString()).build());
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
      doc.getHistorialCategorias().add(HistorialCategoria.builder().categoria(nuevaCategoria).desde(LocalDate.now().toString()).build());
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

  private void handleQuejaRegistrada(EventoGenerico evento) {
    Map<String, Object> queja = evento.getQueja() != null ? evento.getQueja() : evento.getExtra();
    String donadorId = evento.getIdReferencia() != null ? evento.getIdReferencia() : (String) queja.get("donadorID");

    if (donadorId != null) {
      DonadorDocument donador = donadorRepository.findById(donadorId)
          .orElseGet(() -> DonadorDocument.builder().id(donadorId).build());

      int q = (donador.getQuejasRecibidas() != null ? donador.getQuejasRecibidas() : 0) + 1;
      donador.setQuejasRecibidas(q);
      if (q >= 10) donador.setEstado("BANEADO");
      else if (q >= 5) donador.setEstado("SOSPECHOSO");

      donador.setUltimaActualizacion(evento.getTimestamp());
      donadorRepository.save(donador);
    }

    String categoria = (String) queja.get("categoriaProducto");
    String subcategoria = (String) queja.get("subcategoriaProducto");
    if (categoria != null) {
      String key = categoria + "_" + (subcategoria != null ? subcategoria : "general");
      CalidadDonacionDocument cal = calidadRepository.findById(key)
          .orElseGet(() -> CalidadDonacionDocument.builder().id(key).categoriaProducto(categoria).subcategoriaProducto(subcategoria).totalDonaciones(0).donacionesConQueja(0).build());

      cal.setDonacionesConQueja(cal.getDonacionesConQueja() + 1);
      cal.recalcularProporcion();
      cal.setUltimaActualizacion(evento.getTimestamp());
      calidadRepository.save(cal);
    }
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
      recalcularCalidad();
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
    recalcularCalidad();
  }

  @SuppressWarnings("unchecked")
  private List<ProductoDonado> productosDe(Map<String, Object> don) {
    Object lista = don.get("detallesProductosDTO") != null ? don.get("detallesProductosDTO") : don.get("productos");
    List<ProductoDonado> productos = new ArrayList<>();
    if (lista instanceof List<?> items) {
      for (Object item : items) {
        if (item instanceof Map<?, ?> m) {
          Map<String, Object> p = (Map<String, Object>) m;
          productos.add(ProductoDonado.builder()
              .productoId(texto(p, "productoID", "productoId"))
              .cantidad(numero(p, "cantidadProducto", "cantidad"))
              .categoriaId(texto(p, "categoriaID", "categoriaId", "categoria"))
              .subcategoriaId(texto(p, "subcategoriaID", "subcategoriaId", "subcategoria"))
              .build());
        }
      }
    }
    return productos;
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

  /** Proporción de donaciones con queja por categoría y subcategoría, calculada desde todas las donaciones. */
  private void recalcularCalidad() {
    if (donacionRepository == null) return;
    Map<String, CalidadDonacionDocument> acumulado = new LinkedHashMap<>();
    for (DonacionDocument d : donacionRepository.findAll()) {
      Set<String> claves = new LinkedHashSet<>();
      Map<String, String[]> partes = new HashMap<>();
      for (ProductoDonado p : d.getProductos()) {
        String categoria = p.getCategoriaId() != null ? p.getCategoriaId() : "SIN_CATEGORIA";
        String subcategoria = p.getSubcategoriaId() != null ? p.getSubcategoriaId() : "general";
        String clave = categoria + "_" + subcategoria;
        claves.add(clave);
        partes.put(clave, new String[] {categoria, subcategoria});
      }
      if (claves.isEmpty()) {
        claves.add("SIN_CATEGORIA_general");
        partes.put("SIN_CATEGORIA_general", new String[] {"SIN_CATEGORIA", "general"});
      }
      boolean conQueja = "CONQUEJA".equalsIgnoreCase(d.getEstado());
      for (String clave : claves) {
        CalidadDonacionDocument cal = acumulado.computeIfAbsent(clave, k -> CalidadDonacionDocument.builder()
            .id(k).categoriaProducto(partes.get(k)[0]).subcategoriaProducto(partes.get(k)[1])
            .totalDonaciones(0).donacionesConQueja(0).build());
        cal.setTotalDonaciones(cal.getTotalDonaciones() + 1);
        if (conQueja) cal.setDonacionesConQueja(cal.getDonacionesConQueja() + 1);
      }
    }
    Instant ahora = Instant.now();
    acumulado.values().forEach(cal -> {
      cal.recalcularProporcion();
      cal.setUltimaActualizacion(ahora);
    });
    calidadRepository.deleteAll();
    calidadRepository.saveAll(acumulado.values());
  }

  /**
   * Necesidad tal como la publica Donadores: id, entidadID, nivelDeUrgencia, descripcion, cantidadObjetivo,
   * productoSolicitadoID y tipo. La razón social sale de las entidades ya conocidas, el stock de lo que ya
   * hay en los depósitos y las asignaciones que llegaron antes que la necesidad se completan con su entidad.
   */
  private void handleNecesidadRegistrada(EventoGenerico evento) {
    Map<String, Object> nec = evento.getNecesidad() != null ? evento.getNecesidad() : evento.getExtra();
    String necesidadId = evento.getIdReferencia() != null ? evento.getIdReferencia() : (String) nec.get("id");
    if (necesidadId == null) return;

    Integer cantidad = numero(nec, "cantidadObjetivo");
    if (cantidad == null) cantidad = 0;
    Integer urgencia = numero(nec, "nivelDeUrgencia", "urgencia");
    if (urgencia == null) urgencia = 5;
    String entidadId = texto(nec, "entidadID", "entidadId");
    String razonSocial = texto(nec, "entidadRazonSocial");
    if (razonSocial == null && entidadId != null && entidadRepository != null) {
      razonSocial = entidadRepository.findById(entidadId).map(EntidadDocument::getRazonSocial).orElse(null);
    }

    NecesidadUrgenteDocument doc = NecesidadUrgenteDocument.builder()
        .id(necesidadId)
        .entidadId(entidadId)
        .entidadRazonSocial(razonSocial)
        .productoSolicitadoId(texto(nec, "productoSolicitadoID", "productoSolicitadoId"))
        .productoNombre(texto(nec, "productoNombre"))
        .categoriaProducto(texto(nec, "categoriaProducto"))
        .subcategoriaProducto(texto(nec, "subcategoriaProducto"))
        .tipoNecesidad(texto(nec, "tipo", "tipoNecesidad"))
        .urgencia(urgencia)
        .cantidadObjetivo(cantidad)
        .cantidadFaltante(cantidad)
        .satisfecha(false)
        .ultimaActualizacion(evento.getTimestamp())
        .build();

    for (DepositoDocument deposito : depositoRepository.findAll()) {
      Integer enStock = deposito.getStockPorProducto() == null || doc.getProductoSolicitadoId() == null
          ? null
          : deposito.getStockPorProducto().get(doc.getProductoSolicitadoId());
      if (enStock != null && enStock > 0) doc.getStockPorDeposito().put(deposito.getId(), enStock);
    }
    necesidadRepository.save(doc);
    log.info("Necesidad urgente guardada en MongoDB: {}", necesidadId);

    if (asignacionRepository != null) {
      completarAsignacionesDeLaNecesidad(doc);
      actualizarPendientesDeLaNecesidad(necesidadId);
    }
  }

  /** Las asignaciones que llegaron antes que su necesidad no sabían la entidad: se la ponemos ahora. */
  private void completarAsignacionesDeLaNecesidad(NecesidadUrgenteDocument necesidad) {
    Set<String> donaciones = new LinkedHashSet<>();
    for (AsignacionDocument a : asignacionRepository.findByNecesidadId(necesidad.getId())) {
      a.setEntidadId(necesidad.getEntidadId());
      a.setEntidadRazonSocial(necesidad.getEntidadRazonSocial());
      asignacionRepository.save(a);
      if (a.getDonacionId() != null) donaciones.add(a.getDonacionId());
    }
    recalcularDonadoresDe(donaciones);
  }

  private void recalcularDonadoresDe(Collection<String> donacionIds) {
    if (donacionRepository == null) return;
    for (String id : donacionIds) {
      donacionRepository.findById(id).ifPresent(d -> recalcularDonador(d.getDonadorId()));
    }
  }

  /**
   * Eventos de entidades: ENTIDAD_CREADA trae la entidad completa, ENTIDAD_RAZON_MODIFICADA trae la nueva
   * razón social en extra y ENTIDAD_ELIMINADA solo el id. La razón social se copia a las necesidades y a
   * las asignaciones de la entidad.
   */
  private void handleEntidad(EventoGenerico evento) {
    Map<String, Object> datos = evento.getEntidad() != null ? evento.getEntidad() : evento.getExtra();
    String entidadId = evento.getIdReferencia() != null ? evento.getIdReferencia() : texto(datos, "id");
    if (entidadId == null) return;

    if (evento.getTipo().toUpperCase().contains("ELIMINADA")) {
      if (entidadRepository != null) entidadRepository.deleteById(entidadId);
      return;
    }
    String razonSocial = texto(datos, "razonSocial");
    if (razonSocial == null) return;

    if (entidadRepository != null) {
      EntidadDocument doc = entidadRepository.findById(entidadId)
          .orElseGet(() -> EntidadDocument.builder().id(entidadId).build());
      if (noEsMasNuevo(doc.getUltimaActualizacion(), evento)) return;
      doc.setRazonSocial(razonSocial);
      doc.setUltimaActualizacion(evento.getTimestamp());
      entidadRepository.save(doc);
    }

    for (NecesidadUrgenteDocument n : necesidadRepository.findByEntidadId(entidadId)) {
      n.setEntidadRazonSocial(razonSocial);
      necesidadRepository.save(n);
    }
    if (asignacionRepository != null) {
      Set<String> donaciones = new LinkedHashSet<>();
      for (AsignacionDocument a : asignacionRepository.findByEntidadId(entidadId)) {
        a.setEntidadRazonSocial(razonSocial);
        asignacionRepository.save(a);
        if (a.getDonacionId() != null) donaciones.add(a.getDonacionId());
      }
      recalcularDonadoresDe(donaciones);
    }
  }

  /** Donadores y Entidades borró todas las entidades y, con ellas, sus necesidades. */
  private void handleEntidadesTodasEliminadas() {
    if (entidadRepository != null) entidadRepository.deleteAll();
    necesidadRepository.deleteAll();
    log.info("Todas las entidades y sus necesidades eliminadas del modelo de lectura");
  }

  private void handleNecesidadModificada(EventoGenerico evento) {
    String necesidadId = evento.getIdReferencia();
    if (necesidadId == null) return;

    NecesidadUrgenteDocument doc = necesidadRepository.findById(necesidadId)
        .orElseGet(() -> NecesidadUrgenteDocument.builder().id(necesidadId).build());

    if (esObsoleto(doc.getUltimaActualizacion(), evento)) return;

    Object cantObj = evento.getExtra().get("cantidadObjetivoRestante");
    if (cantObj == null) cantObj = evento.getExtra().get("cantidadObjetivo");

    if (cantObj instanceof Number n) {
      int faltante = n.intValue();
      doc.setCantidadFaltante(faltante);
      if (faltante <= 0) {
        doc.setSatisfecha(true);
      }
    }
    doc.setUltimaActualizacion(evento.getTimestamp());
    necesidadRepository.save(doc);
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

    if (dep.get("stock") instanceof List<?> items) {
      doc.setStockPorProducto(agruparStock(items));
    }

    doc.setUltimaActualizacion(evento.getTimestamp());
    depositoRepository.save(doc);

    if (dep.get("stock") instanceof List<?> stock) {
      actualizarStockPorDeposito(depositoId, stock);
    }
  }

  /** Las necesidades guardan cuánto stock de su producto hay en cada depósito (consulta 2). */
  private void actualizarStockPorDeposito(String depositoId, List<?> stock) {
    Map<String, Integer> porProducto = agruparStock(stock);
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
      necesidadRepository.findById(doc.getNecesidadId()).ifPresent(n -> {
        doc.setEntidadId(n.getEntidadId());
        doc.setEntidadRazonSocial(n.getEntidadRazonSocial());
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
    int pendientes = asignacionRepository.findByNecesidadId(necesidadId).stream()
        .filter(x -> "ASIGNADA".equalsIgnoreCase(x.getEstado()))
        .mapToInt(x -> x.getCantidad() == null ? 0 : x.getCantidad())
        .sum();
    necesidadRepository.findById(necesidadId).ifPresent(n -> {
      n.setUnidadesAsignadasPendientes(pendientes);
      necesidadRepository.save(n);
    });
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
    return ultimaActualizacion != null
        && evento.getTimestamp() != null
        && !evento.getTimestamp().isAfter(ultimaActualizacion);
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

  private boolean esObsoleto(java.time.Instant ultimaActualizacion, EventoGenerico evento) {
    return ultimaActualizacion != null
        && evento.getTimestamp() != null
        && ultimaActualizacion.isAfter(evento.getTimestamp());
  }
}

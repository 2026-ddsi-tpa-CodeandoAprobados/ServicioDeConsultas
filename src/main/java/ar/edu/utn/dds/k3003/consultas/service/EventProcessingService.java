package ar.edu.utn.dds.k3003.consultas.service;

import ar.edu.utn.dds.k3003.consultas.documents.*;
import ar.edu.utn.dds.k3003.consultas.messaging.EventoGenerico;
import ar.edu.utn.dds.k3003.consultas.repositories.CalidadDonacionRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DepositoDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonadorDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.NecesidadDocumentRepository;
import java.time.LocalDate;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EventProcessingService {

  private final DonadorDocumentRepository donadorRepository;
  private final NecesidadDocumentRepository necesidadRepository;
  private final DepositoDocumentRepository depositoRepository;
  private final CalidadDonacionRepository calidadRepository;

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
    log.info("Procesando evento CQRS '{}' para referencia '{}' [ID: {}]", tipo, evento.getIdReferencia(), evento.getId());

    try {
      if (tipo.contains("DONADOR_CREADO")) {
        handleDonadorCreado(evento);
      } else if (tipo.contains("DONADOR_ESTADO")) {
        handleDonadorEstado(evento);
      } else if (tipo.contains("DONADOR_CATEGORIA")) {
        handleDonadorCategoria(evento);
      } else if (tipo.contains("DONADOR_ELIMINADO")) {
        handleDonadorEliminado(evento);
      } else if (tipo.contains("QUEJA_REGISTRADA") || tipo.contains("QUEJA")) {
        handleQuejaRegistrada(evento);
      } else if (tipo.contains("INSIGNIA")) {
        handleInsignia(evento);
      } else if (tipo.contains("MISION")) {
        handleMision(evento);
      } else if (tipo.contains("DONACION")) {
        handleDonacion(evento);
      } else if (tipo.contains("NECESIDAD_REGISTRADA")) {
        handleNecesidadRegistrada(evento);
      } else if (tipo.contains("NECESIDAD_MODIFICADA") || tipo.contains("NECESIDAD_SATISFECHA")) {
        handleNecesidadModificada(evento);
      } else if (tipo.contains("NECESIDAD_ELIMINADA")) {
        handleNecesidadEliminada(evento);
      } else if (tipo.contains("DEPOSITO")) {
        handleDeposito(evento);
      } else {
        log.warn("Evento no mapeado explícitamente: {}", tipo);
      }
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

  private void handleInsignia(EventoGenerico evento) {
    Map<String, Object> ins = evento.getInsignia() != null ? evento.getInsignia() : evento.getExtra();
    String donadorId = evento.getIdReferencia() != null ? evento.getIdReferencia() : (String) ins.get("donadorId");
    String nombreInsignia = (String) ins.get("nombre");
    if (nombreInsignia == null) nombreInsignia = (String) ins.get("id");

    if (donadorId != null && nombreInsignia != null) {
      DonadorDocument donador = donadorRepository.findById(donadorId)
          .orElseGet(() -> DonadorDocument.builder().id(donadorId).build());

      if (!donador.getInsignias().contains(nombreInsignia)) {
        donador.getInsignias().add(nombreInsignia);
        donador.setUltimaActualizacion(evento.getTimestamp());
        donadorRepository.save(donador);
      }
    }
  }

  private void handleMision(EventoGenerico evento) {
    Map<String, Object> mis = evento.getMision() != null ? evento.getMision() : evento.getExtra();
    String donadorId = evento.getIdReferencia() != null ? evento.getIdReferencia() : (String) mis.get("donadorId");

    if (donadorId != null) {
      DonadorDocument donador = donadorRepository.findById(donadorId)
          .orElseGet(() -> DonadorDocument.builder().id(donadorId).build());

      Integer progreso = mis.get("progreso") instanceof Number n ? n.intValue() : 0;
      Integer objetivo = mis.get("objetivo") instanceof Number n ? n.intValue() : 10;
      String nombre = (String) mis.get("nombre");

      donador.setMisionEnCurso(MisionEnCurso.builder().nombre(nombre).progreso(progreso).objetivo(objetivo).build());
      donador.setUltimaActualizacion(evento.getTimestamp());
      donadorRepository.save(donador);
    }
  }

  private void handleDonacion(EventoGenerico evento) {
    Map<String, Object> don = evento.getDonacion() != null ? evento.getDonacion() : evento.getExtra();
    String donadorId = (String) don.get("donadorId");
    String estadoDonacion = (String) don.get("estado");

    if (donadorId != null && estadoDonacion != null) {
      DonadorDocument donador = donadorRepository.findById(donadorId)
          .orElseGet(() -> DonadorDocument.builder().id(donadorId).build());

      int count = donador.getDonacionesPorEstado().getOrDefault(estadoDonacion, 0) + 1;
      donador.getDonacionesPorEstado().put(estadoDonacion, count);

      if ("ENTREGADA".equalsIgnoreCase(estadoDonacion) && don.containsKey("entidadId")) {
        String entId = (String) don.get("entidadId");
        String razon = (String) don.get("entidadRazonSocial");
        Integer unidades = don.get("unidades") instanceof Number n ? n.intValue() : 1;

        donador.getEntidadesBeneficiadas().add(
            EntidadBeneficiadaResumen.builder().entidadId(entId).razonSocial(razon).unidades(unidades).build()
        );
      }

      donador.setUltimaActualizacion(evento.getTimestamp());
      donadorRepository.save(donador);
    }

    String categoria = (String) don.get("categoriaProducto");
    String subcategoria = (String) don.get("subcategoriaProducto");
    if (categoria != null) {
      String key = categoria + "_" + (subcategoria != null ? subcategoria : "general");
      CalidadDonacionDocument cal = calidadRepository.findById(key)
          .orElseGet(() -> CalidadDonacionDocument.builder().id(key).categoriaProducto(categoria).subcategoriaProducto(subcategoria).totalDonaciones(0).donacionesConQueja(0).build());

      cal.setTotalDonaciones(cal.getTotalDonaciones() + 1);
      cal.recalcularProporcion();
      cal.setUltimaActualizacion(evento.getTimestamp());
      calidadRepository.save(cal);
    }
  }

  private void handleNecesidadRegistrada(EventoGenerico evento) {
    Map<String, Object> nec = evento.getNecesidad() != null ? evento.getNecesidad() : evento.getExtra();
    String necesidadId = evento.getIdReferencia() != null ? evento.getIdReferencia() : (String) nec.get("id");
    if (necesidadId == null) return;

    Integer cantidad = nec.get("cantidadObjetivo") instanceof Number n ? n.intValue() : 0;
    Integer urgencia = nec.get("urgencia") instanceof Number n ? n.intValue() : 5;

    NecesidadUrgenteDocument doc = NecesidadUrgenteDocument.builder()
        .id(necesidadId)
        .entidadId((String) nec.get("entidadID"))
        .entidadRazonSocial((String) nec.get("entidadRazonSocial"))
        .productoSolicitadoId((String) nec.get("productoSolicitadoID"))
        .productoNombre((String) nec.get("productoNombre"))
        .categoriaProducto((String) nec.get("categoriaProducto"))
        .subcategoriaProducto((String) nec.get("subcategoriaProducto"))
        .tipoNecesidad((String) nec.get("tipo"))
        .urgencia(urgencia)
        .cantidadObjetivo(cantidad)
        .cantidadFaltante(cantidad)
        .satisfecha(false)
        .ultimaActualizacion(evento.getTimestamp())
        .build();

    necesidadRepository.save(doc);
    log.info("Necesidad urgente guardada en MongoDB: {}", necesidadId);
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

  private void handleDeposito(EventoGenerico evento) {
    Map<String, Object> dep = evento.getDeposito() != null ? evento.getDeposito() : evento.getExtra();
    String depositoId = evento.getIdReferencia() != null ? evento.getIdReferencia() : (String) dep.get("id");
    if (depositoId == null) return;

    DepositoDocument doc = depositoRepository.findById(depositoId)
        .orElseGet(() -> DepositoDocument.builder().id(depositoId).build());

    if (dep.containsKey("nombre")) doc.setNombre((String) dep.get("nombre"));
    if (dep.containsKey("capacidadTotal") && dep.get("capacidadTotal") instanceof Number n) {
      doc.setCapacidadTotal(n.intValue());
    }
    if (dep.containsKey("stockActual") && dep.get("stockActual") instanceof Number n) {
      doc.setStockActual(n.intValue());
    }
    if (dep.containsKey("algoritmoMatchmaking")) {
      doc.setAlgoritmoMatchmaking((String) dep.get("algoritmoMatchmaking"));
    }
    if (doc.getCapacidadTotal() != null && doc.getCapacidadTotal() > 0 && doc.getStockActual() != null) {
      doc.setOcupacionPorcentaje((double) doc.getStockActual() / doc.getCapacidadTotal() * 100.0);
    }
    if (dep.containsKey("entidadAtendidaId")) {
      doc.getEntidadesDistintasAtendidas().add((String) dep.get("entidadAtendidaId"));
    }

    doc.setUltimaActualizacion(evento.getTimestamp());
    depositoRepository.save(doc);
  }

  private boolean esObsoleto(java.time.Instant ultimaActualizacion, EventoGenerico evento) {
    return ultimaActualizacion != null
        && evento.getTimestamp() != null
        && ultimaActualizacion.isAfter(evento.getTimestamp());
  }
}

package ar.edu.utn.dds.k3003.consultas.service;

import ar.edu.utn.dds.k3003.consultas.documents.CalidadDonacionDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DepositoDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DonadorDocument;
import ar.edu.utn.dds.k3003.consultas.documents.NecesidadUrgenteDocument;
import ar.edu.utn.dds.k3003.consultas.repositories.CalidadDonacionRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DepositoDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonadorDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.NecesidadDocumentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ConsultaService {

  private final DonadorDocumentRepository donadorRepository;
  private final NecesidadDocumentRepository necesidadRepository;
  private final DepositoDocumentRepository depositoRepository;
  private final CalidadDonacionRepository calidadRepository;
  private final MongoTemplate mongoTemplate;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private ar.edu.utn.dds.k3003.consultas.repositories.AsignacionDocumentRepository asignacionRepository;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private ar.edu.utn.dds.k3003.consultas.repositories.EventoProcesadoRepository eventosProcesados;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private ar.edu.utn.dds.k3003.consultas.repositories.DonacionDocumentRepository donacionRepository;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private ar.edu.utn.dds.k3003.consultas.repositories.EntidadDocumentRepository entidadRepository;

  public ConsultaService(
      DonadorDocumentRepository donadorRepository,
      NecesidadDocumentRepository necesidadRepository,
      DepositoDocumentRepository depositoRepository,
      CalidadDonacionRepository calidadRepository,
      MongoTemplate mongoTemplate) {
    this.donadorRepository = donadorRepository;
    this.necesidadRepository = necesidadRepository;
    this.depositoRepository = depositoRepository;
    this.calidadRepository = calidadRepository;
    this.mongoTemplate = mongoTemplate;
  }

  public Optional<DonadorDocument> obtenerFichaIntegralDonador(String donadorId) {
    log.info("Consulta 1: Buscando ficha integral de donador con ID: {}", donadorId);
    return donadorRepository.findById(donadorId);
  }

  public List<DonadorDocument> obtenerTodosLosDonadores() {
    return donadorRepository.findAll();
  }

  public List<NecesidadUrgenteDocument> obtenerNecesidadesUrgentes(
      Integer urgenciaMinima,
      String tipoNecesidad,
      String categoriaProducto,
      String subcategoriaProducto) {

    log.info(
        "Consulta 2: Buscando necesidades urgentes con filtros (urgMin: {}, tipo: {}, cat: {}, subcat: {})",
        urgenciaMinima,
        tipoNecesidad,
        categoriaProducto,
        subcategoriaProducto);

    Query query = new Query();
    List<Criteria> criteriaList = new ArrayList<>();

    criteriaList.add(Criteria.where("satisfecha").ne(true));

    if (urgenciaMinima != null) {
      criteriaList.add(Criteria.where("urgencia").gte(urgenciaMinima));
    }
    if (tipoNecesidad != null && !tipoNecesidad.isBlank()) {
      criteriaList.add(Criteria.where("tipoNecesidad").is(tipoNecesidad));
    }
    if (categoriaProducto != null && !categoriaProducto.isBlank()) {
      criteriaList.add(Criteria.where("categoriaProducto").is(categoriaProducto));
    }
    if (subcategoriaProducto != null && !subcategoriaProducto.isBlank()) {
      criteriaList.add(Criteria.where("subcategoriaProducto").is(subcategoriaProducto));
    }

    query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
    query.with(Sort.by(Sort.Direction.DESC, "urgencia"));

    return mongoTemplate.find(query, NecesidadUrgenteDocument.class);
  }

  public List<DepositoDocument> obtenerEstadoTodosDepositos() {
    log.info("Consulta 3: Obteniendo estado de todos los depósitos");
    return depositoRepository.findAll();
  }

  public Optional<DepositoDocument> obtenerEstadoDeposito(String depositoId) {
    log.info("Consulta 3: Obteniendo estado de depósito con ID: {}", depositoId);
    return depositoRepository.findById(depositoId);
  }

  public List<CalidadDonacionDocument> obtenerCalidadPorCategoria(String categoria) {
    log.info("Consulta 4: Obteniendo calidad de donaciones para categoria: {}", categoria);
    if (categoria != null && !categoria.isBlank()) {
      return calidadRepository.findByCategoriaProducto(categoria);
    }
    return calidadRepository.findAll();
  }

  public List<DonadorDocument> obtenerDonadoresConMasQuejasPipeline() {
    log.info("Consulta 4: Ejecutando pipeline de agregación en MongoDB para donadores con más quejas");

    Aggregation aggregation =
        Aggregation.newAggregation(
            Aggregation.match(Criteria.where("quejasRecibidas").gt(0)),
            Aggregation.sort(Sort.Direction.DESC, "quejasRecibidas"));

    AggregationResults<DonadorDocument> results =
        mongoTemplate.aggregate(aggregation, "donadores", DonadorDocument.class);

    return results.getMappedResults();
  }

  public void borrarTodo() {
    log.warn("Limpiando todas las colecciones del modelo de lectura en MongoDB");
    donadorRepository.deleteAll();
    necesidadRepository.deleteAll();
    depositoRepository.deleteAll();
    calidadRepository.deleteAll();
    if (asignacionRepository != null) asignacionRepository.deleteAll();
    if (eventosProcesados != null) eventosProcesados.deleteAll();
    if (donacionRepository != null) donacionRepository.deleteAll();
    if (entidadRepository != null) entidadRepository.deleteAll();
  }

  /**
   * Consulta 3 con rango de fechas: el tiempo promedio de entrega y las entidades atendidas se calculan
   * solo con las entregas del rango. Sin rango, es lo mismo que el estado guardado.
   */
  public List<DepositoDocument> obtenerEstadoDepositosEnRango(
      String depositoId, java.time.Instant desde, java.time.Instant hasta) {
    List<DepositoDocument> depositos = depositoId != null
        ? depositoRepository.findById(depositoId).map(List::of).orElse(List.of())
        : depositoRepository.findAll();
    if (donacionRepository == null || asignacionRepository == null) return depositos;
    for (DepositoDocument deposito : depositos) {
      List<ar.edu.utn.dds.k3003.consultas.documents.DonacionDocument> donaciones =
          donacionRepository.findByDepositoId(deposito.getId());
      List<ar.edu.utn.dds.k3003.consultas.documents.AsignacionDocument> asignaciones = donaciones.isEmpty()
          ? List.of()
          : asignacionRepository.findByDonacionIdIn(
              donaciones.stream().map(ar.edu.utn.dds.k3003.consultas.documents.DonacionDocument::getId).toList());
      EstadisticasDeposito estadisticas = EstadisticasDeposito.calcular(donaciones, asignaciones, desde, hasta);
      deposito.setAsignacionesPorEstado(estadisticas.getAsignacionesPorEstado());
      deposito.setTiempoPromedioEntregaMinutos(estadisticas.getTiempoPromedioEntregaMinutos());
      deposito.setEntidadesDistintasAtendidas(estadisticas.getEntidadesDistintas());
    }
    return depositos;
  }
}

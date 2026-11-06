package ar.edu.utn.dds.k3003.consultas.service;

import ar.edu.utn.dds.k3003.consultas.documents.CalidadDonacionDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DepositoDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DonadorDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DonadorQuejasResumen;
import ar.edu.utn.dds.k3003.consultas.documents.NecesidadUrgenteDocument;
import ar.edu.utn.dds.k3003.consultas.repositories.DepositoDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonadorDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.NecesidadDocumentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.ArithmeticOperators;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.aggregation.Fields;
import org.springframework.data.mongodb.core.aggregation.StringOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ConsultaService {

  /** Categoría y subcategoría con que se informa un producto que llegó sin ellas. */
  static final String SIN_CATEGORIA = "SIN_CATEGORIA";
  static final String SIN_SUBCATEGORIA = "general";
  static final String ESTADO_CON_QUEJA = "CONQUEJA";
  static final int LIMITE_RANKING_POR_DEFECTO = 10;

  private final DonadorDocumentRepository donadorRepository;
  private final NecesidadDocumentRepository necesidadRepository;
  private final DepositoDocumentRepository depositoRepository;
  private final MongoTemplate mongoTemplate;

  /** Cuántos donadores devuelve el ranking de quejas si no se pide otra cantidad. */
  @Value("${app.consultas.ranking-limite:" + LIMITE_RANKING_POR_DEFECTO + "}")
  private int limiteRanking = LIMITE_RANKING_POR_DEFECTO;

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
      MongoTemplate mongoTemplate) {
    this.donadorRepository = donadorRepository;
    this.necesidadRepository = necesidadRepository;
    this.depositoRepository = depositoRepository;
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

  /**
   * Consulta 4, primera parte: por categoría y subcategoría, la proporción de donaciones que terminaron con
   * queja (estado CONQUEJA). Se calcula en cada consulta sobre {@code donaciones}, que es lo que Donaciones
   * publica; los filtros son opcionales.
   */
  public List<CalidadDonacionDocument> obtenerCalidad(String categoria, String subcategoria) {
    log.info("Consulta 4: calidad de donaciones (categoria: {}, subcategoria: {})", categoria, subcategoria);
    return mongoTemplate
        .aggregate(pipelineCalidad(categoria, subcategoria), "donaciones", CalidadDonacionDocument.class)
        .getMappedResults();
  }

  /**
   * <ol>
   *   <li>{@code $unwind} de los productos, conservando las donaciones sin productos.
   *   <li>{@code $group} por donación + categoría + subcategoría: una donación cuenta una sola vez en cada
   *       categoría y subcategoría, aunque tenga varios productos de ella.
   *   <li>{@code $match} opcional por categoría y/o subcategoría.
   *   <li>{@code $group} por categoría + subcategoría: total de donaciones y cuántas están CONQUEJA.
   *   <li>{@code $project} con la proporción; sin categoría o subcategoría se informa
   *       {@value #SIN_CATEGORIA} / {@value #SIN_SUBCATEGORIA} (hoy Donaciones no las publica).
   *   <li>{@code $sort} por proporción descendente.
   * </ol>
   */
  Aggregation pipelineCalidad(String categoria, String subcategoria) {
    List<AggregationOperation> etapas = new ArrayList<>();
    etapas.add(Aggregation.unwind("productos", true));
    etapas.add(Aggregation.group(Fields.from(
            Fields.field("donacion", "_id"),
            Fields.field("categoria", "productos.categoriaId"),
            Fields.field("subcategoria", "productos.subcategoriaId")))
        .first("estado").as("estado")
        .first("ultimaActualizacion").as("ultimaActualizacion"));

    List<Criteria> filtros = new ArrayList<>();
    if (categoria != null && !categoria.isBlank()) {
      filtros.add(Criteria.where("_id.categoria").is(SIN_CATEGORIA.equals(categoria) ? null : categoria));
    }
    if (subcategoria != null && !subcategoria.isBlank()) {
      filtros.add(Criteria.where("_id.subcategoria").is(SIN_SUBCATEGORIA.equals(subcategoria) ? null : subcategoria));
    }
    if (!filtros.isEmpty()) {
      etapas.add(Aggregation.match(new Criteria().andOperator(filtros.toArray(new Criteria[0]))));
    }

    etapas.add(Aggregation.group(Fields.from(Fields.field("categoria"), Fields.field("subcategoria")))
        .count().as("totalDonaciones")
        .sum(ConditionalOperators.when(ComparisonOperators.valueOf("estado").equalToValue(ESTADO_CON_QUEJA))
            .then(1).otherwise(0)).as("donacionesConQueja")
        .max("ultimaActualizacion").as("ultimaActualizacion"));

    AggregationExpression categoriaONombre = ConditionalOperators.ifNull("categoria").then(SIN_CATEGORIA);
    AggregationExpression subcategoriaONombre = ConditionalOperators.ifNull("subcategoria").then(SIN_SUBCATEGORIA);
    etapas.add(Aggregation.project("totalDonaciones", "donacionesConQueja", "ultimaActualizacion")
        .and(StringOperators.Concat.valueOf(categoriaONombre).concat("_").concatValueOf(subcategoriaONombre)).as("_id")
        .and(categoriaONombre).as("categoriaProducto")
        .and(subcategoriaONombre).as("subcategoriaProducto")
        .and(ArithmeticOperators.Divide.valueOf("donacionesConQueja").divideBy("totalDonaciones")).as("proporcionQuejas"));

    etapas.add(Aggregation.sort(Sort.by(Sort.Direction.DESC, "proporcionQuejas")
        .and(Sort.by(Sort.Direction.DESC, "totalDonaciones"))
        .and(Sort.by(Sort.Direction.ASC, "categoriaProducto", "subcategoriaProducto"))));
    return Aggregation.newAggregation(etapas);
  }

  /**
   * Consulta 4, segunda parte: los donadores con más quejas, con su estado y su categoría. Proyecta solo
   * esos campos y devuelve como máximo {@code limite} (por defecto {@code app.consultas.ranking-limite}).
   */
  public List<DonadorQuejasResumen> obtenerDonadoresConMasQuejas(Integer limite) {
    int cantidad = limite != null ? limite : limiteRanking;
    log.info("Consulta 4: pipeline de donadores con más quejas (limite: {})", cantidad);
    return mongoTemplate
        .aggregate(pipelineRankingQuejas(cantidad), "donadores", DonadorQuejasResumen.class)
        .getMappedResults();
  }

  Aggregation pipelineRankingQuejas(int limite) {
    return Aggregation.newAggregation(
        Aggregation.match(Criteria.where("quejasRecibidas").gt(0)),
        Aggregation.sort(Sort.by(Sort.Direction.DESC, "quejasRecibidas").and(Sort.by(Sort.Direction.ASC, "_id"))),
        Aggregation.limit(limite),
        Aggregation.project("nombre", "estado", "categoria", "quejasRecibidas"));
  }

  public void borrarTodo() {
    log.warn("Limpiando todas las colecciones del modelo de lectura en MongoDB");
    donadorRepository.deleteAll();
    necesidadRepository.deleteAll();
    depositoRepository.deleteAll();
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

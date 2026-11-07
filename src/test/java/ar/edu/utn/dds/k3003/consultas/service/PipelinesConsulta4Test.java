package ar.edu.utn.dds.k3003.consultas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ar.edu.utn.dds.k3003.consultas.documents.DonadorQuejasResumen;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;

/**
 * Las etapas que se mandan a MongoDB en la consulta 4. Lo que devuelven contra una base real lo prueba
 * {@link Consulta4MongoTest} (necesita Docker).
 */
class PipelinesConsulta4Test {

  private final ConsultaService servicio = new ConsultaService(null, null, null, null);

  private static List<String> etapas(Aggregation aggregation) {
    return aggregation.toPipeline(Aggregation.DEFAULT_CONTEXT).stream().map(Document::toJson).toList();
  }

  @Test
  void laCalidadSinFiltrosAgrupaPorDonacionYDespuesPorCategoriaYSubcategoria() {
    List<String> etapas = etapas(servicio.pipelineCalidad(null, null));

    assertEquals(List.of(
        "{\"$unwind\": {\"path\": \"$productos\", \"preserveNullAndEmptyArrays\": true}}",
        "{\"$group\": {\"_id\": {\"donacion\": \"$_id\", \"categoria\": \"$productos.categoria\", "
            + "\"subcategoria\": \"$productos.subcategoria\"}, \"estado\": {\"$first\": \"$estado\"}, "
            + "\"ultimaActualizacion\": {\"$first\": \"$ultimaActualizacion\"}}}",
        "{\"$group\": {\"_id\": {\"categoria\": \"$_id.categoria\", \"subcategoria\": \"$_id.subcategoria\"}, "
            + "\"totalDonaciones\": {\"$sum\": 1}, \"donacionesConQueja\": {\"$sum\": {\"$cond\": {\"if\": "
            + "{\"$eq\": [\"$estado\", \"CONQUEJA\"]}, \"then\": 1, \"else\": 0}}}, "
            + "\"ultimaActualizacion\": {\"$max\": \"$ultimaActualizacion\"}}}",
        "{\"$project\": {\"totalDonaciones\": 1, \"donacionesConQueja\": 1, \"ultimaActualizacion\": 1, "
            + "\"_id\": {\"$concat\": [{\"$ifNull\": [\"$_id.categoria\", \"SIN_CATEGORIA\"]}, \"_\", "
            + "{\"$ifNull\": [\"$_id.subcategoria\", \"general\"]}]}, "
            + "\"categoriaProducto\": {\"$ifNull\": [\"$_id.categoria\", \"SIN_CATEGORIA\"]}, "
            + "\"subcategoriaProducto\": {\"$ifNull\": [\"$_id.subcategoria\", \"general\"]}, "
            + "\"proporcionQuejas\": {\"$divide\": [\"$donacionesConQueja\", \"$totalDonaciones\"]}}}",
        "{\"$sort\": {\"proporcionQuejas\": -1, \"totalDonaciones\": -1, \"categoriaProducto\": 1, "
            + "\"subcategoriaProducto\": 1}}"),
        etapas);
  }

  @Test
  void laCalidadFiltraPorCategoriaYSubcategoriaDespuesDelPrimerGroup() {
    List<String> etapas = etapas(servicio.pipelineCalidad("Alimentos", "Pastas"));

    assertEquals(6, etapas.size());
    assertEquals("{\"$match\": {\"$and\": [{\"_id.categoria\": \"Alimentos\"}, {\"_id.subcategoria\": \"Pastas\"}]}}",
        etapas.get(2));
  }

  @Test
  void filtrarPorSoloUnaDeLasDosAgregaUnaSolaCondicion() {
    assertEquals("{\"$match\": {\"$and\": [{\"_id.subcategoria\": \"Pastas\"}]}}",
        etapas(servicio.pipelineCalidad(null, "Pastas")).get(2));
    assertEquals("{\"$match\": {\"$and\": [{\"_id.categoria\": \"Alimentos\"}]}}",
        etapas(servicio.pipelineCalidad("Alimentos", " ")).get(2));
  }

  @Test
  void filtrarPorSinCategoriaBuscaLosProductosQueLlegaronSinElla() {
    assertEquals("{\"$match\": {\"$and\": [{\"_id.categoria\": null}, {\"_id.subcategoria\": null}]}}",
        etapas(servicio.pipelineCalidad("SIN_CATEGORIA", "general")).get(2));
  }

  @Test
  void elRankingDeQuejasLimitaYProyectaSoloLosCamposDeLaConsulta() {
    assertEquals(List.of(
        "{\"$match\": {\"quejasRecibidas\": {\"$gt\": 0}}}",
        "{\"$sort\": {\"quejasRecibidas\": -1, \"_id\": 1}}",
        "{\"$limit\": 3}",
        "{\"$project\": {\"nombre\": 1, \"estado\": 1, \"categoria\": 1, \"quejasRecibidas\": 1}}"),
        etapas(servicio.pipelineRankingQuejas(3)));
  }

  @Test
  void sinLimiteElRankingDevuelveDiez() {
    MongoTemplate mongo = mock(MongoTemplate.class, RETURNS_DEEP_STUBS);
    ArgumentCaptor<Aggregation> pipeline = ArgumentCaptor.forClass(Aggregation.class);

    new ConsultaService(null, null, null, mongo).obtenerDonadoresConMasQuejas(null);

    verify(mongo).aggregate(pipeline.capture(), eq("donadores"), eq(DonadorQuejasResumen.class));
    assertEquals("{\"$limit\": 10}", etapas(pipeline.getValue()).get(2));
  }
}

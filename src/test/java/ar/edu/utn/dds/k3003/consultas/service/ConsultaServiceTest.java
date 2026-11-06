package ar.edu.utn.dds.k3003.consultas.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ar.edu.utn.dds.k3003.consultas.documents.CalidadDonacionDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DepositoDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DonadorDocument;
import ar.edu.utn.dds.k3003.consultas.documents.NecesidadUrgenteDocument;
import ar.edu.utn.dds.k3003.consultas.repositories.CalidadDonacionRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DepositoDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonadorDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.NecesidadDocumentRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
public class ConsultaServiceTest {

  @Mock private DonadorDocumentRepository donadorRepository;
  @Mock private NecesidadDocumentRepository necesidadRepository;
  @Mock private DepositoDocumentRepository depositoRepository;
  @Mock private CalidadDonacionRepository calidadRepository;
  @Mock private MongoTemplate mongoTemplate;

  private ConsultaService consultaService;

  @BeforeEach
  void setUp() {
    consultaService =
        new ConsultaService(
            donadorRepository,
            necesidadRepository,
            depositoRepository,
            calidadRepository,
            mongoTemplate);
  }

  @Test
  void testConsulta1ObtenerFichaIntegralDonador() {
    DonadorDocument doc = DonadorDocument.builder().id("12").nombre("Lucia").build();
    when(donadorRepository.findById("12")).thenReturn(Optional.of(doc));

    Optional<DonadorDocument> result = consultaService.obtenerFichaIntegralDonador("12");
    assertTrue(result.isPresent());
    assertEquals("Lucia", result.get().getNombre());
  }

  @Test
  void testConsulta2ObtenerNecesidadesUrgentesConFiltros() {
    NecesidadUrgenteDocument nec =
        NecesidadUrgenteDocument.builder()
            .id("n1")
            .urgencia(9)
            .tipoNecesidad("EXTRAORDINARIA")
            .categoriaProducto("Alimentos")
            .build();

    when(mongoTemplate.find(any(Query.class), eq(NecesidadUrgenteDocument.class)))
        .thenReturn(List.of(nec));

    List<NecesidadUrgenteDocument> list =
        consultaService.obtenerNecesidadesUrgentes(5, "EXTRAORDINARIA", "Alimentos", null);

    assertEquals(1, list.size());
    assertEquals("n1", list.get(0).getId());
  }

  @Test
  void testConsulta3ObtenerEstadoDepositos() {
    DepositoDocument dep = DepositoDocument.builder().id("dep-1").nombre("Central").build();
    when(depositoRepository.findAll()).thenReturn(List.of(dep));

    List<DepositoDocument> list = consultaService.obtenerEstadoTodosDepositos();
    assertEquals(1, list.size());
  }

  @Test
  void testConsulta4CalidadPorCategoria() {
    CalidadDonacionDocument cal =
        CalidadDonacionDocument.builder().id("cat1_sub1").categoriaProducto("cat1").build();
    when(calidadRepository.findByCategoriaProducto("cat1")).thenReturn(List.of(cal));

    List<CalidadDonacionDocument> list = consultaService.obtenerCalidadPorCategoria("cat1");
    assertEquals(1, list.size());
  }

  @Test
  @SuppressWarnings("unchecked")
  void testConsulta4PipelineAgregacionDonadoresConMasQuejas() {
    DonadorDocument donador =
        DonadorDocument.builder().id("12").nombre("Lucia").quejasRecibidas(3).build();

    AggregationResults<DonadorDocument> results = mock(AggregationResults.class);
    when(results.getMappedResults()).thenReturn(List.of(donador));

    when(mongoTemplate.aggregate(any(Aggregation.class), eq("donadores"), eq(DonadorDocument.class)))
        .thenReturn(results);

    List<DonadorDocument> donadores = consultaService.obtenerDonadoresConMasQuejasPipeline();
    assertEquals(1, donadores.size());
    assertEquals(3, donadores.get(0).getQuejasRecibidas());
  }

  @Test
  void testBorrarTodo() {
    consultaService.borrarTodo();
    verify(donadorRepository, times(1)).deleteAll();
    verify(necesidadRepository, times(1)).deleteAll();
    verify(depositoRepository, times(1)).deleteAll();
    verify(calidadRepository, times(1)).deleteAll();
  }
}

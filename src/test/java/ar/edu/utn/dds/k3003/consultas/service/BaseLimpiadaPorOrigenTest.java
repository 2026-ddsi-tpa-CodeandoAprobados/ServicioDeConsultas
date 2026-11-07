package ar.edu.utn.dds.k3003.consultas.service;

import static ar.edu.utn.dds.k3003.consultas.support.EventosReales.asignacionCreada;
import static ar.edu.utn.dds.k3003.consultas.support.EventosReales.baseLimpiada;
import static ar.edu.utn.dds.k3003.consultas.support.EventosReales.depositoStock;
import static ar.edu.utn.dds.k3003.consultas.support.EventosReales.donacion;
import static ar.edu.utn.dds.k3003.consultas.support.EventosReales.donadorActualizado;
import static ar.edu.utn.dds.k3003.consultas.support.EventosReales.donadorCreado;
import static ar.edu.utn.dds.k3003.consultas.support.EventosReales.entidadCreada;
import static ar.edu.utn.dds.k3003.consultas.support.EventosReales.entregaCompletada;
import static ar.edu.utn.dds.k3003.consultas.support.EventosReales.necesidadRegistrada;
import static ar.edu.utn.dds.k3003.consultas.support.EventosReales.producto;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.utn.dds.k3003.consultas.documents.DepositoDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DonadorDocument;
import ar.edu.utn.dds.k3003.consultas.messaging.EventoGenerico;
import ar.edu.utn.dds.k3003.consultas.metrics.ConsultasMetrics;
import ar.edu.utn.dds.k3003.consultas.repositories.AsignacionDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DepositoDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonacionDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonadorDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.EntidadDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.EventoProcesadoRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.NecesidadDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.ProductoDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.support.MongoEnMemoria;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** BASE_LIMPIADA borra solo lo que aportó el módulo que lo publica (según su idReferencia). */
class BaseLimpiadaPorOrigenTest {

  private static final String DESPUES = "2026-11-06T20:00:00Z";

  private DonadorDocumentRepository donadores;
  private NecesidadDocumentRepository necesidades;
  private DepositoDocumentRepository depositos;
  private AsignacionDocumentRepository asignaciones;
  private DonacionDocumentRepository donaciones;
  private EntidadDocumentRepository entidades;
  private ProductoDocumentRepository productos;
  private SimpleMeterRegistry registry;
  private EventProcessingService servicio;

  @BeforeEach
  void preparar() {
    donadores = MongoEnMemoria.repositorio(DonadorDocumentRepository.class);
    necesidades = MongoEnMemoria.repositorio(NecesidadDocumentRepository.class);
    depositos = MongoEnMemoria.repositorio(DepositoDocumentRepository.class);
    asignaciones = MongoEnMemoria.repositorio(AsignacionDocumentRepository.class);
    donaciones = MongoEnMemoria.repositorio(DonacionDocumentRepository.class);
    entidades = MongoEnMemoria.repositorio(EntidadDocumentRepository.class);
    productos = MongoEnMemoria.repositorio(ProductoDocumentRepository.class);
    registry = new SimpleMeterRegistry();
    servicio = new EventProcessingService(donadores, necesidades, depositos);
    ReflectionTestUtils.setField(servicio, "asignacionRepository", asignaciones);
    ReflectionTestUtils.setField(servicio, "eventosProcesados", MongoEnMemoria.repositorio(EventoProcesadoRepository.class));
    ReflectionTestUtils.setField(servicio, "donacionRepository", donaciones);
    ReflectionTestUtils.setField(servicio, "entidadRepository", entidades);
    servicio.setMetricas(new ConsultasMetrics(registry));

    // Un poco de cada módulo. Sin productoRepository al principio: el catálogo no se cruza con las donaciones
    // de este escenario (el finder anidado findByProductosProductoId no existe en el repositorio en memoria).
    procesar(
        entidadCreada("2026-11-06T09:00:00Z", "4", "Comedor Los Girasoles"),
        donadorCreado("2026-11-06T09:00:01Z", "12", "VERIFICADO", "OCASIONAL"),
        donadorActualizado("2026-11-06T09:00:02Z", "12", "COLABORADOR"),
        depositoStock("2026-11-06T10:00:00Z", "3", "5", 10, "7"),
        donacion("DONACION_CREADA", "2026-11-06T10:00:01Z", "7", "12", "3", "ACEPTADA", "5", 10),
        asignacionCreada("2026-11-06T11:00:00Z", "a1", "7", "5", 10, "n1"),
        necesidadRegistrada("2026-11-06T11:00:01Z", "n1", "4", 9, 200, "5"),
        entregaCompletada("2026-11-06T13:00:00Z", "a1", "7", "5", 10, "n1", "2026-11-06T11:00:00Z"));
    ReflectionTestUtils.setField(servicio, "productoRepository", productos);
    procesar(producto("PRODUCTO_CREADO", "2026-11-06T09:00:00Z", "99", "Arroz", "1", "Alimentos", "2", "Arroz"));

    // El escenario armado: la ficha y el depósito ya tienen datos.
    assertEquals(1, donador().getDonacionesPorEstado().get("ACEPTADA"));
    assertEquals(1, deposito().getAsignacionesPorEstado().get("COMPLETADA"));
  }

  private void procesar(EventoGenerico... eventos) {
    for (EventoGenerico e : eventos) servicio.procesar(e);
  }

  private DonadorDocument donador() {
    return donadores.findById("12").orElseThrow();
  }

  private DepositoDocument deposito() {
    return depositos.findById("3").orElseThrow();
  }

  private double ignorados(String motivo) {
    Counter c = registry.find(ConsultasMetrics.IGNORADOS).tags("tipo", "BASE_LIMPIADA", "motivo", motivo).counter();
    return c == null ? 0 : c.count();
  }

  @Test
  void laDeDonacionesBorraLasDonacionesYRecalculaDonadoresYDepositos() {
    procesar(baseLimpiada(DESPUES, "donaciones"));

    assertEquals(0, donaciones.count());
    DonadorDocument d = donador();
    assertEquals(Map.of("INGRESADA", 0, "ACEPTADA", 0, "CONQUEJA", 0), d.getDonacionesPorEstado());
    assertTrue(d.getEntidadesBeneficiadas().isEmpty());
    DepositoDocument dep = deposito();
    assertEquals(Map.of("ASIGNADA", 0, "COMPLETADA", 0), dep.getAsignacionesPorEstado());
    assertNull(dep.getTiempoPromedioEntregaMinutos());
    assertTrue(dep.getEntidadesDistintasAtendidas().isEmpty());
  }

  @Test
  void laDeDonacionesNoTocaLoQueAportaronLosOtrosModulos() {
    procesar(baseLimpiada(DESPUES, "donaciones"));

    DonadorDocument d = donador();
    assertEquals("Lucía", d.getNombre());
    assertEquals("COLABORADOR", d.getCategoria());
    assertEquals(List.of("Completitud"), d.getInsignias());
    assertEquals(1, entidades.count());
    assertEquals(1, necesidades.count());
    assertEquals(1, productos.count());
    assertEquals(1, asignaciones.count(), "las asignaciones son de Logística");
    DepositoDocument dep = deposito();
    assertEquals(1000, dep.getCapacidadTotal());
    assertEquals(Map.of("5", 10), dep.getStockPorProducto());
  }

  @Test
  void laDeLogisticaBorraDepositosYAsignacionesPeroNoLasDonaciones() {
    procesar(baseLimpiada(DESPUES, "logistica"));

    assertEquals(0, depositos.count());
    assertEquals(0, asignaciones.count());
    assertEquals(1, donaciones.count());
    assertEquals(1, productos.count());
    assertEquals(1, donador().getDonacionesPorEstado().get("ACEPTADA"));
    assertTrue(donador().getEntidadesBeneficiadas().isEmpty());
    assertTrue(necesidades.findById("n1").orElseThrow().getStockPorDeposito().isEmpty());
  }

  @Test
  void laDeIncentivosBorraInsigniasYMisionesPeroNoLasDonaciones() {
    procesar(baseLimpiada(DESPUES, "incentivos"));

    assertTrue(donador().getInsignias().isEmpty());
    assertNull(donador().getMisionEnCurso());
    assertEquals(1, donaciones.count());
    assertEquals(1, depositos.count());
    assertEquals(1, donador().getDonacionesPorEstado().get("ACEPTADA"));
  }

  @Test
  void unOrigenDesconocidoOSinIdReferenciaNoBorraNada() {
    procesar(baseLimpiada(DESPUES, "otro-modulo"), baseLimpiada("2026-11-06T20:00:01Z", null));

    assertEquals(1, donaciones.count());
    assertEquals(1, depositos.count());
    assertEquals(1, asignaciones.count());
    assertFalse(donador().getInsignias().isEmpty());
    assertEquals(1, entidades.count());
    assertEquals(1, necesidades.count());
    assertEquals(2, ignorados("no_mapeado"));
  }
}

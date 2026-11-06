package ar.edu.utn.dds.k3003.consultas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.dds.k3003.consultas.documents.AsignacionDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DepositoDocument;
import ar.edu.utn.dds.k3003.consultas.documents.EventoProcesado;
import ar.edu.utn.dds.k3003.consultas.documents.NecesidadUrgenteDocument;
import ar.edu.utn.dds.k3003.consultas.messaging.EventoGenerico;
import ar.edu.utn.dds.k3003.consultas.repositories.AsignacionDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DepositoDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonadorDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.EventoProcesadoRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.NecesidadDocumentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Eventos que publica Logística, con el formato de docs/eventos-logistica.md de su repositorio. */
@ExtendWith(MockitoExtension.class)
class EventosDeLogisticaTest {

  @Mock private DonadorDocumentRepository donadorRepository;
  @Mock private NecesidadDocumentRepository necesidadRepository;
  @Mock private DepositoDocumentRepository depositoRepository;
  @Mock private AsignacionDocumentRepository asignacionRepository;
  @Mock private EventoProcesadoRepository eventosProcesados;

  private EventProcessingService servicio;

  @BeforeEach
  void preparar() {
    servicio = new EventProcessingService(donadorRepository, necesidadRepository, depositoRepository);
    ReflectionTestUtils.setField(servicio, "asignacionRepository", asignacionRepository);
    ReflectionTestUtils.setField(servicio, "eventosProcesados", eventosProcesados);
  }

  private EventoGenerico evento(String id, String tipo, String idReferencia, Instant timestamp) {
    return EventoGenerico.builder().id(id).tipo(tipo).idReferencia(idReferencia).timestamp(timestamp).build();
  }

  @Test
  void elDepositoDeLogisticaSeGuardaConSuCapacidadSuOcupacionYSuAlgoritmo() {
    EventoGenerico e = evento("e1", "DEPOSITO_STOCK_ACTUALIZADO", "3", Instant.parse("2026-11-06T14:00:00Z"));
    e.setDeposito(Map.of("id", "3", "nombre", "Central", "direccion", "Medrano 951", "capacidadMaxima", 100,
        "unidadesOcupadas", 10, "algoritmo", "SUB_ATENDIDOS",
        "stock", List.of(Map.of("paqueteId", "12", "donacionId", "don-1", "productoId", "arroz", "cantidad", 10))));
    NecesidadUrgenteDocument necesidad = NecesidadUrgenteDocument.builder().id("n1").productoSolicitadoId("arroz").build();
    when(depositoRepository.findById("3")).thenReturn(Optional.empty());
    when(necesidadRepository.findAll()).thenReturn(List.of(necesidad));

    servicio.procesar(e);

    ArgumentCaptor<DepositoDocument> captor = ArgumentCaptor.forClass(DepositoDocument.class);
    verify(depositoRepository).save(captor.capture());
    DepositoDocument d = captor.getValue();
    assertEquals("Central", d.getNombre());
    assertEquals(100, d.getCapacidadTotal());
    assertEquals(10, d.getStockActual());
    assertEquals(10.0, d.getOcupacionPorcentaje());
    assertEquals("SUB_ATENDIDOS", d.getAlgoritmoMatchmaking());
    assertEquals(10, necesidad.getStockPorDeposito().get("3"));
    verify(necesidadRepository).save(necesidad);
  }

  @Test
  void eliminarUnDepositoLoBorraYLoSacaDelStockDeLasNecesidades() {
    NecesidadUrgenteDocument necesidad = NecesidadUrgenteDocument.builder().id("n1").build();
    necesidad.getStockPorDeposito().put("3", 10);
    when(necesidadRepository.findAll()).thenReturn(List.of(necesidad));

    servicio.procesar(evento("e2", "DEPOSITO_ELIMINADO", "3", Instant.now()));

    verify(depositoRepository).deleteById("3");
    verify(depositoRepository, never()).save(any());
    assertTrue(necesidad.getStockPorDeposito().isEmpty());
  }

  @Test
  void unaAsignacionSeGuardaConLaEntidadDeSuNecesidadYSumaPendientes() {
    EventoGenerico e = evento("e3", "ASIGNACION_CREADA", "a1", Instant.parse("2026-11-06T14:03:21Z"));
    e.setAsignacion(Map.of("id", "a1", "paqueteId", "13", "donacionId", "don-1", "productoId", "arroz", "cantidad", 10,
        "necesidadId", "n1", "origen", "MATCHMAKING", "estado", "ASIGNADA", "fechaAsignacion", "2026-11-06T14:03:21Z"));
    NecesidadUrgenteDocument necesidad = NecesidadUrgenteDocument.builder().id("n1").entidadId("4").build();
    when(asignacionRepository.findById("a1")).thenReturn(Optional.empty());
    when(necesidadRepository.findById("n1")).thenReturn(Optional.of(necesidad));
    when(asignacionRepository.findByNecesidadId("n1")).thenReturn(List.of(
        AsignacionDocument.builder().id("a1").necesidadId("n1").estado("ASIGNADA").cantidad(10).build()));

    servicio.procesar(e);

    ArgumentCaptor<AsignacionDocument> captor = ArgumentCaptor.forClass(AsignacionDocument.class);
    verify(asignacionRepository).save(captor.capture());
    AsignacionDocument a = captor.getValue();
    assertEquals("4", a.getEntidadId());
    assertEquals("MATCHMAKING", a.getOrigen());
    assertEquals(Instant.parse("2026-11-06T14:03:21Z"), a.getFechaAsignacion());
    assertNull(a.getFechaEntrega());
    assertEquals(10, necesidad.getUnidadesAsignadasPendientes());
  }

  @Test
  void laEntregaCompletaLaAsignacionYYaNoQuedanPendientes() {
    EventoGenerico e = evento("e4", "ENTREGA_COMPLETADA", "a1", Instant.parse("2026-11-06T16:40:02Z"));
    e.setAsignacion(Map.of("id", "a1", "necesidadId", "n1", "cantidad", 10, "estado", "COMPLETADA",
        "fechaAsignacion", "2026-11-06T14:03:21Z", "fechaEntrega", "2026-11-06T16:40:02Z"));
    AsignacionDocument guardada = AsignacionDocument.builder().id("a1").estado("ASIGNADA")
        .ultimaActualizacion(Instant.parse("2026-11-06T14:03:21Z")).build();
    NecesidadUrgenteDocument necesidad = NecesidadUrgenteDocument.builder().id("n1").unidadesAsignadasPendientes(10).build();
    when(asignacionRepository.findById("a1")).thenReturn(Optional.of(guardada));
    when(necesidadRepository.findById("n1")).thenReturn(Optional.of(necesidad));
    when(asignacionRepository.findByNecesidadId("n1")).thenReturn(List.of(
        AsignacionDocument.builder().id("a1").necesidadId("n1").estado("COMPLETADA").cantidad(10).build()));

    servicio.procesar(e);

    assertEquals("COMPLETADA", guardada.getEstado());
    assertEquals(Instant.parse("2026-11-06T16:40:02Z"), guardada.getFechaEntrega());
    assertEquals(0, necesidad.getUnidadesAsignadasPendientes());
  }

  @Test
  void unEventoRepetidoNoSeAplicaDosVeces() {
    when(eventosProcesados.existsById("e5")).thenReturn(true);
    EventoGenerico e = evento("e5", "DEPOSITO_CREADO", "3", Instant.now());
    e.setDeposito(Map.of("id", "3", "nombre", "Central"));

    servicio.procesar(e);

    verify(depositoRepository, never()).save(any());
  }

  @Test
  void unEventoAplicadoQuedaMarcadoComoProcesado() {
    EventoGenerico e = evento("e6", "DEPOSITO_CREADO", "3", Instant.now());
    e.setDeposito(Map.of("id", "3", "nombre", "Central"));
    when(depositoRepository.findById("3")).thenReturn(Optional.empty());

    servicio.procesar(e);

    ArgumentCaptor<EventoProcesado> captor = ArgumentCaptor.forClass(EventoProcesado.class);
    verify(eventosProcesados, times(1)).save(captor.capture());
    assertEquals("e6", captor.getValue().getId());
  }

  @Test
  void unEventoMasViejoQueLoGuardadoSeIgnora() {
    Instant ahora = Instant.parse("2026-11-06T15:00:00Z");
    when(depositoRepository.findById("3")).thenReturn(
        Optional.of(DepositoDocument.builder().id("3").nombre("Nuevo").ultimaActualizacion(ahora).build()));
    EventoGenerico viejo = evento("e7", "DEPOSITO_MODIFICADO", "3", ahora.minusSeconds(60));
    viejo.setDeposito(Map.of("id", "3", "nombre", "Viejo"));

    servicio.procesar(viejo);

    verify(depositoRepository, never()).save(any());
  }

  @Test
  void alReiniciarLaBaseDeLogisticaSeBorranDepositosYAsignaciones() {
    NecesidadUrgenteDocument necesidad = NecesidadUrgenteDocument.builder().id("n1").unidadesAsignadasPendientes(5).build();
    necesidad.getStockPorDeposito().put("3", 10);
    when(necesidadRepository.findAll()).thenReturn(List.of(necesidad));

    servicio.procesar(evento("e8", "BASE_LIMPIADA", "logistica", Instant.now()));

    verify(depositoRepository).deleteAll();
    verify(asignacionRepository).deleteAll();
    assertTrue(necesidad.getStockPorDeposito().isEmpty());
    assertEquals(0, necesidad.getUnidadesAsignadasPendientes());
  }
}

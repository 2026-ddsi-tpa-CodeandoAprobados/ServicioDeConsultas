package ar.edu.utn.dds.k3003.consultas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.dds.k3003.consultas.documents.AsignacionDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DepositoDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DonacionDocument;
import ar.edu.utn.dds.k3003.consultas.documents.EntidadDocument;
import ar.edu.utn.dds.k3003.consultas.documents.NecesidadUrgenteDocument;
import ar.edu.utn.dds.k3003.consultas.messaging.EventoGenerico;
import ar.edu.utn.dds.k3003.consultas.repositories.AsignacionDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DepositoDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonacionDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonadorDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.EntidadDocumentRepository;
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

/**
 * Eventos de Donadores y Entidades con el formato real de su publicador (NecesidadMaterialDTO con
 * nivelDeUrgencia, EntidadBeneficaDTO, eventos de borrado total).
 */
@ExtendWith(MockitoExtension.class)
class EventosDeDonadoresTest {

  @Mock private DonadorDocumentRepository donadorRepository;
  @Mock private NecesidadDocumentRepository necesidadRepository;
  @Mock private DepositoDocumentRepository depositoRepository;
  @Mock private AsignacionDocumentRepository asignacionRepository;
  @Mock private EventoProcesadoRepository eventosProcesados;
  @Mock private DonacionDocumentRepository donacionRepository;
  @Mock private EntidadDocumentRepository entidadRepository;

  private EventProcessingService servicio;

  @BeforeEach
  void preparar() {
    servicio = new EventProcessingService(donadorRepository, necesidadRepository, depositoRepository);
    ReflectionTestUtils.setField(servicio, "asignacionRepository", asignacionRepository);
    ReflectionTestUtils.setField(servicio, "eventosProcesados", eventosProcesados);
    ReflectionTestUtils.setField(servicio, "donacionRepository", donacionRepository);
    ReflectionTestUtils.setField(servicio, "entidadRepository", entidadRepository);
  }

  private EventoGenerico evento(String id, String tipo, String idReferencia, Instant timestamp) {
    return EventoGenerico.builder().id(id).tipo(tipo).idReferencia(idReferencia).timestamp(timestamp).build();
  }

  private EventoGenerico necesidadRegistrada() {
    EventoGenerico e = evento("n-1", "NECESIDAD_REGISTRADA", "nec-1", Instant.parse("2026-11-06T14:00:00Z"));
    e.setNecesidad(Map.of("id", "nec-1", "entidadID", "4", "nivelDeUrgencia", 8, "descripcion", "Fideos",
        "cantidadObjetivo", 30, "productoSolicitadoID", "7", "tipo", "EXTRAORDINARIA"));
    return e;
  }

  @Test
  void laNecesidadGuardaElNivelDeUrgenciaQueEnviaDonadores() {
    when(entidadRepository.findById("4"))
        .thenReturn(Optional.of(EntidadDocument.builder().id("4").razonSocial("Comedor Los Girasoles").build()));

    servicio.procesar(necesidadRegistrada());

    ArgumentCaptor<NecesidadUrgenteDocument> captor = ArgumentCaptor.forClass(NecesidadUrgenteDocument.class);
    verify(necesidadRepository).save(captor.capture());
    NecesidadUrgenteDocument n = captor.getValue();
    assertEquals(8, n.getUrgencia());
    assertEquals("EXTRAORDINARIA", n.getTipoNecesidad());
    assertEquals(30, n.getCantidadFaltante());
    assertEquals("4", n.getEntidadId());
    assertEquals("Comedor Los Girasoles", n.getEntidadRazonSocial());
    assertEquals("7", n.getProductoSolicitadoId());
  }

  @Test
  void laNecesidadNuevaTomaElStockQueYaHabiaEnLosDepositos() {
    when(depositoRepository.findAll()).thenReturn(List.of(
        DepositoDocument.builder().id("3").stockPorProducto(Map.of("7", 15, "9", 4)).build(),
        DepositoDocument.builder().id("5").stockPorProducto(Map.of("9", 2)).build()));

    servicio.procesar(necesidadRegistrada());

    ArgumentCaptor<NecesidadUrgenteDocument> captor = ArgumentCaptor.forClass(NecesidadUrgenteDocument.class);
    verify(necesidadRepository).save(captor.capture());
    assertEquals(Map.of("3", 15), captor.getValue().getStockPorDeposito());
  }

  @Test
  void unaAsignacionQueLlegoAntesRecibeLaEntidadCuandoLlegaLaNecesidad() {
    AsignacionDocument previa = AsignacionDocument.builder().id("a1").necesidadId("nec-1").donacionId("d1")
        .estado("ASIGNADA").cantidad(10).build();
    when(asignacionRepository.findByNecesidadId("nec-1")).thenReturn(List.of(previa));
    when(donacionRepository.findById("d1")).thenReturn(Optional.of(DonacionDocument.builder().id("d1").donadorId("12").build()));
    when(entidadRepository.findById("4"))
        .thenReturn(Optional.of(EntidadDocument.builder().id("4").razonSocial("Comedor").build()));

    servicio.procesar(necesidadRegistrada());

    ArgumentCaptor<AsignacionDocument> captor = ArgumentCaptor.forClass(AsignacionDocument.class);
    verify(asignacionRepository).save(captor.capture());
    assertEquals("4", captor.getValue().getEntidadId());
    assertEquals("Comedor", captor.getValue().getEntidadRazonSocial());
    verify(donadorRepository).save(any());
  }

  @Test
  void laEntidadCreadaGuardaSuRazonSocialYSeLaPasaASusNecesidadesYAsignaciones() {
    EventoGenerico e = evento("e-1", "ENTIDAD_CREADA", "4", Instant.parse("2026-11-06T14:00:00Z"));
    e.setEntidad(Map.of("id", "4", "razonSocial", "Comedor Los Girasoles", "domicilio", "Calle 1"));
    NecesidadUrgenteDocument necesidad = NecesidadUrgenteDocument.builder().id("nec-1").entidadId("4").build();
    AsignacionDocument asignacion = AsignacionDocument.builder().id("a1").entidadId("4").donacionId("d1").build();
    when(entidadRepository.findById("4")).thenReturn(Optional.empty());
    when(necesidadRepository.findByEntidadId("4")).thenReturn(List.of(necesidad));
    when(asignacionRepository.findByEntidadId("4")).thenReturn(List.of(asignacion));
    when(donacionRepository.findById("d1")).thenReturn(Optional.of(DonacionDocument.builder().id("d1").donadorId("12").build()));

    servicio.procesar(e);

    ArgumentCaptor<EntidadDocument> guardada = ArgumentCaptor.forClass(EntidadDocument.class);
    verify(entidadRepository).save(guardada.capture());
    assertEquals("Comedor Los Girasoles", guardada.getValue().getRazonSocial());
    assertEquals("Comedor Los Girasoles", necesidad.getEntidadRazonSocial());
    assertEquals("Comedor Los Girasoles", asignacion.getEntidadRazonSocial());
    verify(necesidadRepository).save(necesidad);
    verify(asignacionRepository).save(asignacion);
  }

  @Test
  void elCambioDeRazonSocialViajaEnExtra() {
    EventoGenerico e = evento("e-2", "ENTIDAD_RAZON_MODIFICADA", "4", Instant.parse("2026-11-06T15:00:00Z"));
    e.getExtra().put("razonSocial", "Comedor Nuevo");
    when(entidadRepository.findById("4")).thenReturn(Optional.of(EntidadDocument.builder().id("4")
        .razonSocial("Comedor Viejo").ultimaActualizacion(Instant.parse("2026-11-06T14:00:00Z")).build()));

    servicio.procesar(e);

    ArgumentCaptor<EntidadDocument> captor = ArgumentCaptor.forClass(EntidadDocument.class);
    verify(entidadRepository).save(captor.capture());
    assertEquals("Comedor Nuevo", captor.getValue().getRazonSocial());
  }

  @Test
  void unCambioDeRazonSocialMasViejoQueLoGuardadoSeIgnora() {
    EventoGenerico e = evento("e-3", "ENTIDAD_RAZON_MODIFICADA", "4", Instant.parse("2026-11-06T13:00:00Z"));
    e.getExtra().put("razonSocial", "Comedor Viejo");
    when(entidadRepository.findById("4")).thenReturn(Optional.of(EntidadDocument.builder().id("4")
        .razonSocial("Comedor Nuevo").ultimaActualizacion(Instant.parse("2026-11-06T14:00:00Z")).build()));

    servicio.procesar(e);

    verify(entidadRepository, never()).save(any());
  }

  @Test
  void alBorrarTodasLasEntidadesSeBorranTambienSusNecesidades() {
    servicio.procesar(evento("e-4", "ENTIDADES_TODAS_ELIMINADAS", null, Instant.now()));

    verify(entidadRepository).deleteAll();
    verify(necesidadRepository).deleteAll();
  }

  @Test
  void alBorrarTodosLosDonadoresSeVaciaLaColeccion() {
    servicio.procesar(evento("e-5", "DONADORES_TODOS_ELIMINADOS", null, Instant.now()));

    verify(donadorRepository).deleteAll();
  }

  @Test
  void elDepositoGuardaCuantasUnidadesDeCadaProductoTiene() {
    EventoGenerico e = evento("e-6", "DEPOSITO_STOCK_ACTUALIZADO", "3", Instant.parse("2026-11-06T14:00:00Z"));
    e.setDeposito(Map.of("id", "3", "nombre", "Central", "capacidadMaxima", 100, "unidadesOcupadas", 15,
        "algoritmo", "SUB_ATENDIDOS",
        "stock", List.of(
            Map.of("paqueteId", "1", "donacionId", "d1", "productoId", "7", "cantidad", 10),
            Map.of("paqueteId", "2", "donacionId", "d2", "productoId", "7", "cantidad", 5))));
    when(depositoRepository.findById("3")).thenReturn(Optional.empty());

    servicio.procesar(e);

    ArgumentCaptor<DepositoDocument> captor = ArgumentCaptor.forClass(DepositoDocument.class);
    verify(depositoRepository).save(captor.capture());
    assertEquals(Map.of("7", 15), captor.getValue().getStockPorProducto());
    assertNull(captor.getValue().getTiempoPromedioEntregaMinutos());
  }
}

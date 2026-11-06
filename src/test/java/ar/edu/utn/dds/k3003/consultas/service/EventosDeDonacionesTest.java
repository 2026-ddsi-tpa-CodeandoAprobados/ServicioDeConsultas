package ar.edu.utn.dds.k3003.consultas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.dds.k3003.consultas.documents.AsignacionDocument;
import ar.edu.utn.dds.k3003.consultas.documents.CalidadDonacionDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DepositoDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DonacionDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DonadorDocument;
import ar.edu.utn.dds.k3003.consultas.documents.NecesidadUrgenteDocument;
import ar.edu.utn.dds.k3003.consultas.documents.ProductoDonado;
import ar.edu.utn.dds.k3003.consultas.messaging.EventoGenerico;
import ar.edu.utn.dds.k3003.consultas.repositories.AsignacionDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.CalidadDonacionRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DepositoDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonacionDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonadorDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.EventoProcesadoRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.NecesidadDocumentRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** Eventos de Donaciones (DONACION_*) y lo que se calcula a partir de ellos. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventosDeDonacionesTest {

  private static final Instant T0 = Instant.parse("2026-11-06T10:00:00Z");

  @Mock private DonadorDocumentRepository donadorRepository;
  @Mock private NecesidadDocumentRepository necesidadRepository;
  @Mock private DepositoDocumentRepository depositoRepository;
  @Mock private CalidadDonacionRepository calidadRepository;
  @Mock private AsignacionDocumentRepository asignacionRepository;
  @Mock private EventoProcesadoRepository eventosProcesados;
  @Mock private DonacionDocumentRepository donacionRepository;

  private EventProcessingService servicio;

  @BeforeEach
  void preparar() {
    servicio = new EventProcessingService(donadorRepository, necesidadRepository, depositoRepository, calidadRepository);
    ReflectionTestUtils.setField(servicio, "asignacionRepository", asignacionRepository);
    ReflectionTestUtils.setField(servicio, "eventosProcesados", eventosProcesados);
    ReflectionTestUtils.setField(servicio, "donacionRepository", donacionRepository);
  }

  private Map<String, Object> donacion(String estado) {
    return Map.of("id", "don-1", "donadorID", "d1", "depositoID", "3", "descripcion", "arroz",
        "estado", estado, "fechaRegistro", "2026-11-06",
        "detallesProductosDTO", List.of(Map.of("id", "x1", "productoID", "arroz", "cantidadProducto", 10,
            "categoriaID", "alimentos", "subcategoriaID", "arroz")));
  }

  private EventoGenerico evento(String id, String tipo, Instant timestamp, Map<String, Object> donacion) {
    EventoGenerico e = EventoGenerico.builder().id(id).tipo(tipo).idReferencia("don-1").timestamp(timestamp).build();
    e.setDonacion(donacion);
    return e;
  }

  private DonacionDocument documento(String estado) {
    return DonacionDocument.builder().id("don-1").donadorId("d1").depositoId("3").estado(estado).fechaIngreso(T0)
        .productos(new ArrayList<>(List.of(ProductoDonado.builder().productoId("arroz").cantidad(10)
            .categoriaId("alimentos").subcategoriaId("arroz").build())))
        .ultimaActualizacion(T0).build();
  }

  @Test
  void unaDonacionCreadaSeGuardaConSuDonadorSuDepositoYSusProductos() {
    DonacionDocument guardada = documento("INGRESADA");
    when(donacionRepository.findById("don-1")).thenReturn(Optional.empty());
    when(donacionRepository.findByDonadorId("d1")).thenReturn(List.of(guardada));
    when(donacionRepository.findAll()).thenReturn(List.of(guardada));

    servicio.procesar(evento("e1", "DONACION_CREADA", T0, donacion("INGRESADA")));

    ArgumentCaptor<DonacionDocument> captor = ArgumentCaptor.forClass(DonacionDocument.class);
    verify(donacionRepository).save(captor.capture());
    DonacionDocument d = captor.getValue();
    assertEquals("d1", d.getDonadorId());
    assertEquals("3", d.getDepositoId());
    assertEquals(T0, d.getFechaIngreso());
    assertEquals("alimentos", d.getProductos().get(0).getCategoriaId());
    assertEquals(10, d.getProductos().get(0).getCantidad());

    ArgumentCaptor<DonadorDocument> donador = ArgumentCaptor.forClass(DonadorDocument.class);
    verify(donadorRepository).save(donador.capture());
    assertEquals(1, donador.getValue().getDonacionesPorEstado().get("INGRESADA"));
    assertEquals(0, donador.getValue().getDonacionesPorEstado().get("ACEPTADA"));
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void alPasarAQuejaLaDonacionSeMueveDeEstadoYNoSeCuentaDosVeces() {
    DonacionDocument existente = documento("INGRESADA");
    when(donacionRepository.findById("don-1")).thenReturn(Optional.of(existente));
    when(donacionRepository.findByDonadorId("d1")).thenReturn(List.of(existente));
    when(donacionRepository.findAll()).thenReturn(List.of(existente));

    servicio.procesar(evento("e2", "DONACION_ESTADO_MODIFICADO", T0.plusSeconds(600), donacion("CONQUEJA")));

    ArgumentCaptor<DonadorDocument> donador = ArgumentCaptor.forClass(DonadorDocument.class);
    verify(donadorRepository).save(donador.capture());
    assertEquals(0, donador.getValue().getDonacionesPorEstado().get("INGRESADA"));
    assertEquals(1, donador.getValue().getDonacionesPorEstado().get("CONQUEJA"));
    assertEquals(T0, existente.getFechaIngreso(), "el ingreso es el de la primera vez que se informó");

    ArgumentCaptor<Iterable> calidad = ArgumentCaptor.forClass(Iterable.class);
    verify(calidadRepository).saveAll(calidad.capture());
    CalidadDonacionDocument cal = ((Iterable<CalidadDonacionDocument>) calidad.getValue()).iterator().next();
    assertEquals("alimentos_arroz", cal.getId());
    assertEquals(1, cal.getTotalDonaciones());
    assertEquals(1, cal.getDonacionesConQueja());
    assertEquals(1.0, cal.getProporcionQuejas());
  }

  @Test
  void unaDonacionEliminadaSeBorraYSeRecalculaElDonador() {
    DonacionDocument existente = documento("ACEPTADA");
    when(donacionRepository.findById("don-1")).thenReturn(Optional.of(existente));
    when(donacionRepository.findByDonadorId("d1")).thenReturn(List.of());

    servicio.procesar(evento("e3", "DONACION_ELIMINADA", T0.plusSeconds(60), donacion("ACEPTADA")));

    verify(donacionRepository).deleteById("don-1");
    verify(donacionRepository, never()).save(any());
    ArgumentCaptor<DonadorDocument> donador = ArgumentCaptor.forClass(DonadorDocument.class);
    verify(donadorRepository).save(donador.capture());
    assertEquals(0, donador.getValue().getDonacionesPorEstado().get("ACEPTADA"));
  }

  @Test
  void laEntregaDeUnaAsignacionActualizaLasEntidadesDelDonadorYLasEstadisticasDelDeposito() {
    DonacionDocument don = documento("ACEPTADA");
    AsignacionDocument completada = AsignacionDocument.builder().id("a1").donacionId("don-1").necesidadId("n1")
        .entidadId("4").entidadRazonSocial("Comedor Los Girasoles").cantidad(10).estado("COMPLETADA")
        .fechaEntrega(T0.plusSeconds(7200)).build();
    when(asignacionRepository.findById("a1")).thenReturn(Optional.empty());
    when(necesidadRepository.findById("n1")).thenReturn(Optional.of(
        NecesidadUrgenteDocument.builder().id("n1").entidadId("4").entidadRazonSocial("Comedor Los Girasoles").build()));
    when(donacionRepository.findById("don-1")).thenReturn(Optional.of(don));
    when(donacionRepository.findByDonadorId("d1")).thenReturn(List.of(don));
    when(donacionRepository.findByDepositoId("3")).thenReturn(List.of(don));
    when(asignacionRepository.findByDonacionIdIn(any())).thenReturn(List.of(completada));
    DepositoDocument deposito = DepositoDocument.builder().id("3").nombre("Central").build();
    when(depositoRepository.findById("3")).thenReturn(Optional.of(deposito));

    EventoGenerico e = EventoGenerico.builder().id("e4").tipo("ENTREGA_COMPLETADA").idReferencia("a1")
        .timestamp(T0.plusSeconds(7200)).build();
    e.setAsignacion(Map.of("id", "a1", "donacionId", "don-1", "necesidadId", "n1", "cantidad", 10,
        "estado", "COMPLETADA", "fechaEntrega", "2026-11-06T12:00:00Z"));

    servicio.procesar(e);

    ArgumentCaptor<DonadorDocument> donador = ArgumentCaptor.forClass(DonadorDocument.class);
    verify(donadorRepository).save(donador.capture());
    assertEquals(1, donador.getValue().getEntidadesBeneficiadas().size());
    assertEquals("Comedor Los Girasoles", donador.getValue().getEntidadesBeneficiadas().get(0).getRazonSocial());
    assertEquals(10, donador.getValue().getEntidadesBeneficiadas().get(0).getUnidades());

    assertEquals(1, deposito.getAsignacionesPorEstado().get("COMPLETADA"));
    assertEquals(120.0, deposito.getTiempoPromedioEntregaMinutos());
    assertEquals(Set.of("4"), deposito.getEntidadesDistintasAtendidas());
    assertEquals(1, deposito.getCantidadEntidadesDistintas());
  }

  @Test
  void elTiempoPromedioYLasEntidadesRespetanElRangoDeFechas() {
    DonacionDocument d1 = DonacionDocument.builder().id("d1").fechaIngreso(Instant.parse("2026-11-06T10:00:00Z")).build();
    DonacionDocument d2 = DonacionDocument.builder().id("d2").fechaIngreso(Instant.parse("2026-11-06T11:00:00Z")).build();
    List<AsignacionDocument> asignaciones = List.of(
        AsignacionDocument.builder().id("a1").donacionId("d1").estado("COMPLETADA").entidadId("4")
            .fechaEntrega(Instant.parse("2026-11-06T12:00:00Z")).build(),
        AsignacionDocument.builder().id("a2").donacionId("d2").estado("COMPLETADA").entidadId("5")
            .fechaEntrega(Instant.parse("2026-11-06T14:00:00Z")).build(),
        AsignacionDocument.builder().id("a3").donacionId("d2").estado("ASIGNADA").build(),
        AsignacionDocument.builder().id("a4").donacionId("de-otro-deposito").estado("COMPLETADA").entidadId("9")
            .fechaEntrega(Instant.parse("2026-11-06T13:00:00Z")).build());

    EstadisticasDeposito todo = EstadisticasDeposito.calcular(List.of(d1, d2), asignaciones, null, null);
    assertEquals(150.0, todo.getTiempoPromedioEntregaMinutos());
    assertEquals(Set.of("4", "5"), todo.getEntidadesDistintas());
    assertEquals(1, todo.getAsignacionesPorEstado().get("ASIGNADA"));
    assertEquals(2, todo.getAsignacionesPorEstado().get("COMPLETADA"));

    EstadisticasDeposito soloLaTarde =
        EstadisticasDeposito.calcular(List.of(d1, d2), asignaciones, Instant.parse("2026-11-06T13:00:00Z"), null);
    assertEquals(180.0, soloLaTarde.getTiempoPromedioEntregaMinutos());
    assertEquals(Set.of("5"), soloLaTarde.getEntidadesDistintas());
    assertEquals(2, soloLaTarde.getAsignacionesPorEstado().get("COMPLETADA"));
  }

  @Test
  void sinEntregasNoHayTiempoPromedio() {
    EstadisticasDeposito vacia = EstadisticasDeposito.calcular(List.of(), List.of(), null, null);
    assertEquals(null, vacia.getTiempoPromedioEntregaMinutos());
    assertTrue(vacia.getEntidadesDistintas().isEmpty());
    assertEquals(0, vacia.getAsignacionesPorEstado().get("ASIGNADA"));
  }
}

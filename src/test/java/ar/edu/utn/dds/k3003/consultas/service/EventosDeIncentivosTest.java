package ar.edu.utn.dds.k3003.consultas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.utn.dds.k3003.consultas.documents.DonadorDocument;
import ar.edu.utn.dds.k3003.consultas.documents.HistorialCategoria;
import ar.edu.utn.dds.k3003.consultas.documents.MisionEnCurso;
import ar.edu.utn.dds.k3003.consultas.messaging.EventoGenerico;
import ar.edu.utn.dds.k3003.consultas.repositories.AsignacionDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.CalidadDonacionRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DepositoDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonadorDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.EventoProcesadoRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.NecesidadDocumentRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Eventos que publica Incentivos. Los JSON son los que arma {@code PublicadorEventosIncentivos} en los
 * casos de {@code PublicadorEventosIncentivosTest} (repo de Incentivos, carpeta messaging/), y se leen
 * con la misma configuración de Jackson que usa {@code RabbitMQConfig}.
 */
@ExtendWith(MockitoExtension.class)
class EventosDeIncentivosTest {

  /** donadorActualizadoLlevaElEstadoCompleto: d-1, OCASIONAL -> COLABORADOR, misión 4/10. */
  private static final String DONADOR_ACTUALIZADO = """
      {"id":"6f1c2a9e-1b7d-4c55-9a0e-1f2b3c4d5e6f","tipo":"DONADOR_ACTUALIZADO",
       "timestamp":"2026-11-06T15:00:00.123456Z","idReferencia":"d-1",
       "donador":{"id":"d-1","categoriaActual":"COLABORADOR",
         "historialCategorias":[{"categoria":"OCASIONAL","desde":"2026-10-01T10:00:00Z"},
                                {"categoria":"COLABORADOR","desde":"2026-11-01T10:00:00Z"}],
         "insignias":[{"id":"i1","nombre":"Primeros pasos","descripcion":"desc"}],
         "misionEnCurso":{"id":"m2","nombre":"Donaciones Exitosas","tipo":"DONACIONES_EXITOSAS",
                          "progreso":4,"objetivo":10},
         "misionesCompletadas":[{"id":"m1","nombre":"Primeros pasos"}],
         "fechaEvento":"2026-11-06T15:00:00.123456Z"},
       "extra":{"motivo":"MISION_AVANZADA"}}
      """;

  /** donadorSinMisionNiHistorialMandaNulosExplicitos: d-2 recién registrado. */
  private static final String DONADOR_SIN_MISION = """
      {"id":"0a1b2c3d-0000-4000-8000-000000000002","tipo":"DONADOR_ACTUALIZADO",
       "timestamp":"2026-11-06T15:05:00Z","idReferencia":"d-2",
       "donador":{"id":"d-2","categoriaActual":null,"historialCategorias":[],"insignias":[],
         "misionEnCurso":null,"misionesCompletadas":[],"fechaEvento":"2026-11-06T15:05:00Z"},
       "extra":{"motivo":"REGISTRADO"}}
      """;

  /** abmDeInsigniasYMisiones y eliminacionSinEstadoPrevioMandaSoloElId. */
  private static final String INSIGNIA_CREADA = """
      {"id":"e-ins-1","tipo":"INSIGNIA_CREADA","timestamp":"2026-11-06T15:10:00Z","idReferencia":"i1",
       "insignia":{"id":"i1","nombre":"Oro","descripcion":"desc"},"extra":{}}
      """;
  private static final String INSIGNIA_ELIMINADA = """
      {"id":"e-ins-2","tipo":"INSIGNIA_ELIMINADA","timestamp":"2026-11-06T15:11:00Z","idReferencia":"i9",
       "insignia":{"id":"i9","nombre":null,"descripcion":null},"extra":{}}
      """;
  private static final String MISION_MODIFICADA = """
      {"id":"e-mis-1","tipo":"MISION_MODIFICADA","timestamp":"2026-11-06T15:12:00Z","idReferencia":"m1",
       "mision":{"id":"m1","nombre":"Gran benefactor","insigniaId":"i1","categoriaInicio":"SALVADOR",
                 "categoriaFin":"REVOLUCIONARIO","tipo":"REVOLUCION_DONADORA","cantidadRequerida":null,
                 "objetivo":11},
       "extra":{}}
      """;
  private static final String MISION_ELIMINADA = """
      {"id":"e-mis-2","tipo":"MISION_ELIMINADA","timestamp":"2026-11-06T15:13:00Z","idReferencia":"m9",
       "mision":{"id":"m9","nombre":null,"insigniaId":null,"categoriaInicio":null,"categoriaFin":null,
                 "tipo":null,"cantidadRequerida":null,"objetivo":null},
       "extra":{}}
      """;

  /** baseLimpiada. */
  private static final String BASE_LIMPIADA = """
      {"id":"e-base-1","tipo":"BASE_LIMPIADA","timestamp":"2026-11-06T16:00:00Z","idReferencia":"incentivos",
       "extra":{}}
      """;

  private final ObjectMapper json = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Mock private DonadorDocumentRepository donadorRepository;
  @Mock private NecesidadDocumentRepository necesidadRepository;
  @Mock private DepositoDocumentRepository depositoRepository;
  @Mock private CalidadDonacionRepository calidadRepository;
  @Mock private AsignacionDocumentRepository asignacionRepository;
  @Mock private EventoProcesadoRepository eventosProcesados;

  private EventProcessingService servicio;

  @BeforeEach
  void preparar() {
    servicio = new EventProcessingService(donadorRepository, necesidadRepository, depositoRepository, calidadRepository);
    ReflectionTestUtils.setField(servicio, "asignacionRepository", asignacionRepository);
    ReflectionTestUtils.setField(servicio, "eventosProcesados", eventosProcesados);
  }

  private EventoGenerico leer(String mensaje) throws Exception {
    return json.readValue(mensaje, EventoGenerico.class);
  }

  private DonadorDocument guardado() {
    ArgumentCaptor<DonadorDocument> captor = ArgumentCaptor.forClass(DonadorDocument.class);
    verify(donadorRepository).save(captor.capture());
    return captor.getValue();
  }

  @Test
  void donadorActualizadoDeUnDonadorNuevoLoCreaConLosCamposDeIncentivos() throws Exception {
    when(donadorRepository.findById("d-1")).thenReturn(Optional.empty());

    servicio.procesar(leer(DONADOR_ACTUALIZADO));

    DonadorDocument d = guardado();
    assertEquals("d-1", d.getId());
    assertEquals("COLABORADOR", d.getCategoria());
    assertEquals(List.of(
        HistorialCategoria.builder().categoria("OCASIONAL").desde("2026-10-01").build(),
        HistorialCategoria.builder().categoria("COLABORADOR").desde("2026-11-01").build()),
        d.getHistorialCategorias());
    assertEquals(List.of("Primeros pasos"), d.getInsignias());
    assertEquals(MisionEnCurso.builder().nombre("Donaciones Exitosas").progreso(4).objetivo(10).build(),
        d.getMisionEnCurso());
    assertEquals(List.of("Primeros pasos"), d.getMisionesCompletadas());
    assertEquals(Instant.parse("2026-11-06T15:00:00.123456Z"), d.getUltimaActualizacionIncentivos());
    assertNull(d.getUltimaActualizacion(), "el timestamp compartido con los otros módulos no se toca");
  }

  @Test
  void donadorActualizadoReemplazaLosCamposDeIncentivosYConservaLosDeOtrosModulos() throws Exception {
    Instant deDonadores = Instant.parse("2026-11-06T18:00:00Z"); // más nuevo que el evento de Incentivos
    DonadorDocument existente = DonadorDocument.builder()
        .id("d-1").nombre("Lucía").estado("VERIFICADO").quejasRecibidas(2)
        .insignias(new ArrayList<>(List.of("Vieja")))
        .misionEnCurso(MisionEnCurso.builder().nombre("Vieja").progreso(1).objetivo(3).build())
        .ultimaActualizacion(deDonadores)
        .ultimaActualizacionIncentivos(Instant.parse("2026-11-06T14:00:00Z"))
        .build();
    when(donadorRepository.findById("d-1")).thenReturn(Optional.of(existente));

    servicio.procesar(leer(DONADOR_ACTUALIZADO));

    DonadorDocument d = guardado();
    assertEquals(List.of("Primeros pasos"), d.getInsignias());
    assertEquals("Donaciones Exitosas", d.getMisionEnCurso().getNombre());
    assertEquals("Lucía", d.getNombre());
    assertEquals("VERIFICADO", d.getEstado());
    assertEquals(2, d.getQuejasRecibidas());
    assertEquals(deDonadores, d.getUltimaActualizacion());
  }

  @Test
  void donadorActualizadoSinMisionNiHistorialDejaTodoVacio() throws Exception {
    DonadorDocument existente = DonadorDocument.builder()
        .id("d-2").categoria("OCASIONAL")
        .insignias(new ArrayList<>(List.of("Vieja")))
        .misionEnCurso(MisionEnCurso.builder().nombre("Vieja").build())
        .build();
    when(donadorRepository.findById("d-2")).thenReturn(Optional.of(existente));

    servicio.procesar(leer(DONADOR_SIN_MISION));

    DonadorDocument d = guardado();
    assertNull(d.getCategoria());
    assertTrue(d.getHistorialCategorias().isEmpty());
    assertTrue(d.getInsignias().isEmpty());
    assertNull(d.getMisionEnCurso());
    assertTrue(d.getMisionesCompletadas().isEmpty());
  }

  @Test
  void unDonadorActualizadoMasViejoQueElUltimoDeIncentivosSeDescarta() throws Exception {
    DonadorDocument existente = DonadorDocument.builder()
        .id("d-1").categoria("TRANSFORMADOR")
        .ultimaActualizacionIncentivos(Instant.parse("2026-11-06T15:30:00Z"))
        .build();
    when(donadorRepository.findById("d-1")).thenReturn(Optional.of(existente));

    servicio.procesar(leer(DONADOR_ACTUALIZADO));

    verify(donadorRepository, never()).save(any());
    assertEquals("TRANSFORMADOR", existente.getCategoria());
  }

  @Test
  void losEventosDeInsigniasYMisionesNoTocanNiCreanDonadores() throws Exception {
    for (String mensaje : List.of(INSIGNIA_CREADA, INSIGNIA_ELIMINADA, MISION_MODIFICADA, MISION_ELIMINADA)) {
      servicio.procesar(leer(mensaje));
    }

    verifyNoInteractions(donadorRepository);
  }

  @Test
  void baseLimpiadaDeIncentivosBorraInsigniasYMisionesSinBorrarDonadores() throws Exception {
    DonadorDocument donador = DonadorDocument.builder()
        .id("d-1").categoria("COLABORADOR")
        .historialCategorias(new ArrayList<>(List.of(
            HistorialCategoria.builder().categoria("COLABORADOR").desde("2026-11-01").build())))
        .insignias(new ArrayList<>(List.of("Primeros pasos")))
        .misionEnCurso(MisionEnCurso.builder().nombre("Donaciones Exitosas").progreso(4).objetivo(10).build())
        .misionesCompletadas(new ArrayList<>(List.of("Primeros pasos")))
        .build();
    when(donadorRepository.findAll()).thenReturn(List.of(donador));

    servicio.procesar(leer(BASE_LIMPIADA));

    verify(donadorRepository).saveAll(List.of(donador));
    verify(donadorRepository, never()).deleteAll();
    verify(depositoRepository, never()).deleteAll();
    verify(asignacionRepository, never()).deleteAll();
    assertTrue(donador.getInsignias().isEmpty());
    assertNull(donador.getMisionEnCurso());
    assertTrue(donador.getMisionesCompletadas().isEmpty());
    assertEquals("COLABORADOR", donador.getCategoria());
    assertEquals(1, donador.getHistorialCategorias().size());
    assertEquals(Instant.parse("2026-11-06T16:00:00Z"), donador.getUltimaActualizacionIncentivos());
  }
}

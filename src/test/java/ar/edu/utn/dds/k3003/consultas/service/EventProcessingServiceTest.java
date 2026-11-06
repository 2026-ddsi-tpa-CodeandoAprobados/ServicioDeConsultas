package ar.edu.utn.dds.k3003.consultas.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ar.edu.utn.dds.k3003.consultas.documents.CalidadDonacionDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DonadorDocument;
import ar.edu.utn.dds.k3003.consultas.documents.NecesidadUrgenteDocument;
import ar.edu.utn.dds.k3003.consultas.messaging.EventoGenerico;
import ar.edu.utn.dds.k3003.consultas.repositories.CalidadDonacionRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DepositoDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonadorDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.NecesidadDocumentRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EventProcessingServiceTest {

  @Mock private DonadorDocumentRepository donadorRepository;
  @Mock private NecesidadDocumentRepository necesidadRepository;
  @Mock private DepositoDocumentRepository depositoRepository;
  @Mock private CalidadDonacionRepository calidadRepository;

  private EventProcessingService eventProcessingService;

  @BeforeEach
  void setUp() {
    eventProcessingService =
        new EventProcessingService(
            donadorRepository,
            necesidadRepository,
            depositoRepository,
            calidadRepository);
  }

  @Test
  void testProcesarDonadorCreado() {
    Map<String, Object> donadorData = new HashMap<>();
    donadorData.put("id", "12");
    donadorData.put("nombre", "Lucia");
    donadorData.put("estado", "VERIFICADO");
    donadorData.put("categoria", "COLABORADOR");

    EventoGenerico evento =
        EventoGenerico.builder()
            .id("e1")
            .tipo("DONADOR_CREADO")
            .timestamp(Instant.now())
            .idReferencia("12")
            .donador(donadorData)
            .build();

    when(donadorRepository.findById("12")).thenReturn(Optional.empty());

    eventProcessingService.procesar(evento);

    ArgumentCaptor<DonadorDocument> captor = ArgumentCaptor.forClass(DonadorDocument.class);
    verify(donadorRepository, times(1)).save(captor.capture());

    DonadorDocument doc = captor.getValue();
    assertEquals("12", doc.getId());
    assertEquals("Lucia", doc.getNombre());
    assertEquals("COLABORADOR", doc.getCategoria());
    assertEquals(1, doc.getHistorialCategorias().size());
  }

  @Test
  void testIdempotenciaEventoObsoletoIgnorado() {
    Instant ahora = Instant.now();
    Instant antes = ahora.minusSeconds(60);

    DonadorDocument existente =
        DonadorDocument.builder()
            .id("12")
            .nombre("Lucia")
            .categoria("COLABORADOR")
            .ultimaActualizacion(ahora)
            .build();

    when(donadorRepository.findById("12")).thenReturn(Optional.of(existente));

    Map<String, Object> data = Map.of("id", "12", "nombre", "Lucia Vieja");
    EventoGenerico eventoViejo =
        EventoGenerico.builder()
            .id("e-viejo")
            .tipo("DONADOR_CREADO")
            .timestamp(antes)
            .idReferencia("12")
            .donador(data)
            .build();

    eventProcessingService.procesar(eventoViejo);

    verify(donadorRepository, never()).save(any());
  }

  @Test
  void testProcesarQuejaRegistradaIncrementaQuejasYCambiaEstado() {
    DonadorDocument donador =
        DonadorDocument.builder()
            .id("12")
            .nombre("Lucia")
            .estado("VERIFICADO")
            .quejasRecibidas(4)
            .build();

    when(donadorRepository.findById("12")).thenReturn(Optional.of(donador));

    Map<String, Object> quejaData = new HashMap<>();
    quejaData.put("donadorID", "12");
    quejaData.put("categoriaProducto", "Alimentos");
    quejaData.put("subcategoriaProducto", "Pastas");

    when(calidadRepository.findById("Alimentos_Pastas")).thenReturn(Optional.empty());

    EventoGenerico evento =
        EventoGenerico.builder()
            .id("e3")
            .tipo("QUEJA_REGISTRADA")
            .timestamp(Instant.now())
            .idReferencia("12")
            .queja(quejaData)
            .build();

    eventProcessingService.procesar(evento);

    ArgumentCaptor<DonadorDocument> captor = ArgumentCaptor.forClass(DonadorDocument.class);
    verify(donadorRepository, times(1)).save(captor.capture());

    DonadorDocument saved = captor.getValue();
    assertEquals(5, saved.getQuejasRecibidas());
    assertEquals("SOSPECHOSO", saved.getEstado());

    verify(calidadRepository, times(1)).save(any(CalidadDonacionDocument.class));
  }

  @Test
  void testProcesarNecesidadModificadaASatisfecha() {
    NecesidadUrgenteDocument nec =
        NecesidadUrgenteDocument.builder()
            .id("n1")
            .cantidadObjetivo(50)
            .cantidadFaltante(50)
            .satisfecha(false)
            .build();

    when(necesidadRepository.findById("n1")).thenReturn(Optional.of(nec));

    Map<String, Object> extra = new HashMap<>();
    extra.put("cantidadObjetivoRestante", 0);

    EventoGenerico evento =
        EventoGenerico.builder()
            .id("e4")
            .tipo("NECESIDAD_SATISFECHA")
            .timestamp(Instant.now())
            .idReferencia("n1")
            .extra(extra)
            .build();

    eventProcessingService.procesar(evento);

    ArgumentCaptor<NecesidadUrgenteDocument> captor =
        ArgumentCaptor.forClass(NecesidadUrgenteDocument.class);
    verify(necesidadRepository, times(1)).save(captor.capture());

    assertTrue(captor.getValue().getSatisfecha());
    assertEquals(0, captor.getValue().getCantidadFaltante());
  }
}

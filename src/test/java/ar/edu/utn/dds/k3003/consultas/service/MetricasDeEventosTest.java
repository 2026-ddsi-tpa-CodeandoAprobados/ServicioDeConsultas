package ar.edu.utn.dds.k3003.consultas.service;

import static ar.edu.utn.dds.k3003.consultas.support.EventosReales.donadorCreado;
import static ar.edu.utn.dds.k3003.consultas.support.EventosReales.donadorEstado;
import static ar.edu.utn.dds.k3003.consultas.support.EventosReales.quejaRegistrada;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import ar.edu.utn.dds.k3003.consultas.documents.DonadorDocument;
import ar.edu.utn.dds.k3003.consultas.messaging.EventoGenerico;
import ar.edu.utn.dds.k3003.consultas.metrics.ConsultasMetrics;
import ar.edu.utn.dds.k3003.consultas.repositories.DepositoDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonadorDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.EventoProcesadoRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.NecesidadDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.support.MongoEnMemoria;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Los contadores de eventos recibidos, procesados, ignorados y con error. */
class MetricasDeEventosTest {

  private SimpleMeterRegistry registry;
  private DonadorDocumentRepository donadores;
  private EventProcessingService servicio;

  @BeforeEach
  void preparar() {
    registry = new SimpleMeterRegistry();
    donadores = MongoEnMemoria.repositorio(DonadorDocumentRepository.class);
    servicio = new EventProcessingService(donadores,
        MongoEnMemoria.repositorio(NecesidadDocumentRepository.class),
        MongoEnMemoria.repositorio(DepositoDocumentRepository.class));
    ReflectionTestUtils.setField(servicio, "eventosProcesados", MongoEnMemoria.repositorio(EventoProcesadoRepository.class));
    servicio.setMetricas(new ConsultasMetrics(registry));
  }

  private double contador(String nombre, String... etiquetas) {
    Counter c = registry.find(nombre).tags(etiquetas).counter();
    return c == null ? 0 : c.count();
  }

  @Test
  void unEventoAplicadoCuentaComoRecibidoYProcesado() {
    servicio.procesar(donadorCreado("2026-11-06T09:00:00Z", "12", "VERIFICADO", "OCASIONAL"));

    assertEquals(1, contador(ConsultasMetrics.RECIBIDOS, "tipo", "DONADOR_CREADO"));
    assertEquals(1, contador(ConsultasMetrics.PROCESADOS, "tipo", "DONADOR_CREADO"));
    assertEquals(0, contador(ConsultasMetrics.IGNORADOS));
  }

  @Test
  void elMismoEventoDosVecesSeIgnoraPorRepetido() {
    EventoGenerico e = donadorCreado("2026-11-06T09:00:00Z", "12", "VERIFICADO", "OCASIONAL");
    servicio.procesar(e);
    servicio.procesar(e);

    assertEquals(2, contador(ConsultasMetrics.RECIBIDOS, "tipo", "DONADOR_CREADO"));
    assertEquals(1, contador(ConsultasMetrics.PROCESADOS, "tipo", "DONADOR_CREADO"));
    assertEquals(1, contador(ConsultasMetrics.IGNORADOS, "tipo", "DONADOR_CREADO", "motivo", "repetido"));
  }

  @Test
  void unEventoMasViejoQueLoGuardadoSeIgnoraPorObsoleto() {
    servicio.procesar(donadorEstado("2026-11-06T12:00:00Z", "12", "SOSPECHOSO"));
    servicio.procesar(donadorEstado("2026-11-06T10:00:00Z", "12", "VERIFICADO"));

    assertEquals(1, contador(ConsultasMetrics.PROCESADOS, "tipo", "DONADOR_ESTADO_MODIFICADO"));
    assertEquals(1, contador(ConsultasMetrics.IGNORADOS, "tipo", "DONADOR_ESTADO_MODIFICADO", "motivo", "obsoleto"));
  }

  @Test
  void unaQuejaYaContadaSeIgnoraPorRepetidaYUnaAtrasadaIgualSeProcesa() {
    servicio.procesar(donadorEstado("2026-11-06T12:00:00Z", "12", "VERIFICADO"));
    servicio.procesar(quejaRegistrada("ev-1", "2026-11-06T10:00:00Z", "q1", "7", "12"));
    servicio.procesar(quejaRegistrada("ev-2", "2026-11-06T10:00:01Z", "q1", "7", "12"));

    assertEquals(1, contador(ConsultasMetrics.PROCESADOS, "tipo", "QUEJA_REGISTRADA"));
    assertEquals(1, contador(ConsultasMetrics.IGNORADOS, "tipo", "QUEJA_REGISTRADA", "motivo", "repetido"));
  }

  @Test
  void losEventosDelCatalogoDeIncentivosSeIgnoranPorCatalogo() {
    servicio.procesar(EventoGenerico.builder().id("e1").tipo("INSIGNIA_CREADA").idReferencia("1")
        .timestamp(Instant.now()).build());

    assertEquals(1, contador(ConsultasMetrics.IGNORADOS, "tipo", "INSIGNIA_CREADA", "motivo", "catalogo"));
  }

  @Test
  void unTipoDesconocidoSeIgnoraComoNoMapeadoYSeEtiquetaComoOtro() {
    servicio.procesar(EventoGenerico.builder().id("e1").tipo("ALGO_" + System.nanoTime()).build());

    assertEquals(1, contador(ConsultasMetrics.RECIBIDOS, "tipo", "OTRO"));
    assertEquals(1, contador(ConsultasMetrics.IGNORADOS, "tipo", "OTRO", "motivo", "no_mapeado"));
  }

  @Test
  void unEventoSinTipoSeIgnoraPorInvalido() {
    servicio.procesar(EventoGenerico.builder().id("e1").build());

    assertEquals(1, contador(ConsultasMetrics.IGNORADOS, "tipo", "OTRO", "motivo", "invalido"));
  }

  @Test
  void unErrorAlProcesarSeCuentaYNoCambiaElManejoDeExcepciones() {
    DonadorDocumentRepository fallando = mock(DonadorDocumentRepository.class);
    doThrow(new IllegalStateException("Mongo caído")).when(fallando).save(any(DonadorDocument.class));
    EventProcessingService conError = new EventProcessingService(fallando,
        MongoEnMemoria.repositorio(NecesidadDocumentRepository.class),
        MongoEnMemoria.repositorio(DepositoDocumentRepository.class));
    conError.setMetricas(new ConsultasMetrics(registry));

    assertDoesNotThrow(() -> conError.procesar(donadorCreado("2026-11-06T09:00:00Z", "12", "VERIFICADO", "OCASIONAL")));

    assertEquals(1, contador(ConsultasMetrics.ERRORES, "tipo", "DONADOR_CREADO"));
    assertEquals(0, contador(ConsultasMetrics.PROCESADOS, "tipo", "DONADOR_CREADO"));
  }
}

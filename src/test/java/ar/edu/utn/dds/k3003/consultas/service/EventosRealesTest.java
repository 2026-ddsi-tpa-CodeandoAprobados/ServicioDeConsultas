package ar.edu.utn.dds.k3003.consultas.service;

import static ar.edu.utn.dds.k3003.consultas.support.EventosReales.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ar.edu.utn.dds.k3003.consultas.documents.AsignacionDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DepositoDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DonadorDocument;
import ar.edu.utn.dds.k3003.consultas.documents.EntidadBeneficiadaResumen;
import ar.edu.utn.dds.k3003.consultas.documents.NecesidadUrgenteDocument;
import ar.edu.utn.dds.k3003.consultas.messaging.EventoGenerico;
import ar.edu.utn.dds.k3003.consultas.repositories.AsignacionDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DepositoDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonacionDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonadorDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.EntidadDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.EventoProcesadoRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.NecesidadDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.support.MongoEnMemoria;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Secuencias de eventos con el JSON real de cada módulo, en el orden en que llegan de verdad, contra un
 * modelo de lectura en memoria. Cubren lo que necesitan las consultas 1, 2 y 3.
 */
class EventosRealesTest {

  private DonadorDocumentRepository donadores;
  private NecesidadDocumentRepository necesidades;
  private DepositoDocumentRepository depositos;
  private AsignacionDocumentRepository asignaciones;
  private DonacionDocumentRepository donaciones;
  private EntidadDocumentRepository entidades;
  private EventProcessingService servicio;

  @BeforeEach
  void preparar() {
    donadores = MongoEnMemoria.repositorio(DonadorDocumentRepository.class);
    necesidades = MongoEnMemoria.repositorio(NecesidadDocumentRepository.class);
    depositos = MongoEnMemoria.repositorio(DepositoDocumentRepository.class);
    asignaciones = MongoEnMemoria.repositorio(AsignacionDocumentRepository.class);
    donaciones = MongoEnMemoria.repositorio(DonacionDocumentRepository.class);
    entidades = MongoEnMemoria.repositorio(EntidadDocumentRepository.class);
    servicio = new EventProcessingService(donadores, necesidades, depositos);
    ReflectionTestUtils.setField(servicio, "asignacionRepository", asignaciones);
    ReflectionTestUtils.setField(servicio, "eventosProcesados", MongoEnMemoria.repositorio(EventoProcesadoRepository.class));
    ReflectionTestUtils.setField(servicio, "donacionRepository", donaciones);
    ReflectionTestUtils.setField(servicio, "entidadRepository", entidades);
  }

  private void procesar(EventoGenerico... eventos) {
    for (EventoGenerico e : eventos) servicio.procesar(e);
  }

  private DonadorDocument donador(String id) {
    return donadores.findById(id).orElseThrow();
  }

  private NecesidadUrgenteDocument necesidad(String id) {
    return necesidades.findById(id).orElseThrow();
  }

  /**
   * El circuito completo como pasa en DonaTrack: Donaciones registra la donación (Logística la guarda en el
   * depósito), Donadores registra la necesidad pidiéndole antes a Logística que asigne, y Logística entrega.
   */
  private void circuitoCompleto() {
    procesar(
        entidadCreada("2026-11-06T09:00:00Z", "4", "Comedor Los Girasoles"),
        donadorCreado("2026-11-06T09:00:01Z", "12", "VERIFICADO", "OCASIONAL"),
        depositoStock("2026-11-06T10:00:00.250Z", "3", "5", 10, "7"),
        donacion("DONACION_CREADA", "2026-11-06T10:00:00.300Z", "7", "12", "3", "INGRESADA", "5", 10),
        donacion("DONACION_ESTADO_MODIFICADO", "2026-11-06T10:05:00Z", "7", "12", "3", "ACEPTADA", "5", 10),
        // Donadores llama a Logística antes de publicar la necesidad: la asignación llega primero.
        asignacionCreada("2026-11-06T11:00:00Z", "a1", "7", "5", 10, "n1"),
        necesidadRegistrada("2026-11-06T11:00:00.500Z", "n1", "4", 9, 200, "5"));
  }

  // ---------------- Consulta 1 ----------------

  @Test
  void laFichaDelDonadorSeArmaConLosEventosDeDonacionesDonadoresYLogistica() {
    circuitoCompleto();
    procesar(entregaCompletada("2026-11-06T13:00:00Z", "a1", "7", "5", 10, "n1", "2026-11-06T11:00:00Z"));

    DonadorDocument d = donador("12");
    assertEquals("Lucía", d.getNombre());
    assertEquals("VERIFICADO", d.getEstado());
    assertEquals(1, d.getDonacionesPorEstado().get("ACEPTADA"));
    assertEquals(0, d.getDonacionesPorEstado().get("INGRESADA"));
    assertEquals(List.of(new EntidadBeneficiadaResumen("4", "Comedor Los Girasoles", 10)), d.getEntidadesBeneficiadas());
  }

  @Test
  void unaDonacionConQuejaSeCuentaComoConQuejaYLaQuejaSumaUna() {
    circuitoCompleto();
    procesar(
        donacion("DONACION_ESTADO_MODIFICADO", "2026-11-06T12:00:00Z", "7", "12", "3", "CONQUEJA", "5", 10),
        quejaRegistrada("ev-q1", "2026-11-06T12:00:01Z", "q1", "7", "12"));

    DonadorDocument d = donador("12");
    assertEquals(1, d.getDonacionesPorEstado().get("CONQUEJA"));
    assertEquals(0, d.getDonacionesPorEstado().get("ACEPTADA"));
    assertEquals(1, d.getQuejasRecibidas());
  }

  @Test
  void laMismaQuejaEnDosEventosDistintosSeCuentaUnaVez() {
    procesar(
        donadorCreado("2026-11-06T09:00:00Z", "12", "VERIFICADO", "OCASIONAL"),
        quejaRegistrada("ev-1", "2026-11-06T12:00:00Z", "q1", "7", "12"),
        quejaRegistrada("ev-2", "2026-11-06T12:00:05Z", "q1", "7", "12"),
        quejaRegistrada("ev-3", "2026-11-06T12:01:00Z", "q2", "8", "12"));

    assertEquals(2, donador("12").getQuejasRecibidas());
    assertEquals(List.of("q1", "q2"), donador("12").getQuejaIds());
  }

  @Test
  void unaQuejaQueLlegaFueraDeOrdenSeCuentaPeroNoPisaUnEstadoPosterior() {
    procesar(donadorCreado("2026-11-06T09:00:00Z", "12", "VERIFICADO", "OCASIONAL"));
    for (int i = 1; i <= 4; i++) {
      procesar(quejaRegistrada("ev-" + i, "2026-11-06T10:0" + i + ":00Z", "q" + i, "7", "12"));
    }
    // Donadores publica SOSPECHOSO (5.ª queja) y después lo vuelven a VERIFICADO; la 5.ª queja llega última.
    procesar(
        donadorEstado("2026-11-06T11:00:00Z", "12", "SOSPECHOSO"),
        donadorEstado("2026-11-06T12:00:00Z", "12", "VERIFICADO"),
        quejaRegistrada("ev-5", "2026-11-06T11:00:00.001Z", "q5", "7", "12"));

    assertEquals(5, donador("12").getQuejasRecibidas());
    assertEquals("VERIFICADO", donador("12").getEstado());
  }

  @Test
  void laQuintaQuejaEnOrdenDejaAlDonadorSospechoso() {
    procesar(donadorCreado("2026-11-06T09:00:00Z", "12", "VERIFICADO", "OCASIONAL"));
    for (int i = 1; i <= 5; i++) {
      procesar(quejaRegistrada("ev-" + i, "2026-11-06T10:0" + i + ":00Z", "q" + i, "7", "12"));
    }
    assertEquals("SOSPECHOSO", donador("12").getEstado());
  }

  @Test
  void losEventosDeIncentivosYDeDonadoresNoSeDescartanEntreSi() {
    procesar(
        donadorCreado("2026-11-06T09:00:00Z", "12", "VERIFICADO", "OCASIONAL"),
        donadorActualizado("2026-11-06T15:00:00Z", "12", "COLABORADOR"),
        // Más viejo que el de Incentivos, pero es el primero de Donadores después del alta: se aplica.
        donadorEstado("2026-11-06T10:00:00Z", "12", "SOSPECHOSO"));

    DonadorDocument d = donador("12");
    assertEquals("SOSPECHOSO", d.getEstado());
    assertEquals("COLABORADOR", d.getCategoria());
    assertEquals(List.of("Completitud"), d.getInsignias());
  }

  @Test
  void laCategoriaDeDonadoresNoDuplicaElHistorialQueYaMandoIncentivos() {
    procesar(
        donadorCreado("2026-11-06T09:00:00Z", "12", "VERIFICADO", "OCASIONAL"),
        donadorActualizado("2026-11-06T15:00:00Z", "12", "COLABORADOR"),
        donadorCategoria("2026-11-06T15:00:01Z", "12", "COLABORADOR"));

    assertEquals(2, donador("12").getHistorialCategorias().size());
  }

  @Test
  void siDonadoresVaciaSuBaseSeBorranLosDonadores() {
    procesar(
        donadorCreado("2026-11-06T09:00:00Z", "12", "VERIFICADO", "OCASIONAL"),
        donadoresTodosEliminados("2026-11-06T10:00:00Z"));

    assertTrue(donadores.findAll().isEmpty());
  }

  // ---------------- Consulta 2 ----------------

  @Test
  void laNecesidadTomaLaUrgenciaLaEntidadYNoPierdeLoQueAportoLogistica() {
    circuitoCompleto();

    NecesidadUrgenteDocument n = necesidad("n1");
    assertEquals(9, n.getUrgencia());
    assertEquals("EXTRAORDINARIA", n.getTipoNecesidad());
    assertEquals("4", n.getEntidadId());
    assertEquals("Comedor Los Girasoles", n.getEntidadRazonSocial());
    assertEquals(200, n.getCantidadObjetivo());
    assertEquals(200, n.getCantidadFaltante());
    assertFalse(n.getSatisfecha());
    // La asignación y el stock llegaron antes que la necesidad.
    assertEquals(10, n.getUnidadesAsignadasPendientes());
    assertEquals(Map.of("3", 10), n.getStockPorDeposito());
  }

  @Test
  void registrarDeNuevoLaNecesidadNoBorraElStockNiLosPendientes() {
    circuitoCompleto();
    procesar(depositoStock("2026-11-06T11:30:00Z", "3", "5", 25, "7"));
    procesar(necesidadRegistrada("2026-11-06T11:40:00Z", "n1", "4", 9, 200, "5"));

    NecesidadUrgenteDocument n = necesidad("n1");
    assertEquals(Map.of("3", 25), n.getStockPorDeposito());
    assertEquals(10, n.getUnidadesAsignadasPendientes());
  }

  @Test
  void unaNecesidadRegistradaAtrasadaCompletaLosDatosPeroNoPisaLoQueFalta() {
    procesar(
        necesidadSatisfecha("2026-11-06T12:00:00Z", "n1", 150, 50),
        necesidadRegistrada("2026-11-06T11:00:00Z", "n1", "4", 9, 200, "5"));

    NecesidadUrgenteDocument n = necesidad("n1");
    assertEquals(50, n.getCantidadFaltante());
    assertEquals(9, n.getUrgencia());
    assertEquals("5", n.getProductoSolicitadoId());
    assertEquals(200, n.getCantidadObjetivo());
  }

  @Test
  void unEventoDeLogisticaMasNuevoNoHaceDescartarUnoDeDonadoresSobreLaNecesidad() {
    circuitoCompleto();
    procesar(
        depositoStock("2026-11-06T18:00:00Z", "3", "5", 30, "7"),
        entregaCompletada("2026-11-06T18:00:01Z", "a1", "7", "5", 10, "n1", "2026-11-06T11:00:00Z"),
        necesidadSatisfecha("2026-11-06T12:00:00Z", "n1", 10, 190));

    assertEquals(190, necesidad("n1").getCantidadFaltante());
    assertEquals(0, necesidad("n1").getUnidadesAsignadasPendientes());
  }

  @Test
  void modificarLaNecesidadCambiaElObjetivoYLoQueFalta() {
    circuitoCompleto();
    procesar(necesidadModificada("2026-11-06T12:00:00Z", "n1", 120));

    assertEquals(120, necesidad("n1").getCantidadObjetivo());
    assertEquals(120, necesidad("n1").getCantidadFaltante());
  }

  @Test
  void elCambioDeRazonSocialLlegaALasNecesidadesYALaFichaDelDonador() {
    circuitoCompleto();
    procesar(
        entregaCompletada("2026-11-06T13:00:00Z", "a1", "7", "5", 10, "n1", "2026-11-06T11:00:00Z"),
        entidadRazonModificada("2026-11-06T14:00:00Z", "4", "Comedor Girasoles Asoc. Civil"));

    assertEquals("Comedor Girasoles Asoc. Civil", necesidad("n1").getEntidadRazonSocial());
    assertEquals("Comedor Girasoles Asoc. Civil", donador("12").getEntidadesBeneficiadas().get(0).getRazonSocial());
  }

  @Test
  void siDonadoresVaciaLasEntidadesSeBorranConSusNecesidadesPeroLaHistoriaQueda() {
    circuitoCompleto();
    procesar(
        entregaCompletada("2026-11-06T13:00:00Z", "a1", "7", "5", 10, "n1", "2026-11-06T11:00:00Z"),
        entidadesTodasEliminadas("2026-11-06T14:00:00Z"));

    // En Donadores el borrado total de entidades se lleva sus necesidades sin publicar NECESIDAD_ELIMINADA.
    assertTrue(entidades.findAll().isEmpty());
    assertTrue(necesidades.findAll().isEmpty());
    // Lo que ya se entregó sigue en la ficha del donador y en la asignación.
    assertEquals(List.of(new EntidadBeneficiadaResumen("4", "Comedor Los Girasoles", 10)),
        donador("12").getEntidadesBeneficiadas());
    assertEquals("Comedor Los Girasoles", asignaciones.findById("a1").orElseThrow().getEntidadRazonSocial());
  }

  // ---------------- Consulta 3 ----------------

  @Test
  void elDepositoCuentaAsignacionesTiempoDeEntregaYEntidadesAtendidas() {
    circuitoCompleto();
    procesar(entregaCompletada("2026-11-06T13:00:00.300Z", "a1", "7", "5", 10, "n1", "2026-11-06T11:00:00Z"));

    DepositoDocument d = depositos.findById("3").orElseThrow();
    assertEquals(1000, d.getCapacidadTotal());
    assertEquals(1.0, d.getOcupacionPorcentaje());
    assertEquals("SUB_ATENDIDOS", d.getAlgoritmoMatchmaking());
    assertEquals(1, d.getAsignacionesPorEstado().get("COMPLETADA"));
    assertEquals(0, d.getAsignacionesPorEstado().get("ASIGNADA"));
    assertEquals(180.0, d.getTiempoPromedioEntregaMinutos());
    assertEquals(1, d.getCantidadEntidadesDistintas());
  }

  @Test
  void laAsignacionQueLlegoAntesQueLaNecesidadQuedaConSuEntidad() {
    circuitoCompleto();

    AsignacionDocument a = asignaciones.findById("a1").orElseThrow();
    assertEquals("4", a.getEntidadId());
    assertEquals("Comedor Los Girasoles", a.getEntidadRazonSocial());
  }

  // ---------------- Consulta 4 (datos que usa el pipeline) ----------------

  @Test
  void hoyLaDonacionLlegaSinCategoriaYElPipelineLaInformaComoSinCategoria() {
    procesar(donacion("DONACION_CREADA", "2026-11-06T10:00:00Z", "7", "12", "3", "INGRESADA", "5", 10));

    var producto = donaciones.findById("7").orElseThrow().getProductos().get(0);
    assertEquals("5", producto.getProductoId());
    assertEquals(10, producto.getCantidad());
    assertNull(producto.getCategoriaId());
    assertNull(producto.getSubcategoriaId());
  }

  @Test
  void siDonacionesPublicaLaCategoriaSeGuardaParaElPipeline() {
    procesar(donacionConCategoria("DONACION_CREADA", "2026-11-06T10:00:00Z", "8", "12", "ACEPTADA", "1", "4"));

    var producto = donaciones.findById("8").orElseThrow().getProductos().get(0);
    assertEquals("1", producto.getCategoriaId());
    assertEquals("4", producto.getSubcategoriaId());
  }

  @Test
  void elJsonRealDeCadaModuloSeLeeConElConversorDelConsumidor() {
    EventoGenerico donacion = donacion("DONACION_CREADA", "2026-11-06T10:00:00.123456789Z", "7", "12", "3",
        "INGRESADA", "5", 10);
    assertEquals(Instant.parse("2026-11-06T10:00:00.123456789Z"), donacion.getTimestamp());
    assertEquals("12", donacion.getDonacion().get("donadorID"));
    assertEquals("SOSPECHOSO", donadorEstado("2026-11-06T10:00:00Z", "12", "SOSPECHOSO").getExtra().get("estado"));
    assertEquals(9, necesidadRegistrada("2026-11-06T10:00:00Z", "n1", "4", 9, 1, "5").getNecesidad().get("nivelDeUrgencia"));
    assertEquals("a1", asignacionCreada("2026-11-06T10:00:00Z", "a1", "7", "5", 1, "n1").getAsignacion().get("id"));
    assertNull(donadorActualizado("2026-11-06T10:00:00Z", "12", "COLABORADOR").getQueja());
  }
}

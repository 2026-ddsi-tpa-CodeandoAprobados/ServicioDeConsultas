package ar.edu.utn.dds.k3003.consultas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.dds.k3003.consultas.documents.DonacionDocument;
import ar.edu.utn.dds.k3003.consultas.documents.NecesidadUrgenteDocument;
import ar.edu.utn.dds.k3003.consultas.documents.ProductoDocument;
import ar.edu.utn.dds.k3003.consultas.documents.ProductoDonado;
import ar.edu.utn.dds.k3003.consultas.messaging.EventoGenerico;
import ar.edu.utn.dds.k3003.consultas.repositories.AsignacionDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DepositoDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonacionDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonadorDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.EventoProcesadoRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.NecesidadDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.ProductoDocumentRepository;
import java.time.Instant;
import java.util.ArrayList;
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

/** Eventos de producto de Donaciones y clasificación (categoría y subcategoría) de necesidades y donaciones. */
@ExtendWith(MockitoExtension.class)
class EventosDeProductosTest {

  @Mock private DonadorDocumentRepository donadorRepository;
  @Mock private NecesidadDocumentRepository necesidadRepository;
  @Mock private DepositoDocumentRepository depositoRepository;
  @Mock private AsignacionDocumentRepository asignacionRepository;
  @Mock private EventoProcesadoRepository eventosProcesados;
  @Mock private DonacionDocumentRepository donacionRepository;
  @Mock private ProductoDocumentRepository productoRepository;

  private EventProcessingService servicio;

  @BeforeEach
  void preparar() {
    servicio = new EventProcessingService(donadorRepository, necesidadRepository, depositoRepository);
    ReflectionTestUtils.setField(servicio, "asignacionRepository", asignacionRepository);
    ReflectionTestUtils.setField(servicio, "eventosProcesados", eventosProcesados);
    ReflectionTestUtils.setField(servicio, "donacionRepository", donacionRepository);
    ReflectionTestUtils.setField(servicio, "productoRepository", productoRepository);
  }

  private EventoGenerico productoEvento(String tipo, Instant timestamp) {
    EventoGenerico e = EventoGenerico.builder().id("p-" + tipo + timestamp).tipo(tipo).idReferencia("7").timestamp(timestamp).build();
    e.setProducto(Map.of("id", "7", "nombre", "Fideos Matarazzo", "subcategoriaID", "2", "subcategoria", "Fideos",
        "categoriaID", "1", "categoria", "Alimentos"));
    return e;
  }

  @Test
  void elProductoCreadoSeGuardaYClasificaLasNecesidadesDeEseProducto() {
    NecesidadUrgenteDocument necesidad = NecesidadUrgenteDocument.builder().id("nec-1").productoSolicitadoId("7").build();
    when(productoRepository.findById("7")).thenReturn(Optional.empty());
    when(necesidadRepository.findByProductoSolicitadoId("7")).thenReturn(List.of(necesidad));

    servicio.procesar(productoEvento("PRODUCTO_CREADO", Instant.parse("2026-11-06T14:00:00Z")));

    ArgumentCaptor<ProductoDocument> guardado = ArgumentCaptor.forClass(ProductoDocument.class);
    verify(productoRepository).save(guardado.capture());
    assertEquals("Alimentos", guardado.getValue().getCategoria());
    assertEquals("Fideos", guardado.getValue().getSubcategoria());
    assertEquals("Fideos Matarazzo", necesidad.getProductoNombre());
    assertEquals("Alimentos", necesidad.getCategoriaProducto());
    assertEquals("Fideos", necesidad.getSubcategoriaProducto());
    verify(necesidadRepository).save(necesidad);
  }

  @Test
  void elProductoClasificaLasDonacionesQueYaLoIncluian() {
    DonacionDocument donacion = DonacionDocument.builder().id("d1").donadorId("12").estado("CONQUEJA")
        .productos(new ArrayList<>(List.of(
            ProductoDonado.builder().productoId("7").cantidad(5).build(),
            ProductoDonado.builder().productoId("9").cantidad(1).build())))
        .build();
    when(productoRepository.findById("7")).thenReturn(Optional.empty());
    when(donacionRepository.findByProductosProductoId("7")).thenReturn(List.of(donacion));

    servicio.procesar(productoEvento("PRODUCTO_CREADO", Instant.parse("2026-11-06T14:00:00Z")));

    assertEquals("Alimentos", donacion.getProductos().get(0).getCategoriaId());
    assertEquals("Fideos", donacion.getProductos().get(0).getSubcategoriaId());
    assertEquals(null, donacion.getProductos().get(1).getCategoriaId(), "los otros productos no cambian");
    verify(donacionRepository).save(donacion);
  }

  @Test
  void unaNecesidadNuevaTomaLaClasificacionDeUnProductoYaConocido() {
    when(productoRepository.findById("7")).thenReturn(Optional.of(ProductoDocument.builder().id("7")
        .nombre("Fideos Matarazzo").categoria("Alimentos").subcategoria("Fideos").build()));
    EventoGenerico e = EventoGenerico.builder().id("n-1").tipo("NECESIDAD_REGISTRADA").idReferencia("nec-1")
        .timestamp(Instant.parse("2026-11-06T14:00:00Z")).build();
    e.setNecesidad(Map.of("id", "nec-1", "entidadID", "4", "nivelDeUrgencia", 8, "cantidadObjetivo", 30,
        "productoSolicitadoID", "7", "tipo", "EXTRAORDINARIA"));

    servicio.procesar(e);

    ArgumentCaptor<NecesidadUrgenteDocument> captor = ArgumentCaptor.forClass(NecesidadUrgenteDocument.class);
    verify(necesidadRepository).save(captor.capture());
    assertEquals("Alimentos", captor.getValue().getCategoriaProducto());
    assertEquals("Fideos", captor.getValue().getSubcategoriaProducto());
    assertEquals("Fideos Matarazzo", captor.getValue().getProductoNombre());
  }

  @Test
  void unProductoMasViejoQueLoGuardadoSeIgnora() {
    when(productoRepository.findById("7")).thenReturn(Optional.of(ProductoDocument.builder().id("7")
        .categoria("Almacen").ultimaActualizacion(Instant.parse("2026-11-06T15:00:00Z")).build()));

    servicio.procesar(productoEvento("PRODUCTO_MODIFICADO", Instant.parse("2026-11-06T14:00:00Z")));

    verify(productoRepository, never()).save(any());
  }

  @Test
  void elProductoEliminadoQuedaMarcadoEnLugarDeBorrarse() {
    EventoGenerico e = EventoGenerico.builder().id("p-del").tipo("PRODUCTO_ELIMINADO").idReferencia("7")
        .timestamp(Instant.now()).build();

    servicio.procesar(e);

    ArgumentCaptor<ProductoDocument> guardado = ArgumentCaptor.forClass(ProductoDocument.class);
    verify(productoRepository).save(guardado.capture());
    assertEquals(true, guardado.getValue().getEliminado());
    verify(productoRepository, never()).deleteById(any());
  }

  @Test
  void laDonacionGuardaElNombreDeLaCategoriaYLaSubcategoriaDelDetalle() {
    EventoGenerico e = EventoGenerico.builder().id("d-1").tipo("DONACION_CREADA").idReferencia("d1")
        .timestamp(Instant.parse("2026-11-06T14:00:00Z")).build();
    e.setDonacion(Map.of("id", "d1", "donadorID", "12", "depositoID", "3", "estado", "INGRESADA",
        "detallesProductosDTO", List.of(Map.of("productoID", "7", "cantidadProducto", 5, "categoriaID", "1",
            "categoria", "Alimentos", "subcategoriaID", "2", "subcategoria", "Fideos"))));

    servicio.procesar(e);

    ArgumentCaptor<DonacionDocument> donacion = ArgumentCaptor.forClass(DonacionDocument.class);
    verify(donacionRepository).save(donacion.capture());
    assertEquals("Alimentos", donacion.getValue().getProductos().get(0).getCategoriaId());
    assertEquals("Fideos", donacion.getValue().getProductos().get(0).getSubcategoriaId());
  }

  @Test
  void siElDetalleNoTraeClasificacionSeUsaLaDelProductoConocido() {
    when(productoRepository.findById("7")).thenReturn(Optional.of(ProductoDocument.builder().id("7")
        .categoria("Alimentos").subcategoria("Fideos").build()));
    EventoGenerico e = EventoGenerico.builder().id("d-2").tipo("DONACION_CREADA").idReferencia("d2")
        .timestamp(Instant.parse("2026-11-06T14:00:00Z")).build();
    e.setDonacion(Map.of("id", "d2", "donadorID", "12", "depositoID", "3", "estado", "ACEPTADA",
        "detallesProductosDTO", List.of(Map.of("productoID", "7", "cantidadProducto", 5))));

    servicio.procesar(e);

    ArgumentCaptor<DonacionDocument> donacion = ArgumentCaptor.forClass(DonacionDocument.class);
    verify(donacionRepository).save(donacion.capture());
    assertEquals("Alimentos", donacion.getValue().getProductos().get(0).getCategoriaId());
    assertEquals("Fideos", donacion.getValue().getProductos().get(0).getSubcategoriaId());
  }
}

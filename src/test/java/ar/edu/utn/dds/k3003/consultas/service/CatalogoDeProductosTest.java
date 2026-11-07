package ar.edu.utn.dds.k3003.consultas.service;

import static ar.edu.utn.dds.k3003.consultas.support.EventosReales.necesidadRegistrada;
import static ar.edu.utn.dds.k3003.consultas.support.EventosReales.producto;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.dds.k3003.consultas.documents.NecesidadUrgenteDocument;
import ar.edu.utn.dds.k3003.consultas.documents.ProductoDocument;
import ar.edu.utn.dds.k3003.consultas.repositories.DepositoDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonadorDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.EventoProcesadoRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.NecesidadDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.ProductoDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.support.MongoEnMemoria;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Catálogo de productos de Donaciones con el JSON que publica hoy {@code DonacionesEventPublisher}: alta,
 * modificación y baja lógica ordenadas por timestamp, y la clasificación (por nombre) de las necesidades.
 */
class CatalogoDeProductosTest {

  private static final String ALTA = "2026-11-06T14:00:00Z";
  private static final String MODIFICACION = "2026-11-06T15:00:00Z";
  private static final String BAJA = "2026-11-06T16:00:00Z";

  private NecesidadDocumentRepository necesidades;
  private ProductoDocumentRepository productos;
  private EventProcessingService servicio;

  @BeforeEach
  void preparar() {
    necesidades = MongoEnMemoria.repositorio(NecesidadDocumentRepository.class);
    productos = MongoEnMemoria.repositorio(ProductoDocumentRepository.class);
    servicio = new EventProcessingService(MongoEnMemoria.repositorio(DonadorDocumentRepository.class), necesidades,
        MongoEnMemoria.repositorio(DepositoDocumentRepository.class));
    ReflectionTestUtils.setField(servicio, "eventosProcesados", MongoEnMemoria.repositorio(EventoProcesadoRepository.class));
    ReflectionTestUtils.setField(servicio, "productoRepository", productos);
  }

  private void fideos(String tipo, String timestamp, String categoria, String subcategoria) {
    servicio.procesar(producto(tipo, timestamp, "7", "Fideos Matarazzo", "1", categoria, "2", subcategoria));
  }

  private NecesidadUrgenteDocument necesidad() {
    return necesidades.findById("nec-1").orElseThrow();
  }

  @Test
  void elAltaGuardaTodosLosCamposDelProducto() {
    fideos("PRODUCTO_CREADO", ALTA, "Alimentos", "Fideos");

    ProductoDocument p = productos.findById("7").orElseThrow();
    assertEquals("Fideos Matarazzo", p.getNombre());
    assertEquals("Paquete de 500g", p.getDescripcion());
    assertEquals("1", p.getCategoriaId());
    assertEquals("Alimentos", p.getCategoria());
    assertEquals("2", p.getSubcategoriaId());
    assertEquals("Fideos", p.getSubcategoria());
    assertFalse(p.getEliminado());
  }

  @Test
  void laNecesidadQueLlegaDespuesDelProductoTomaSuNombreYSuClasificacion() {
    fideos("PRODUCTO_CREADO", ALTA, "Alimentos", "Fideos");
    servicio.procesar(necesidadRegistrada(MODIFICACION, "nec-1", "4", 9, 200, "7"));

    assertEquals("Fideos Matarazzo", necesidad().getProductoNombre());
    assertEquals("Alimentos", necesidad().getCategoriaProducto());
    assertEquals("Fideos", necesidad().getSubcategoriaProducto());
  }

  @Test
  void elProductoQueLlegaDespuesClasificaLaNecesidadYaGuardada() {
    servicio.procesar(necesidadRegistrada(ALTA, "nec-1", "4", 9, 200, "7"));
    assertNull(necesidad().getCategoriaProducto());

    fideos("PRODUCTO_CREADO", MODIFICACION, "Alimentos", "Fideos");

    assertEquals("Fideos Matarazzo", necesidad().getProductoNombre());
    assertEquals("Alimentos", necesidad().getCategoriaProducto());
    assertEquals("Fideos", necesidad().getSubcategoriaProducto());
  }

  @Test
  void laModificacionDelProductoReclasificaLaNecesidad() {
    servicio.procesar(necesidadRegistrada(ALTA, "nec-1", "4", 9, 200, "7"));
    fideos("PRODUCTO_CREADO", ALTA, "Alimentos", "Fideos");

    fideos("PRODUCTO_MODIFICADO", MODIFICACION, "Almacén", "Pastas");

    assertEquals("Almacén", necesidad().getCategoriaProducto());
    assertEquals("Pastas", necesidad().getSubcategoriaProducto());
  }

  @Test
  void laBajaEsLogicaYLaNecesidadConservaSuClasificacion() {
    servicio.procesar(necesidadRegistrada(ALTA, "nec-1", "4", 9, 200, "7"));
    fideos("PRODUCTO_CREADO", ALTA, "Alimentos", "Fideos");

    fideos("PRODUCTO_ELIMINADO", BAJA, "Alimentos", "Fideos");

    ProductoDocument p = productos.findById("7").orElseThrow();
    assertTrue(p.getEliminado());
    assertEquals("Alimentos", p.getCategoria());
    assertEquals("Alimentos", necesidad().getCategoriaProducto());
  }

  @Test
  void unAltaAtrasadaNoResucitaUnProductoEliminado() {
    fideos("PRODUCTO_ELIMINADO", BAJA, "Alimentos", "Fideos");

    fideos("PRODUCTO_CREADO", ALTA, "Otra", "Otra");

    ProductoDocument p = productos.findById("7").orElseThrow();
    assertTrue(p.getEliminado());
    assertEquals("Alimentos", p.getCategoria());
  }

  @Test
  void unaBajaMasViejaQueLaUltimaModificacionSeIgnora() {
    fideos("PRODUCTO_MODIFICADO", MODIFICACION, "Alimentos", "Fideos");

    fideos("PRODUCTO_ELIMINADO", ALTA, "Alimentos", "Fideos");

    assertFalse(productos.findById("7").orElseThrow().getEliminado());
  }

  @Test
  void unaModificacionAtrasadaNoPisaLaMasNueva() {
    fideos("PRODUCTO_MODIFICADO", MODIFICACION, "Almacén", "Pastas");

    fideos("PRODUCTO_CREADO", ALTA, "Alimentos", "Fideos");

    assertEquals("Almacén", productos.findById("7").orElseThrow().getCategoria());
  }

  @Test
  void sinNombreDeCategoriaLaNecesidadNoGuardaElId() {
    servicio.procesar(necesidadRegistrada(ALTA, "nec-1", "4", 9, 200, "7"));

    servicio.procesar(producto("PRODUCTO_CREADO", MODIFICACION, "7", "Fideos Matarazzo", "1", null, "2", null));

    assertEquals("Fideos Matarazzo", necesidad().getProductoNombre());
    assertNull(necesidad().getCategoriaProducto(), "C2 guarda el nombre, nunca el id");
    assertNull(necesidad().getSubcategoriaProducto());
  }

  @Test
  void losFiltrosDeLaConsulta2BuscanPorElNombreGuardadoEnLaNecesidad() {
    MongoTemplate mongo = mock(MongoTemplate.class);
    when(mongo.find(any(Query.class), eq(NecesidadUrgenteDocument.class))).thenReturn(List.of());
    ConsultaService consultas = new ConsultaService(MongoEnMemoria.repositorio(DonadorDocumentRepository.class),
        necesidades, MongoEnMemoria.repositorio(DepositoDocumentRepository.class), mongo);

    consultas.obtenerNecesidadesUrgentes(null, null, "Alimentos", "Fideos");

    ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
    verify(mongo).find(query.capture(), eq(NecesidadUrgenteDocument.class));
    String filtro = query.getValue().getQueryObject().toJson();
    assertTrue(filtro.contains("\"categoriaProducto\": \"Alimentos\""), filtro);
    assertTrue(filtro.contains("\"subcategoriaProducto\": \"Fideos\""), filtro);
  }
}

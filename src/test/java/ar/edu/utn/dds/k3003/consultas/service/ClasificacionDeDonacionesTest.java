package ar.edu.utn.dds.k3003.consultas.service;

import static ar.edu.utn.dds.k3003.consultas.support.EventosReales.donacionClasificada;
import static ar.edu.utn.dds.k3003.consultas.support.EventosReales.producto;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import ar.edu.utn.dds.k3003.consultas.documents.ProductoDonado;
import ar.edu.utn.dds.k3003.consultas.repositories.DepositoDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonacionDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonadorDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.EventoProcesadoRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.NecesidadDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.ProductoDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.support.MongoEnMemoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Consulta 4 por nombre: cada producto de una donación guarda por separado los ids y los NOMBRES de su categoría
 * y subcategoría (el pipeline agrupa por los nombres). JSON tal como lo publica hoy Donaciones.
 */
class ClasificacionDeDonacionesTest {

  private static final String T1 = "2026-11-06T14:00:00Z";
  private static final String T2 = "2026-11-06T15:00:00Z";

  private DonacionDocumentRepository donaciones;
  private EventProcessingService servicio;

  @BeforeEach
  void preparar() {
    donaciones = MongoEnMemoria.repositorio(DonacionDocumentRepository.class);
    servicio = new EventProcessingService(MongoEnMemoria.repositorio(DonadorDocumentRepository.class),
        MongoEnMemoria.repositorio(NecesidadDocumentRepository.class),
        MongoEnMemoria.repositorio(DepositoDocumentRepository.class));
    ReflectionTestUtils.setField(servicio, "eventosProcesados", MongoEnMemoria.repositorio(EventoProcesadoRepository.class));
    ReflectionTestUtils.setField(servicio, "donacionRepository", donaciones);
    ReflectionTestUtils.setField(servicio, "productoRepository", MongoEnMemoria.repositorio(ProductoDocumentRepository.class));
  }

  private ProductoDonado productoDe(String donacionId) {
    return donaciones.findById(donacionId).orElseThrow().getProductos().get(0);
  }

  @Test
  void laDonacionGuardaLosIdsYLosNombresPorSeparado() {
    servicio.procesar(donacionClasificada("DONACION_CREADA", T1, "d1", "ACEPTADA", "7", "3", "Alimentos", "7", "Pastas"));

    ProductoDonado p = productoDe("d1");
    assertEquals("3", p.getCategoriaId());
    assertEquals("Alimentos", p.getCategoria());
    assertEquals("7", p.getSubcategoriaId());
    assertEquals("Pastas", p.getSubcategoria());
  }

  @Test
  void conSoloLosIdsNoHayNombreYElPipelineLaInformaComoSinCategoria() {
    servicio.procesar(donacionClasificada("DONACION_CREADA", T1, "d1", "ACEPTADA", "7", "3", null, "7", null));

    ProductoDonado p = productoDe("d1");
    assertEquals("3", p.getCategoriaId());
    assertNull(p.getCategoria(), "el id nunca se usa como nombre");
    assertNull(p.getSubcategoria());
  }

  @Test
  void sinNombresSeCompletanDesdeElProductoConocido() {
    servicio.procesar(producto("PRODUCTO_CREADO", T1, "7", "Fideos", "3", "Alimentos", "7", "Pastas"));

    servicio.procesar(donacionClasificada("DONACION_CREADA", T2, "d1", "ACEPTADA", "7", null, null, null, null));

    ProductoDonado p = productoDe("d1");
    assertEquals("Alimentos", p.getCategoria());
    assertEquals("Pastas", p.getSubcategoria());
    assertEquals("3", p.getCategoriaId());
    assertEquals("7", p.getSubcategoriaId());
  }

  @Test
  void loQueTraeElDetalleNoLoPisaElProductoConocido() {
    servicio.procesar(producto("PRODUCTO_CREADO", T1, "7", "Fideos", "3", "Alimentos", "7", "Pastas"));

    servicio.procesar(donacionClasificada("DONACION_CREADA", T2, "d1", "ACEPTADA", "7", "9", "Almacén", "8", "Secos"));

    assertEquals("Almacén", productoDe("d1").getCategoria());
    assertEquals("Secos", productoDe("d1").getSubcategoria());
  }

  @Test
  void conElCambioDeEstadoLaDonacionViejaSeReclasificaConLosNombres() {
    servicio.procesar(donacionClasificada("DONACION_CREADA", T1, "d1", "ACEPTADA", "7", "3", null, "7", null));

    servicio.procesar(donacionClasificada("DONACION_ESTADO_MODIFICADO", T2, "d1", "CONQUEJA", "7", "3",
        "Alimentos", "7", "Pastas"));

    assertEquals("Alimentos", productoDe("d1").getCategoria());
    assertEquals("CONQUEJA", donaciones.findById("d1").orElseThrow().getEstado());
  }
}

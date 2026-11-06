package ar.edu.utn.dds.k3003.consultas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ar.edu.utn.dds.k3003.consultas.documents.CalidadDonacionDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DonacionDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DonadorDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DonadorQuejasResumen;
import ar.edu.utn.dds.k3003.consultas.documents.ProductoDonado;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Los pipelines de la consulta 4 contra un MongoDB real. Necesita Docker: sin Docker, JUnit los saltea.
 */
@Testcontainers(disabledWithoutDocker = true)
class Consulta4MongoTest {

  @Container
  private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

  private static MongoClient cliente;
  private static MongoTemplate mongo;
  private ConsultaService servicio;

  @BeforeAll
  static void conectar() {
    cliente = MongoClients.create(MONGO.getReplicaSetUrl());
    mongo = new MongoTemplate(cliente, "consultas_test");
  }

  @AfterAll
  static void desconectar() {
    cliente.close();
  }

  @BeforeEach
  void limpiar() {
    mongo.getDb().drop();
    servicio = new ConsultaService(null, null, null, mongo);
  }

  private static ProductoDonado producto(String categoria, String subcategoria) {
    return ProductoDonado.builder().productoId("p").cantidad(1).categoriaId(categoria).subcategoriaId(subcategoria).build();
  }

  private static DonacionDocument donacion(String id, String estado, ProductoDonado... productos) {
    return DonacionDocument.builder().id(id).donadorId("12").estado(estado)
        .productos(new ArrayList<>(List.of(productos))).ultimaActualizacion(Instant.parse("2026-11-06T10:00:00Z"))
        .build();
  }

  private void cargarDonaciones() {
    mongo.insertAll(List.of(
        // Dos productos de la misma subcategoría: la donación cuenta una vez.
        donacion("d1", "CONQUEJA", producto("Alimentos", "Pastas"), producto("Alimentos", "Pastas")),
        donacion("d2", "ACEPTADA", producto("Alimentos", "Pastas")),
        donacion("d3", "ACEPTADA", producto("Higiene", "Jabon")),
        // Sin productos y con un producto sin categoría (como publica hoy Donaciones).
        donacion("d4", "INGRESADA"),
        donacion("d5", "CONQUEJA", producto(null, null)),
        donacion("d6", "ACEPTADA", producto(null, null))));
  }

  @Test
  void laCalidadSaleOrdenadaPorProporcionDeQuejas() {
    cargarDonaciones();

    List<CalidadDonacionDocument> filas = servicio.obtenerCalidad(null, null);

    assertEquals(List.of("Alimentos_Pastas", "SIN_CATEGORIA_general", "Higiene_Jabon"),
        filas.stream().map(CalidadDonacionDocument::getId).toList());
    CalidadDonacionDocument pastas = filas.get(0);
    assertEquals("Alimentos", pastas.getCategoriaProducto());
    assertEquals("Pastas", pastas.getSubcategoriaProducto());
    assertEquals(2, pastas.getTotalDonaciones());
    assertEquals(1, pastas.getDonacionesConQueja());
    assertEquals(0.5, pastas.getProporcionQuejas());
    assertEquals(Instant.parse("2026-11-06T10:00:00Z"), pastas.getUltimaActualizacion());
    CalidadDonacionDocument sinCategoria = filas.get(1);
    assertEquals(3, sinCategoria.getTotalDonaciones());
    assertEquals(1, sinCategoria.getDonacionesConQueja());
  }

  @Test
  void laCalidadSeFiltraPorCategoriaPorSubcategoriaOPorAmbas() {
    cargarDonaciones();

    assertEquals(List.of("Alimentos_Pastas"), ids(servicio.obtenerCalidad("Alimentos", null)));
    assertEquals(List.of("Higiene_Jabon"), ids(servicio.obtenerCalidad(null, "Jabon")));
    assertEquals(List.of(), ids(servicio.obtenerCalidad("Alimentos", "Jabon")));
    assertEquals(List.of("SIN_CATEGORIA_general"), ids(servicio.obtenerCalidad("SIN_CATEGORIA", null)));
  }

  private static List<String> ids(List<CalidadDonacionDocument> filas) {
    return filas.stream().map(CalidadDonacionDocument::getId).toList();
  }

  @Test
  void elRankingDevuelveLosDiezConMasQuejasConSoloLosCamposDeLaConsulta() {
    for (int i = 0; i <= 11; i++) {
      mongo.insert(DonadorDocument.builder().id("d" + i).nombre("Donador " + i).apellido("Apellido")
          .estado(i >= 5 ? "SOSPECHOSO" : "VERIFICADO").categoria("OCASIONAL").quejasRecibidas(i).build());
    }

    List<DonadorQuejasResumen> ranking = servicio.obtenerDonadoresConMasQuejas(null);

    assertEquals(10, ranking.size());
    assertEquals(new DonadorQuejasResumen("d11", "Donador 11", "SOSPECHOSO", "OCASIONAL", 11), ranking.get(0));
    assertEquals(2, ranking.get(9).quejasRecibidas());
    assertEquals(3, servicio.obtenerDonadoresConMasQuejas(3).size());
  }
}

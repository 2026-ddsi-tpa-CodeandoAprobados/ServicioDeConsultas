package ar.edu.utn.dds.k3003.consultas.controllers;

import ar.edu.utn.dds.k3003.consultas.documents.*;
import ar.edu.utn.dds.k3003.consultas.repositories.DepositoDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonacionDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonadorDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.NecesidadDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.service.ConsultaService;
import java.time.Instant;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Operaciones de mantenimiento para la demo: borrar el modelo de lectura y cargar datos de ejemplo. No tienen
 * autenticación, así que están apagadas salvo que se defina {@code ADMIN_ENABLED=true}; apagadas responden
 * 403 sin tocar la base.
 */
@Slf4j
@RestController
@RequestMapping("/consultas/admin")
public class AdminController {

  private static final String DESHABILITADO = "Operación de administración deshabilitada";

  private final ConsultaService consultaService;
  private final DonadorDocumentRepository donadorRepository;
  private final NecesidadDocumentRepository necesidadRepository;
  private final DepositoDocumentRepository depositoRepository;
  private final DonacionDocumentRepository donacionRepository;
  private final boolean habilitado;

  public AdminController(
      ConsultaService consultaService,
      DonadorDocumentRepository donadorRepository,
      NecesidadDocumentRepository necesidadRepository,
      DepositoDocumentRepository depositoRepository,
      DonacionDocumentRepository donacionRepository,
      @Value("${app.admin.enabled:false}") boolean habilitado) {
    this.consultaService = consultaService;
    this.donadorRepository = donadorRepository;
    this.necesidadRepository = necesidadRepository;
    this.depositoRepository = depositoRepository;
    this.donacionRepository = donacionRepository;
    this.habilitado = habilitado;
  }

  @DeleteMapping("/reset")
  public ResponseEntity<Map<String, String>> resetDatabase() {
    if (!habilitado) {
      log.warn("DELETE /consultas/admin/reset rechazado: ADMIN_ENABLED no está activo");
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", DESHABILITADO));
    }
    consultaService.borrarTodo();
    return ResponseEntity.ok(Map.of("mensaje", "Base de datos MongoDB reiniciada con éxito"));
  }

  /**
   * Carga datos iniciales para la presentación / demo según los ejemplos de la consigna.
   */
  @PostMapping("/seed-demo")
  public ResponseEntity<Map<String, Object>> seedDemoData() {
    if (!habilitado) {
      log.warn("POST /consultas/admin/seed-demo rechazado: ADMIN_ENABLED no está activo");
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", DESHABILITADO));
    }
    log.info("Cargando datos de demostración en MongoDB...");

    // 1. Donador de ejemplo del documento (Página 5)
    DonadorDocument lucia =
        DonadorDocument.builder()
            .id("12")
            .nombre("Lucía Fernández")
            .estado("VERIFICADO")
            .categoria("COLABORADOR")
            .historialCategorias(
                List.of(
                    HistorialCategoria.builder().categoria("OCASIONAL").desde("2026-03-02").build(),
                    HistorialCategoria.builder().categoria("COLABORADOR").desde("2026-06-18").build()))
            .insignias(List.of("Completitud", "Solidaridad"))
            .misionEnCurso(
                MisionEnCurso.builder()
                    .nombre("Donaciones Exitosas")
                    .progreso(14)
                    .objetivo(20)
                    .build())
            .donacionesPorEstado(Map.of("INGRESADA", 2, "ACEPTADA", 14, "CONQUEJA", 3, "ENTREGADA", 10))
            .quejasRecibidas(3)
            .entidadesBeneficiadas(
                List.of(
                    EntidadBeneficiadaResumen.builder()
                        .entidadId("4")
                        .razonSocial("Comedor Los Girasoles")
                        .unidades(120)
                        .build()))
            .ultimaActualizacion(Instant.parse("2026-10-20T14:32:10Z"))
            .build();

    DonadorDocument carlos =
        DonadorDocument.builder()
            .id("15")
            .nombre("Carlos Gómez")
            .estado("SOSPECHOSO")
            .categoria("OCASIONAL")
            .quejasRecibidas(6)
            .ultimaActualizacion(Instant.now())
            .build();

    donadorRepository.saveAll(List.of(lucia, carlos));

    // 2. Necesidades Urgentes (Consulta 2)
    NecesidadUrgenteDocument nec1 =
        NecesidadUrgenteDocument.builder()
            .id("nec-fideos-1")
            .entidadId("4")
            .entidadRazonSocial("Comedor Los Girasoles")
            .urgencia(9)
            .tipoNecesidad("EXTRAORDINARIA")
            .productoSolicitadoId("prod-fideos")
            .productoNombre("Fideos secos 500g")
            .categoriaProducto("Alimentos")
            .subcategoriaProducto("Pastas")
            .cantidadObjetivo(200)
            .cantidadFaltante(80)
            .unidadesAsignadasPendientes(20)
            .stockPorDeposito(Map.of("Depósito Medrano", 50, "Depósito Campus", 30))
            .satisfecha(false)
            .ultimaActualizacion(Instant.now())
            .build();

    necesidadRepository.save(nec1);

    // 3. Depósitos (Consulta 3)
    DepositoDocument dep1 =
        DepositoDocument.builder()
            .id("dep-1")
            .nombre("Depósito Central Medrano")
            .capacidadTotal(1000)
            .stockActual(750)
            .ocupacionPorcentaje(75.0)
            .algoritmoMatchmaking("FIFO")
            .asignacionesPorEstado(Map.of("PENDIENTE", 3, "EN_VIAJE", 5, "ENTREGADA", 42))
            .tiempoPromedioEntregaMinutos(145.5)
            .entidadesDistintasAtendidas(Set.of("entidad-1", "entidad-4", "entidad-8"))
            .cantidadEntidadesDistintas(3)
            .ultimaActualizacion(Instant.now())
            .build();

    depositoRepository.save(dep1);

    // 4. Calidad de lo donado (Consulta 4): sale del pipeline sobre las donaciones. 25 donaciones de
    // Alimentos/Pastas, 3 con queja (proporción 0.12).
    List<DonacionDocument> donaciones = new ArrayList<>();
    for (int i = 1; i <= 25; i++) {
      donaciones.add(
          DonacionDocument.builder()
              .id("demo-don-" + i)
              .donadorId(i <= 3 ? "15" : "12")
              .depositoId("dep-1")
              .descripcion("Fideos secos 500g")
              .estado(i <= 3 ? "CONQUEJA" : "ACEPTADA")
              .productos(
                  new ArrayList<>(List.of(
                      ProductoDonado.builder()
                          .productoId("prod-fideos")
                          .cantidad(10)
                          .categoriaId("Alimentos")
                          .subcategoriaId("Pastas")
                          .build())))
              .ultimaActualizacion(Instant.now())
              .build());
    }
    donacionRepository.saveAll(donaciones);

    return ResponseEntity.ok(
        Map.of(
            "status", "OK",
            "mensaje", "Datos de demostración precargados exitosamente",
            "donadoresCreados", 2,
            "necesidadesCreadas", 1,
            "depositosCreados", 1,
            "donacionesCreadas", donaciones.size()));
  }
}

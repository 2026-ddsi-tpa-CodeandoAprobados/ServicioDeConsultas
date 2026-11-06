package ar.edu.utn.dds.k3003.consultas.controllers;

import ar.edu.utn.dds.k3003.consultas.documents.CalidadDonacionDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DepositoDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DonadorDocument;
import ar.edu.utn.dds.k3003.consultas.documents.NecesidadUrgenteDocument;
import ar.edu.utn.dds.k3003.consultas.service.ConsultaService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/consultas")
public class ConsultaController {

  private final ConsultaService consultaService;

  public ConsultaController(ConsultaService consultaService) {
    this.consultaService = consultaService;
  }

  /**
   * Consulta 1: Ficha integral de un donador
   * "Dado un donador, devolver su estado, su categoría actual y el historial de categorías,
   * sus insignias, la misión en curso con su progreso, la cantidad de donaciones por estado,
   * las quejas recibidas y las entidades que recibieron sus donaciones con las unidades."
   */
  @GetMapping("/donador/{donadorId}")
  public ResponseEntity<DonadorDocument> getFichaDonador(@PathVariable String donadorId) {
    return consultaService
        .obtenerFichaIntegralDonador(donadorId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/donadores")
  public ResponseEntity<List<DonadorDocument>> getAllDonadores() {
    return ResponseEntity.ok(consultaService.obtenerTodosLosDonadores());
  }

  /**
   * Consulta 2: Necesidades urgentes sin cubrir
   * "Listar las necesidades insatisfechas ordenadas por urgencia, con filtros por urgencia mínima,
   * tipo de necesidad y categoría o subcategoría del producto."
   */
  @GetMapping("/necesidades-urgentes")
  public ResponseEntity<List<NecesidadUrgenteDocument>> getNecesidadesUrgentes(
      @RequestParam(required = false) Integer urgenciaMinima,
      @RequestParam(required = false) String tipoNecesidad,
      @RequestParam(required = false) String categoriaProducto,
      @RequestParam(required = false) String subcategoriaProducto) {
    return ResponseEntity.ok(
        consultaService.obtenerNecesidadesUrgentes(
            urgenciaMinima, tipoNecesidad, categoriaProducto, subcategoriaProducto));
  }

  /**
   * Consulta 3: Estado de los depósitos
   * "Para cada depósito (o uno en particular), informar la ocupación (stock sobre capacidad),
   * el algoritmo de matchmaking configurado, la cantidad de asignaciones por estado,
   * el tiempo promedio entre el ingreso de una donación y su entrega, y la cantidad de entidades distintas atendidas."
   */
  @GetMapping("/depositos")
  public ResponseEntity<List<DepositoDocument>> getEstadoDepositos() {
    return ResponseEntity.ok(consultaService.obtenerEstadoTodosDepositos());
  }

  @GetMapping("/depositos/{depositoId}")
  public ResponseEntity<DepositoDocument> getEstadoDeposito(@PathVariable String depositoId) {
    return consultaService
        .obtenerEstadoDeposito(depositoId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * Consulta 4 (Parte 1): Calidad de lo donado
   * "Por categoría y subcategoría de producto, calcular la proporción de donaciones que terminaron con queja."
   */
  @GetMapping("/calidad-donaciones")
  public ResponseEntity<List<CalidadDonacionDocument>> getCalidadDonaciones(
      @RequestParam(required = false) String categoria) {
    return ResponseEntity.ok(consultaService.obtenerCalidadPorCategoria(categoria));
  }

  /**
   * Consulta 4 (Parte 2): Donadores con más quejas resuelto con PIPELINE DE AGREGACIÓN de MongoDB.
   * "listar los donadores con más quejas junto con su estado y su categoría."
   */
  @GetMapping("/donadores-con-mas-quejas")
  public ResponseEntity<List<DonadorDocument>> getDonadoresConMasQuejas() {
    return ResponseEntity.ok(consultaService.obtenerDonadoresConMasQuejasPipeline());
  }
}

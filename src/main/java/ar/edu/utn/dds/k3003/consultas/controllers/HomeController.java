package ar.edu.utn.dds.k3003.consultas.controllers;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

  @GetMapping("/")
  public ResponseEntity<Map<String, Object>> root() {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", "UP");
    response.put("servicio", "ServicioDeConsultas - DonaTrack (Entrega 6)");
    response.put("descripcion", "Microservicio de lectura CQRS y consumidor de eventos RabbitMQ persistiendo en MongoDB");
    response.put("consultas", Map.of(
        "consulta_1_donador", "/consultas/donador/{id}",
        "consulta_2_necesidades", "/consultas/necesidades-urgentes",
        "consulta_3_depositos", "/consultas/depositos",
        "consulta_4_calidad", "/consultas/calidad-donaciones",
        "consulta_4_pipeline_quejas", "/consultas/donadores-con-mas-quejas"
    ));
    response.put("admin", Map.of(
        "seed_demo", "POST /consultas/admin/seed-demo",
        "reset", "DELETE /consultas/admin/reset"
    ));
    return ResponseEntity.ok(response);
  }
}

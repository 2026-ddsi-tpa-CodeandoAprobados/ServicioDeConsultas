package ar.edu.utn.dds.k3003.consultas.documents;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "depositos")
public class DepositoDocument implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id private String id;
  private String nombre;
  private Integer capacidadTotal;
  private Integer stockActual;
  private Double ocupacionPorcentaje;
  private String algoritmoMatchmaking;

  @Builder.Default private Map<String, Integer> asignacionesPorEstado = new HashMap<>();

  /**
   * Unidades de cada producto que hay en el depósito (productoId a cantidad), según el último stock que
   * publicó Logística. También sirve para completar el stock por depósito de una necesidad que se registra
   * después del evento de stock.
   */
  @Builder.Default private Map<String, Integer> stockPorProducto = new HashMap<>();

  private Double tiempoPromedioEntregaMinutos;

  @Builder.Default private Set<String> entidadesDistintasAtendidas = new HashSet<>();

  private Integer cantidadEntidadesDistintas;

  private Instant ultimaActualizacion;

  public Integer getCantidadEntidadesDistintas() {
    return entidadesDistintasAtendidas != null ? entidadesDistintasAtendidas.size() : 0;
  }
}

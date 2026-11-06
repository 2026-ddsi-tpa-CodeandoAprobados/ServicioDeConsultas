package ar.edu.utn.dds.k3003.consultas.documents;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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
@Document(collection = "necesidades")
public class NecesidadUrgenteDocument implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id private String id;
  private String entidadId;
  private String entidadRazonSocial;
  private Integer urgencia;
  private String tipoNecesidad;
  private String productoSolicitadoId;
  private String productoNombre;
  private String categoriaProducto;
  private String subcategoriaProducto;
  private Integer cantidadObjetivo;
  private Integer cantidadFaltante;

  @Builder.Default private Integer unidadesAsignadasPendientes = 0;

  @Builder.Default private Map<String, Integer> stockPorDeposito = new HashMap<>();

  @Builder.Default private Boolean satisfecha = false;

  private Instant ultimaActualizacion;
}

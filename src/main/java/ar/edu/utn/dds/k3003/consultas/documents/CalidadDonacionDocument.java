package ar.edu.utn.dds.k3003.consultas.documents;

import java.io.Serializable;
import java.time.Instant;
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
@Document(collection = "calidad_donaciones")
public class CalidadDonacionDocument implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id private String id; // categoria_subcategoria
  private String categoriaProducto;
  private String subcategoriaProducto;
  private Integer totalDonaciones;
  private Integer donacionesConQueja;
  private Double proporcionQuejas;
  private Instant ultimaActualizacion;

  public void recalcularProporcion() {
    if (totalDonaciones != null && totalDonaciones > 0 && donacionesConQueja != null) {
      this.proporcionQuejas = (double) donacionesConQueja / totalDonaciones;
    } else {
      this.proporcionQuejas = 0.0;
    }
  }
}

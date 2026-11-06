package ar.edu.utn.dds.k3003.consultas.documents;

import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Una fila de la consulta 4 (calidad de lo donado). Ya no es una colección: la arma el pipeline de
 * agregación sobre {@code donaciones} en cada consulta (ver {@code ConsultaService#obtenerCalidad}). Se
 * conserva el nombre y los campos para que la respuesta del endpoint no cambie.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalidadDonacionDocument implements Serializable {

  private static final long serialVersionUID = 1L;

  /** categoria_subcategoria. */
  private String id;

  private String categoriaProducto;
  private String subcategoriaProducto;
  private Integer totalDonaciones;
  private Integer donacionesConQueja;
  private Double proporcionQuejas;

  /** La última actualización de las donaciones del grupo. */
  private Instant ultimaActualizacion;
}

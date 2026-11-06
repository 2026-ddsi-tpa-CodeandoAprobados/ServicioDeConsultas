package ar.edu.utn.dds.k3003.consultas.documents;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Una donación tal como la publica Donaciones. De acá se calculan, sin llamar a ningún módulo, los
 * conteos por estado de cada donador, la calidad por categoría y las estadísticas de cada depósito.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "donaciones")
public class DonacionDocument implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id private String id;
  private String donadorId;
  private String depositoId;
  private String descripcion;
  private String estado;
  private String fechaRegistro;

  /** Momento en que se informó la donación por primera vez: es el "ingreso" del tiempo de entrega. */
  private Instant fechaIngreso;

  @Builder.Default private List<ProductoDonado> productos = new ArrayList<>();

  private Instant ultimaActualizacion;
}

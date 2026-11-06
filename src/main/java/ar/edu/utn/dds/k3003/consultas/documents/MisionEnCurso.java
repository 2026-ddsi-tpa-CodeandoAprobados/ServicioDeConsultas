package ar.edu.utn.dds.k3003.consultas.documents;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MisionEnCurso implements Serializable {
  private static final long serialVersionUID = 1L;

  private String nombre;
  private Integer progreso;
  private Integer objetivo;
}

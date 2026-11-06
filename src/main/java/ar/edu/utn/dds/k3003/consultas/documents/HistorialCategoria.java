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
public class HistorialCategoria implements Serializable {
  private static final long serialVersionUID = 1L;

  private String categoria;
  private String desde;
}

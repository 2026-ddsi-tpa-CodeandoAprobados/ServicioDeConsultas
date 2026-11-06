package ar.edu.utn.dds.k3003.consultas.documents;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
@Document(collection = "donadores")
public class DonadorDocument implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id private String id;
  private String nombre;
  private String apellido;
  private String estado;
  private String categoria;

  @Builder.Default
  private List<HistorialCategoria> historialCategorias = new ArrayList<>();

  @Builder.Default private List<String> insignias = new ArrayList<>();

  private MisionEnCurso misionEnCurso;

  /** Nombres de las misiones que el donador ya cumplió (las informa Incentivos). */
  @Builder.Default private List<String> misionesCompletadas = new ArrayList<>();

  @Builder.Default private Map<String, Integer> donacionesPorEstado = new HashMap<>();

  @Builder.Default private Integer quejasRecibidas = 0;

  @Builder.Default
  private List<EntidadBeneficiadaResumen> entidadesBeneficiadas = new ArrayList<>();

  private Instant ultimaActualizacion;

  /**
   * Timestamp del último evento de Incentivos aplicado. Va separado de {@code ultimaActualizacion} para
   * que los eventos de otros módulos no hagan descartar los de Incentivos (y al revés).
   */
  private Instant ultimaActualizacionIncentivos;
}

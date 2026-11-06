package ar.edu.utn.dds.k3003.consultas.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventoGenerico implements Serializable {
  private static final long serialVersionUID = 1L;

  private String id;
  private String tipo;
  private Instant timestamp;
  private String idReferencia;
  private Map<String, Object> donador;
  private Map<String, Object> entidad;
  private Map<String, Object> necesidad;
  private Map<String, Object> queja;
  private Map<String, Object> donacion;
  private Map<String, Object> deposito;
  private Map<String, Object> insignia;
  private Map<String, Object> mision;
  private Map<String, Object> asignacion;
  private Map<String, Object> producto;

  @Builder.Default private Map<String, Object> extra = new HashMap<>();
}

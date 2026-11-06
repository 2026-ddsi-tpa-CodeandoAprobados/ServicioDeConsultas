package ar.edu.utn.dds.k3003.consultas.documents;

import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Asignación de un paquete a una necesidad, tal como la publica Logística. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "asignaciones")
public class AsignacionDocument implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id private String id;
  private String paqueteId;
  private String donacionId;
  private String productoId;
  private Integer cantidad;
  private String necesidadId;
  /** Se completa con la entidad de la necesidad, que viene en los eventos de Donadores y Entidades. */
  private String entidadId;
  private String entidadRazonSocial;
  private String origen;
  private String estado;
  private Instant fechaAsignacion;
  private Instant fechaEntrega;
  private Instant ultimaActualizacion;
}

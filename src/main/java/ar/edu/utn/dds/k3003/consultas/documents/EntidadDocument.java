package ar.edu.utn.dds.k3003.consultas.documents;

import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Nombre de cada entidad benéfica, tal como lo informa Donadores y Entidades (ENTIDAD_*). Se guarda solo
 * para completar la razón social en las necesidades (consulta 2) y en las entidades beneficiadas de cada
 * donador (consulta 1), que en los eventos llegan solo con el id de la entidad.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "entidades")
public class EntidadDocument implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id private String id;
  private String razonSocial;
  /** Timestamp del último evento de Donadores y Entidades aplicado a esta entidad. */
  private Instant ultimaActualizacion;
}

package ar.edu.utn.dds.k3003.consultas.documents;

import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Id de un evento ya aplicado: permite ignorar los que llegan repetidos. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "eventos_procesados")
public class EventoProcesado implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id private String id;
  private Instant procesadoEn;
}

package ar.edu.utn.dds.k3003.consultas.documents;

import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Un producto del catálogo de Donaciones con su subcategoría y su categoría (id y nombre). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "productos")
public class ProductoDocument implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id private String id;
  private String nombre;
  private String categoriaId;
  private String categoria;
  private String subcategoriaId;
  private String subcategoria;
  private Instant ultimaActualizacion;

  /** Lo que se muestra de la categoría: el nombre, o el id si no hay nombre. */
  public String etiquetaCategoria() {
    return categoria != null ? categoria : categoriaId;
  }

  /** Lo que se muestra de la subcategoría: el nombre, o el id si no hay nombre. */
  public String etiquetaSubcategoria() {
    return subcategoria != null ? subcategoria : subcategoriaId;
  }
}

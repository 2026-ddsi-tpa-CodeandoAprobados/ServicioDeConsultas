package ar.edu.utn.dds.k3003.consultas.documents;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Un producto de una donación, con la categoría y la subcategoría para la consulta de calidad. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductoDonado implements Serializable {
  private static final long serialVersionUID = 1L;

  private String productoId;
  private Integer cantidad;
  /** Ids de la categoría y la subcategoría (categoriaID / subcategoriaID del detalle de Donaciones). */
  private String categoriaId;
  private String subcategoriaId;

  /**
   * Nombres de la categoría y la subcategoría. La consulta 4 agrupa y filtra por estos; si faltan, el producto
   * se informa como SIN_CATEGORIA / general.
   */
  private String categoria;
  private String subcategoria;
}

package ar.edu.utn.dds.k3003.consultas.repositories;

import ar.edu.utn.dds.k3003.consultas.documents.NecesidadUrgenteDocument;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NecesidadDocumentRepository
    extends MongoRepository<NecesidadUrgenteDocument, String> {

  List<NecesidadUrgenteDocument> findBySatisfechaFalseOrderByUrgenciaDesc();

  List<NecesidadUrgenteDocument> findByUrgenciaGreaterThanEqualAndSatisfechaFalse(Integer urgenciaMinima);

  List<NecesidadUrgenteDocument> findByTipoNecesidadAndSatisfechaFalse(String tipoNecesidad);

  List<NecesidadUrgenteDocument> findByCategoriaProductoAndSatisfechaFalse(String categoriaProducto);

  List<NecesidadUrgenteDocument> findByProductoSolicitadoId(String productoSolicitadoId);

  List<NecesidadUrgenteDocument> findByEntidadId(String entidadId);
}

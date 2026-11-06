package ar.edu.utn.dds.k3003.consultas.repositories;

import ar.edu.utn.dds.k3003.consultas.documents.CalidadDonacionDocument;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CalidadDonacionRepository
    extends MongoRepository<CalidadDonacionDocument, String> {

  List<CalidadDonacionDocument> findByCategoriaProducto(String categoriaProducto);
}

package ar.edu.utn.dds.k3003.consultas.repositories;

import ar.edu.utn.dds.k3003.consultas.documents.DonadorDocument;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DonadorDocumentRepository extends MongoRepository<DonadorDocument, String> {

  List<DonadorDocument> findByEstado(String estado);

  List<DonadorDocument> findByCategoria(String categoria);
}

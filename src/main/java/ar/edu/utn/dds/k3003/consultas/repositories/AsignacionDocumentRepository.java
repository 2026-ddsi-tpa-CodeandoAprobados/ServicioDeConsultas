package ar.edu.utn.dds.k3003.consultas.repositories;

import ar.edu.utn.dds.k3003.consultas.documents.AsignacionDocument;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AsignacionDocumentRepository extends MongoRepository<AsignacionDocument, String> {

  List<AsignacionDocument> findByNecesidadId(String necesidadId);

  List<AsignacionDocument> findByDonacionIdIn(java.util.Collection<String> donacionIds);
}

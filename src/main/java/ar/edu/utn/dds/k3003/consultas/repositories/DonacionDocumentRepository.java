package ar.edu.utn.dds.k3003.consultas.repositories;

import ar.edu.utn.dds.k3003.consultas.documents.DonacionDocument;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DonacionDocumentRepository extends MongoRepository<DonacionDocument, String> {

  List<DonacionDocument> findByDonadorId(String donadorId);

  List<DonacionDocument> findByDepositoId(String depositoId);
}

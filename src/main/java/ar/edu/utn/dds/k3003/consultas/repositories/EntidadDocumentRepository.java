package ar.edu.utn.dds.k3003.consultas.repositories;

import ar.edu.utn.dds.k3003.consultas.documents.EntidadDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EntidadDocumentRepository extends MongoRepository<EntidadDocument, String> {}

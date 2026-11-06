package ar.edu.utn.dds.k3003.consultas.repositories;

import ar.edu.utn.dds.k3003.consultas.documents.EventoProcesado;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventoProcesadoRepository extends MongoRepository<EventoProcesado, String> {}

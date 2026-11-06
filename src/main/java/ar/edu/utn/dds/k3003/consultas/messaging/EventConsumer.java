package ar.edu.utn.dds.k3003.consultas.messaging;

import ar.edu.utn.dds.k3003.consultas.service.EventProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EventConsumer {

  private final EventProcessingService eventProcessingService;

  public EventConsumer(EventProcessingService eventProcessingService) {
    this.eventProcessingService = eventProcessingService;
  }

  @RabbitListener(
      queues = "${app.rabbitmq.queue:consultas.mongo.queue}",
      containerFactory = "rabbitListenerContainerFactory")
  public void consumirEvento(EventoGenerico evento) {
    if (evento == null) {
      log.warn("Mensaje nulo recibido en la cola de consultas");
      return;
    }
    eventProcessingService.procesar(evento);
  }
}

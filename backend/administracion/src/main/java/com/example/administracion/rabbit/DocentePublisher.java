package com.example.administracion.rabbit;

import com.example.administracion.model.Docente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class DocentePublisher {

    private static final Logger log = LoggerFactory.getLogger(DocentePublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public DocentePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicarDocenteCreado(Docente docente) {
        EventoNotificacion evento = new EventoNotificacion(
                "docente.creado",
                docente.getId(),
                docente.getNombre() + " " + docente.getApellido() + " (especialidad: " + docente.getEspecialidad() + ")");
        log.info("Publicando evento en RabbitMQ -> exchange: {}, routingKey: {}, evento: {}", RabbitMQConfig.EXCHANGE, "docente.creado", evento);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, "docente.creado", evento);
    }
}
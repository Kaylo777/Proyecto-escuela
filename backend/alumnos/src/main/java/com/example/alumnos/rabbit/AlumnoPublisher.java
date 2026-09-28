package com.example.alumnos.rabbit;

import com.example.alumnos.model.Alumno;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class AlumnoPublisher {

    private static final Logger log = LoggerFactory.getLogger(AlumnoPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public AlumnoPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicarAlumnoCreado(Alumno alumno) {
        EventoNotificacion evento = new EventoNotificacion(
                "alumno.creado",
                alumno.getId(),
                alumno.getNombre() + " " + alumno.getApellido() + " (curso: " + alumno.getCurso() + ")");
        log.info("Publicando evento en RabbitMQ -> exchange: {}, routingKey: {}, evento: {}", RabbitMQConfig.EXCHANGE, "alumno.creado", evento);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, "alumno.creado", evento);
    }
}
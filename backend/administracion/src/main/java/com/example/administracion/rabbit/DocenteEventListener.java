package com.example.administracion.rabbit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class DocenteEventListener {

    private static final Logger log = LoggerFactory.getLogger(DocenteEventListener.class);

    @RabbitListener(queues = RabbitMQConfig.COLA_NOTIFICACIONES)
    public void recibirNotificacion(@Payload EventoNotificacion evento) {
        log.info("ADMINISTRACION | Evento recibido desde RabbitMQ: {}", evento);
    }
}
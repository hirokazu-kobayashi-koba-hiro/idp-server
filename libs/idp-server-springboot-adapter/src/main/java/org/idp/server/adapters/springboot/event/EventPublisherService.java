package org.idp.server.adapters.springboot.event;

import org.idp.server.core.sharedsignal.Event;
import org.idp.server.core.sharedsignal.EventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class EventPublisherService implements EventPublisher {

  ApplicationEventPublisher applicationEventPublisher;

  public EventPublisherService(ApplicationEventPublisher applicationEventPublisher) {
    this.applicationEventPublisher = applicationEventPublisher;
  }

  @Override
  public void publish(Event event) {
    applicationEventPublisher.publishEvent(event);
  }
}

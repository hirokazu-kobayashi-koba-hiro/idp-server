package org.idp.server.adapters.springboot.application.event;

import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.SecurityEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class SecurityEventPublisherService implements SecurityEventPublisher {

  ApplicationEventPublisher applicationEventPublisher;

  public SecurityEventPublisherService(ApplicationEventPublisher applicationEventPublisher) {
    this.applicationEventPublisher = applicationEventPublisher;
  }

  @Override
  public void publish(SecurityEvent securityEvent) {
    applicationEventPublisher.publishEvent(securityEvent);
  }
}

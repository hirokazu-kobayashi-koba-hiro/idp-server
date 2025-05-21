package org.idp.server.adapters.springboot.application.event;

import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.SecurityEventPublisher;
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

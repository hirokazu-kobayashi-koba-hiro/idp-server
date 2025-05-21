package org.idp.server.adapters.springboot.application.event;

import org.idp.server.core.oidc.identity.event.UserLifecycleEvent;
import org.idp.server.core.oidc.identity.event.UserLifecycleEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class UserLifecycleEventPublisherService implements UserLifecycleEventPublisher {

  ApplicationEventPublisher applicationEventPublisher;

  public UserLifecycleEventPublisherService(ApplicationEventPublisher applicationEventPublisher) {
    this.applicationEventPublisher = applicationEventPublisher;
  }

  @Override
  public void publish(UserLifecycleEvent userLifecycleEvent) {
    applicationEventPublisher.publishEvent(userLifecycleEvent);
  }
}

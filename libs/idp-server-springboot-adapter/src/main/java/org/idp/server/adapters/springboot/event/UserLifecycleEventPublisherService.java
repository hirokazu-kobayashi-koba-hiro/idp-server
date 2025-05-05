package org.idp.server.adapters.springboot.event;

import org.idp.server.core.identity.UserLifecycleEvent;
import org.idp.server.core.identity.UserLifecycleEventPublisher;
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

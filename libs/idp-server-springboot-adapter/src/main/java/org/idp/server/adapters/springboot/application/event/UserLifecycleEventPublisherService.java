/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
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

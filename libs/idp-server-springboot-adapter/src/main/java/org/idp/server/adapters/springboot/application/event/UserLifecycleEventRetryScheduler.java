/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.adapters.springboot.application.event;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.idp.server.IdpServerApplication;
import org.idp.server.core.oidc.identity.event.UserLifecycleEvent;
import org.idp.server.core.oidc.identity.event.UserLifecycleEventApi;
import org.idp.server.platform.log.LoggerWrapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UserLifecycleEventRetryScheduler {

  LoggerWrapper log = LoggerWrapper.getLogger(UserLifecycleEventRetryScheduler.class);

  Queue<UserLifecycleEvent> retryQueue = new ConcurrentLinkedQueue<>();

  UserLifecycleEventApi userLifecycleEventApi;

  public UserLifecycleEventRetryScheduler(IdpServerApplication idpServerApplication) {
    this.userLifecycleEventApi = idpServerApplication.userLifecycleEventApi();
  }

  public void enqueue(UserLifecycleEvent userLifecycleEvent) {
    retryQueue.add(userLifecycleEvent);
  }

  @Scheduled(fixedDelay = 60_000)
  public void resendFailedEvents() {
    while (!retryQueue.isEmpty()) {
      UserLifecycleEvent userLifecycleEvent = retryQueue.poll();
      try {
        log.info("retry event: {}", userLifecycleEvent.lifecycleType().name());
        userLifecycleEventApi.handle(userLifecycleEvent.tenantIdentifier(), userLifecycleEvent);
      } catch (Exception e) {
        log.error("retry event error: {}", userLifecycleEvent.lifecycleType().name());
        retryQueue.add(userLifecycleEvent);
      }
    }
  }
}

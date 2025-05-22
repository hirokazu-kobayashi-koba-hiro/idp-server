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
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.SecurityEventApi;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SecurityEventRetryScheduler {

  LoggerWrapper log = LoggerWrapper.getLogger(SecurityEventRetryScheduler.class);

  Queue<SecurityEvent> retryQueue = new ConcurrentLinkedQueue<>();

  SecurityEventApi securityEventApi;

  public SecurityEventRetryScheduler(IdpServerApplication idpServerApplication) {
    this.securityEventApi = idpServerApplication.securityEventApi();
  }

  public void enqueue(SecurityEvent securityEvent) {
    retryQueue.add(securityEvent);
  }

  @Scheduled(fixedDelay = 60_000)
  public void resendFailedEvents() {
    while (!retryQueue.isEmpty()) {
      SecurityEvent securityEvent = retryQueue.poll();
      try {
        log.info("retry event: {}", securityEvent.toMap());
        securityEventApi.handle(securityEvent.tenantIdentifier(), securityEvent);
      } catch (Exception e) {
        log.error("retry event error: {}", securityEvent.toMap());
        retryQueue.add(securityEvent);
      }
    }
  }
}

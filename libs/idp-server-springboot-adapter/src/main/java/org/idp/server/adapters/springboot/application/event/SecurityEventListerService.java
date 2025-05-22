/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.adapters.springboot.application.event;

import org.idp.server.IdpServerApplication;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.SecurityEventApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class SecurityEventListerService {

  LoggerWrapper log = LoggerWrapper.getLogger(SecurityEventListerService.class);
  TaskExecutor taskExecutor;
  SecurityEventApi securityEventApi;

  public SecurityEventListerService(
      @Qualifier("securityEventTaskExecutor") TaskExecutor taskExecutor,
      IdpServerApplication idpServerApplication) {
    this.taskExecutor = taskExecutor;
    this.securityEventApi = idpServerApplication.securityEventApi();
  }

  @Async
  @EventListener
  public void onEvent(SecurityEvent securityEvent) {
    log.info("onEvent: {}", securityEvent.toMap());

    taskExecutor.execute(
        new SecurityEventRunnable(
            securityEvent,
            event -> {
              securityEventApi.handle(event.tenantIdentifier(), event);
            }));
  }
}

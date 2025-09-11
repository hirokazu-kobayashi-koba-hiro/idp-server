/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    log.info(
        "SecurityEventListerService.onEvent, TenantId {}, event_type: {}",
        securityEvent.tenant().id(),
        securityEvent.type().value());

    taskExecutor.execute(
        new SecurityEventRunnable(
            securityEvent,
            event -> {
              securityEventApi.handle(event.tenantIdentifier(), event);
            }));
  }
}

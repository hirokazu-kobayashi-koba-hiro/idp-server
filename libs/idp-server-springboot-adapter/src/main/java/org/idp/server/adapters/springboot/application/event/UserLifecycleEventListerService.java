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
import org.idp.server.core.openid.identity.event.UserLifecycleEvent;
import org.idp.server.core.openid.identity.event.UserLifecycleEventApi;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.log.TenantLoggingContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class UserLifecycleEventListerService {

  LoggerWrapper log = LoggerWrapper.getLogger(UserLifecycleEventListerService.class);
  TaskExecutor taskExecutor;
  UserLifecycleEventApi userLifecycleEventApi;

  public UserLifecycleEventListerService(
      @Qualifier("userLifecycleEventTaskExecutor") TaskExecutor taskExecutor,
      IdpServerApplication idpServerApplication) {
    this.taskExecutor = taskExecutor;
    this.userLifecycleEventApi = idpServerApplication.userLifecycleEventApi();
  }

  @Async
  @EventListener
  public void onEvent(UserLifecycleEvent userLifecycleEvent) {
    TenantLoggingContext.setTenant(userLifecycleEvent.tenantIdentifier());
    try {
      log.info(
          "user: {}, onEvent: {}",
          userLifecycleEvent.user().sub(),
          userLifecycleEvent.lifecycleType().name());

      taskExecutor.execute(
          new UserLifecycleEventRunnable(
              userLifecycleEvent,
              event -> {
                userLifecycleEventApi.handle(event.tenantIdentifier(), event);
              }));
    } finally {
      TenantLoggingContext.clearAll();
    }
  }
}

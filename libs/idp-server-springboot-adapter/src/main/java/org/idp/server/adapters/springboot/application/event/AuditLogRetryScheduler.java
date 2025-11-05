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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogApi;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuditLogRetryScheduler {

  LoggerWrapper log = LoggerWrapper.getLogger(AuditLogRetryScheduler.class);

  Queue<AuditLog> retryQueue = new ConcurrentLinkedQueue<>();

  AuditLogApi auditLogApi;

  public AuditLogRetryScheduler(IdpServerApplication idpServerApplication) {
    this.auditLogApi = idpServerApplication.auditLogApi();
  }

  public void enqueue(AuditLog auditLog) {
    retryQueue.add(auditLog);
  }

  @Scheduled(fixedDelay = 60_000)
  public void resendFailedEvents() {
    while (!retryQueue.isEmpty()) {
      AuditLog auditLog = retryQueue.poll();
      try {
        log.info("retry audit log: {}", auditLog.type());
        auditLogApi.handle(auditLog.tenantIdentifier(), auditLog);
      } catch (Exception e) {
        log.error("retry audit log error: {}", auditLog.type());
        retryQueue.add(auditLog);
      }
    }
  }
}

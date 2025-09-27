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
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogApi;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.log.TenantLoggingContext;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Asynchronous event listener for audit log processing.
 *
 * <p>This service handles audit log events asynchronously to improve performance and enable
 * read-only transactions in management APIs.
 *
 * <p>Similar to SecurityEventListerService, this follows the same pattern: - @Async for
 * non-blocking processing - TaskExecutor for background execution - Proper tenant context
 * management
 */
@Service
public class AuditLogEventListener {

  LoggerWrapper log = LoggerWrapper.getLogger(AuditLogEventListener.class);
  TaskExecutor taskExecutor;
  AuditLogApi auditLogApi;

  public AuditLogEventListener(
      @Qualifier("auditLogTaskExecutor") TaskExecutor taskExecutor,
      IdpServerApplication idpServerApplication) {
    this.taskExecutor = taskExecutor;
    this.auditLogApi = idpServerApplication.auditLogApi();
  }

  @Async
  @EventListener
  public void onAuditLogEvent(AuditLog auditLog) {
    TenantIdentifier tenantIdentifier = new TenantIdentifier(auditLog.tenantId());
    TenantLoggingContext.setTenant(tenantIdentifier);

    try {
      log.info(
          "AuditLogEventListener.onEvent, type: {}, resource: {}",
          auditLog.type(),
          auditLog.targetResource());

      taskExecutor.execute(
          new AuditLogRunnable(
              auditLog,
              tenantIdentifier,
              event -> {
                auditLogApi.handle(tenantIdentifier, event);
              }));
    } finally {
      TenantLoggingContext.clearAll();
    }
  }
}

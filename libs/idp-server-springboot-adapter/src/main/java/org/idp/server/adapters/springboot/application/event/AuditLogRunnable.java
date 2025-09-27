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

import java.util.function.Consumer;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.log.TenantLoggingContext;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * Runnable implementation for asynchronous audit log processing.
 *
 * <p>This class encapsulates the audit log processing logic to be executed asynchronously in a
 * background thread, following the same pattern as SecurityEventRunnable.
 */
public class AuditLogRunnable implements Runnable {

  AuditLog auditLog;
  TenantIdentifier tenantIdentifier;
  Consumer<AuditLog> handler;

  public AuditLogRunnable(
      AuditLog auditLog, TenantIdentifier tenantIdentifier, Consumer<AuditLog> handler) {
    this.auditLog = auditLog;
    this.tenantIdentifier = tenantIdentifier;
    this.handler = handler;
  }

  public AuditLog getAuditLog() {
    return auditLog;
  }

  @Override
  public void run() {
    TenantLoggingContext.setTenant(tenantIdentifier);
    try {
      handler.accept(auditLog);
    } finally {
      TenantLoggingContext.clearAll();
    }
  }
}

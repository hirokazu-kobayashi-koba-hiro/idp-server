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

package org.idp.server.usecases.control_plane.system_administrator;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.management.system.SystemConfigurationManagementApi;
import org.idp.server.control_plane.management.system.handler.SystemConfigurationFindService;
import org.idp.server.control_plane.management.system.handler.SystemConfigurationManagementHandler;
import org.idp.server.control_plane.management.system.handler.SystemConfigurationManagementResult;
import org.idp.server.control_plane.management.system.handler.SystemConfigurationManagementService;
import org.idp.server.control_plane.management.system.handler.SystemConfigurationUpdateService;
import org.idp.server.control_plane.management.system.io.SystemConfigurationManagementResponse;
import org.idp.server.control_plane.management.system.io.SystemConfigurationUpdateRequest;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.system.SystemConfigurationRepository;
import org.idp.server.platform.system.SystemConfigurationResolver;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Entry service for system configuration management.
 *
 * <p>Orchestrates system configuration management operations by delegating to Handler.
 *
 * <h2>Responsibilities</h2>
 *
 * <ul>
 *   <li>Transaction management
 *   <li>Handler invocation
 * </ul>
 */
@Transaction
public class SystemConfigurationManagementEntryService implements SystemConfigurationManagementApi {

  private final SystemConfigurationManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  public SystemConfigurationManagementEntryService(
      SystemConfigurationRepository repository,
      SystemConfigurationResolver resolver,
      AuditLogPublisher auditLogPublisher) {
    this.auditLogPublisher = auditLogPublisher;

    Map<String, SystemConfigurationManagementService<?>> services = new HashMap<>();
    services.put("get", new SystemConfigurationFindService(resolver));
    services.put("put", new SystemConfigurationUpdateService(repository, resolver));

    this.handler = new SystemConfigurationManagementHandler(services, this);
  }

  @Override
  @Transaction(readOnly = true)
  public SystemConfigurationManagementResponse get(
      AdminAuthenticationContext authenticationContext, RequestAttributes requestAttributes) {

    SystemConfigurationManagementResult result =
        handler.handle("get", authenticationContext, null, requestAttributes, false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public SystemConfigurationManagementResponse update(
      AdminAuthenticationContext authenticationContext,
      SystemConfigurationUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    SystemConfigurationManagementResult result =
        handler.handle("put", authenticationContext, request, requestAttributes, dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}

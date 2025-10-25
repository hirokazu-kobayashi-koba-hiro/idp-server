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

package org.idp.server.usecases.control_plane.system_manager;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.management.security.hook_result.SecurityEventHookManagementApi;
import org.idp.server.control_plane.management.security.hook_result.handler.*;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookFindListRequest;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookFindRequest;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookManagementResponse;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookRetryRequest;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.hook.*;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.platform.security.repository.SecurityEventHookResultCommandRepository;
import org.idp.server.platform.security.repository.SecurityEventHookResultQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class SecurityEventHookManagementEntryService implements SecurityEventHookManagementApi {

  private final SecurityEventHookManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  public SecurityEventHookManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      SecurityEventHookResultQueryRepository securityEventHookResultQueryRepository,
      SecurityEventHookResultCommandRepository securityEventHookResultCommandRepository,
      SecurityEventHooks securityEventHooks,
      SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, SecurityEventHookManagementService<?>> services = new HashMap<>();
    services.put(
        "findList", new SecurityEventHookFindListService(securityEventHookResultQueryRepository));
    services.put("get", new SecurityEventHookFindService(securityEventHookResultQueryRepository));
    services.put(
        "retry",
        new SecurityEventHookRetryService(
            securityEventHookResultQueryRepository,
            securityEventHookResultCommandRepository,
            securityEventHooks,
            securityEventHookConfigurationQueryRepository));

    this.handler = new SecurityEventHookManagementHandler(services, this, tenantQueryRepository);
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  @Transaction(readOnly = true)
  public SecurityEventHookManagementResponse findList(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      SecurityEventHookResultQueries queries,
      RequestAttributes requestAttributes) {

    SecurityEventHookManagementResult result =
        handler.handle(
            "findList",
            authenticationContext,
            tenantIdentifier,
            new SecurityEventHookFindListRequest(queries),
            requestAttributes);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }

  @Override
  @Transaction(readOnly = true)
  public SecurityEventHookManagementResponse get(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      SecurityEventHookResultIdentifier identifier,
      RequestAttributes requestAttributes) {

    SecurityEventHookManagementResult result =
        handler.handle(
            "get",
            authenticationContext,
            tenantIdentifier,
            new SecurityEventHookFindRequest(identifier),
            requestAttributes);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }

  /**
   * Entry service for security event hook retry management operations.
   *
   * <p>This service handles manual retry operations for failed security event hook executions,
   * providing system administrators with the ability to re-execute failed hooks after resolving
   * underlying issues.
   */
  @Override
  public SecurityEventHookManagementResponse retry(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      SecurityEventHookResultIdentifier identifier,
      RequestAttributes requestAttributes) {

    SecurityEventHookManagementResult result =
        handler.handle(
            "retry",
            authenticationContext,
            tenantIdentifier,
            new SecurityEventHookRetryRequest(identifier),
            requestAttributes);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }
}

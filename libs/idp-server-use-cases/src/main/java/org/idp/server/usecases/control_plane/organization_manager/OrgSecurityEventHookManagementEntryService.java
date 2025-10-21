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

package org.idp.server.usecases.control_plane.organization_manager;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.management.security.hook_result.OrgSecurityEventHookManagementApi;
import org.idp.server.control_plane.management.security.hook_result.handler.*;
import org.idp.server.control_plane.management.security.hook_result.io.SecurityEventHookManagementResponse;
import org.idp.server.control_plane.organization.access.OrganizationAccessVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.hook.*;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.platform.security.repository.SecurityEventHookResultCommandRepository;
import org.idp.server.platform.security.repository.SecurityEventHookResultQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class OrgSecurityEventHookManagementEntryService
    implements OrgSecurityEventHookManagementApi {

  private final OrgSecurityEventHookManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  public OrgSecurityEventHookManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
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

    this.handler =
        new OrgSecurityEventHookManagementHandler(
            services,
            this,
            tenantQueryRepository,
            organizationRepository,
            new OrganizationAccessVerifier());
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  @Transaction(readOnly = true)
  public SecurityEventHookManagementResponse findList(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookResultQueries queries,
      RequestAttributes requestAttributes) {

    SecurityEventHookManagementResult result =
        handler.handle(
            "findList",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            queries,
            requestAttributes);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "SecurityEventHookManagementApi.findList",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse();
    }

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "SecurityEventHookManagementApi.findList",
            "findList",
            result.tenant(),
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }

  @Override
  @Transaction(readOnly = true)
  public SecurityEventHookManagementResponse get(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookResultIdentifier identifier,
      RequestAttributes requestAttributes) {

    SecurityEventHookManagementResult result =
        handler.handle(
            "get",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            identifier,
            requestAttributes);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "SecurityEventHookManagementApi.get",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse();
    }

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "SecurityEventHookManagementApi.get",
            "get",
            result.tenant(),
            operator,
            oAuthToken,
            requestAttributes);
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
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookResultIdentifier identifier,
      RequestAttributes requestAttributes) {

    SecurityEventHookManagementResult result =
        handler.handle(
            "retry",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            identifier,
            requestAttributes);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "SecurityEventHookManagementApi.retry",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse();
    }

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "SecurityEventHookManagementApi.retry",
            "retry",
            result.tenant(),
            operator,
            oAuthToken,
            Map.of("hook_result_identifier", identifier.value()),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }
}

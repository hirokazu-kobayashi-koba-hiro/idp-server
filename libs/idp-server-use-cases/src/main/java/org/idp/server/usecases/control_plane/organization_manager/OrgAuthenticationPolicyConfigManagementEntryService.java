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
import org.idp.server.control_plane.base.OrganizationAccessVerifier;
import org.idp.server.control_plane.management.authentication.policy.OrgAuthenticationPolicyConfigManagementApi;
import org.idp.server.control_plane.management.authentication.policy.handler.*;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigRequest;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfigurationIdentifier;
import org.idp.server.core.openid.authentication.repository.AuthenticationPolicyConfigurationCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationPolicyConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level authentication policy configuration management entry service.
 *
 * <p>This service implements organization-scoped authentication policy configuration management
 * operations that allow organization administrators to manage authentication policy configurations
 * within their organization boundaries.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       AUTHENTICATION_POLICY_CONFIG_* permissions (handled by Handler)
 * </ol>
 *
 * <p>All operations support dry-run functionality for safe preview of changes and comprehensive
 * audit logging for organization-level authentication policy configuration operations.
 *
 * @see OrgAuthenticationPolicyConfigManagementApi
 * @see OrganizationAccessVerifier
 * @see
 *     org.idp.server.usecases.control_plane.system_manager.AuthenticationPolicyConfigurationManagementEntryService
 */
@Transaction
public class OrgAuthenticationPolicyConfigManagementEntryService
    implements OrgAuthenticationPolicyConfigManagementApi {

  AuditLogPublisher auditLogPublisher;

  LoggerWrapper log =
      LoggerWrapper.getLogger(OrgAuthenticationPolicyConfigManagementEntryService.class);

  // Handler/Service pattern (organization-level)
  private OrgAuthenticationPolicyConfigManagementHandler handler;

  public OrgAuthenticationPolicyConfigManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      AuthenticationPolicyConfigurationCommandRepository
          authenticationPolicyConfigurationCommandRepository,
      AuthenticationPolicyConfigurationQueryRepository
          authenticationPolicyConfigurationQueryRepository,
      AuditLogPublisher auditLogPublisher) {
    this.auditLogPublisher = auditLogPublisher;

    this.handler =
        createHandler(
            authenticationPolicyConfigurationCommandRepository,
            authenticationPolicyConfigurationQueryRepository,
            tenantQueryRepository,
            organizationRepository);
  }

  private OrgAuthenticationPolicyConfigManagementHandler createHandler(
      AuthenticationPolicyConfigurationCommandRepository
          authenticationPolicyConfigurationCommandRepository,
      AuthenticationPolicyConfigurationQueryRepository
          authenticationPolicyConfigurationQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository) {

    Map<String, AuthenticationPolicyConfigManagementService<?>> services = new HashMap<>();
    services.put(
        "create",
        new AuthenticationPolicyConfigCreationService(
            authenticationPolicyConfigurationCommandRepository));
    services.put(
        "findList",
        new AuthenticationPolicyConfigFindListService(
            authenticationPolicyConfigurationQueryRepository));
    services.put(
        "get",
        new AuthenticationPolicyConfigFindService(
            authenticationPolicyConfigurationQueryRepository));
    services.put(
        "update",
        new AuthenticationPolicyConfigUpdateService(
            authenticationPolicyConfigurationQueryRepository,
            authenticationPolicyConfigurationCommandRepository));
    services.put(
        "delete",
        new AuthenticationPolicyConfigDeletionService(
            authenticationPolicyConfigurationQueryRepository,
            authenticationPolicyConfigurationCommandRepository));

    return new OrgAuthenticationPolicyConfigManagementHandler(
        services, this, tenantQueryRepository, organizationRepository);
  }

  @Override
  public AuthenticationPolicyConfigManagementResponse create(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationPolicyConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern (Handler performs all access control)
    AuthenticationPolicyConfigManagementResult result =
        handler.handle(
            "create",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            request,
            requestAttributes,
            dryRun);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgAuthenticationPolicyConfigManagementApi.create",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    // Record audit log (create operation)
    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  @Transaction(readOnly = true)
  public AuthenticationPolicyConfigManagementResponse findList(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern (Handler performs all access control)
    AuthenticationPolicyConfigFindListRequest request =
        new AuthenticationPolicyConfigFindListRequest(limit, offset);
    AuthenticationPolicyConfigManagementResult result =
        handler.handle(
            "findList",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            request,
            requestAttributes,
            false);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgAuthenticationPolicyConfigManagementApi.findList",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(false);
    }

    // Record audit log (read operation)
    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgAuthenticationPolicyConfigManagementApi.findList",
            "findList",
            result.tenant(),
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public AuthenticationPolicyConfigManagementResponse get(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationPolicyConfigurationIdentifier identifier,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern (Handler performs all access control)
    AuthenticationPolicyConfigManagementResult result =
        handler.handle(
            "get",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            identifier,
            requestAttributes,
            false);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgAuthenticationPolicyConfigManagementApi.get",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(false);
    }

    // Record audit log (read operation)
    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgAuthenticationPolicyConfigManagementApi.get",
            "get",
            result.tenant(),
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public AuthenticationPolicyConfigManagementResponse update(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationPolicyConfigurationIdentifier identifier,
      AuthenticationPolicyConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern (Handler performs all access control)
    AuthenticationPolicyConfigUpdateRequest updateRequest =
        new AuthenticationPolicyConfigUpdateRequest(identifier, request);
    AuthenticationPolicyConfigManagementResult result =
        handler.handle(
            "update",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            updateRequest,
            requestAttributes,
            dryRun);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgAuthenticationPolicyConfigManagementApi.update",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    // Record audit log (update operation)
    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public AuthenticationPolicyConfigManagementResponse delete(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationPolicyConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // Delegate to Handler/Service pattern (Handler performs all access control)
    AuthenticationPolicyConfigManagementResult result =
        handler.handle(
            "delete",
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            identifier,
            requestAttributes,
            dryRun);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "OrgAuthenticationPolicyConfigManagementApi.delete",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse(dryRun);
    }

    // Record audit log (deletion operation)
    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "OrgAuthenticationPolicyConfigManagementApi.delete",
            "delete",
            result.tenant(),
            operator,
            oAuthToken,
            (Map<String, Object>) result.context(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}

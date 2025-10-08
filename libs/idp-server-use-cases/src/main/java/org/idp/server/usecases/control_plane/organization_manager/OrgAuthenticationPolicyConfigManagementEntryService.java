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
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.authentication.policy.AuthenticationPolicyConfigRegistrationContext;
import org.idp.server.control_plane.management.authentication.policy.AuthenticationPolicyConfigRegistrationContextCreator;
import org.idp.server.control_plane.management.authentication.policy.AuthenticationPolicyConfigUpdateContext;
import org.idp.server.control_plane.management.authentication.policy.AuthenticationPolicyConfigUpdateContextCreator;
import org.idp.server.control_plane.management.authentication.policy.OrgAuthenticationPolicyConfigManagementApi;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementStatus;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigRequest;
import org.idp.server.control_plane.organization.access.OrganizationAccessControlResult;
import org.idp.server.control_plane.organization.access.OrganizationAccessVerifier;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfiguration;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfigurationIdentifier;
import org.idp.server.core.openid.authentication.repository.AuthenticationPolicyConfigurationCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationPolicyConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
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
 *       AUTHENTICATION_POLICY_CONFIG_* permissions
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

  TenantQueryRepository tenantQueryRepository;
  OrganizationRepository organizationRepository;
  AuthenticationPolicyConfigurationCommandRepository
      authenticationPolicyConfigurationCommandRepository;
  AuthenticationPolicyConfigurationQueryRepository authenticationPolicyConfigurationQueryRepository;
  AuditLogPublisher auditLogPublisher;
  OrganizationAccessVerifier organizationAccessVerifier;

  LoggerWrapper log =
      LoggerWrapper.getLogger(OrgAuthenticationPolicyConfigManagementEntryService.class);

  /**
   * Creates a new organization authentication policy configuration management entry service.
   *
   * @param tenantQueryRepository the tenant query repository
   * @param organizationRepository the organization repository
   * @param authenticationPolicyConfigurationCommandRepository the authentication policy
   *     configuration command repository
   * @param authenticationPolicyConfigurationQueryRepository the authentication policy configuration
   *     query repository
   * @param auditLogPublisher the audit log publisher
   */
  public OrgAuthenticationPolicyConfigManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      AuthenticationPolicyConfigurationCommandRepository
          authenticationPolicyConfigurationCommandRepository,
      AuthenticationPolicyConfigurationQueryRepository
          authenticationPolicyConfigurationQueryRepository,
      AuditLogPublisher auditLogPublisher) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.authenticationPolicyConfigurationCommandRepository =
        authenticationPolicyConfigurationCommandRepository;
    this.authenticationPolicyConfigurationQueryRepository =
        authenticationPolicyConfigurationQueryRepository;
    this.auditLogPublisher = auditLogPublisher;
    this.organizationAccessVerifier = new OrganizationAccessVerifier();
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

    AdminPermissions permissions = getRequiredPermissions("create");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new AuthenticationPolicyConfigManagementResponse(
          AuthenticationPolicyConfigManagementStatus.FORBIDDEN, response);
    }

    // Create context using the existing Context Creator pattern
    AuthenticationPolicyConfigRegistrationContextCreator contextCreator =
        new AuthenticationPolicyConfigRegistrationContextCreator(targetTenant, request, dryRun);
    AuthenticationPolicyConfigRegistrationContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.create(
            "OrgAuthenticationPolicyConfigManagementApi.create",
            targetTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (context.isDryRun()) {
      return context.toResponse();
    }

    authenticationPolicyConfigurationCommandRepository.register(
        targetTenant, context.configuration());

    return context.toResponse();
  }

  @Transaction(readOnly = true)
  public AuthenticationPolicyConfigManagementResponse findList(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("findList");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgAuthenticationPolicyConfigManagementApi.findList",
            "findList",
            targetTenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new AuthenticationPolicyConfigManagementResponse(
          AuthenticationPolicyConfigManagementStatus.FORBIDDEN, response);
    }

    long totalCount = authenticationPolicyConfigurationQueryRepository.findTotalCount(targetTenant);
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", limit);
      response.put("offset", offset);
      return new AuthenticationPolicyConfigManagementResponse(
          AuthenticationPolicyConfigManagementStatus.OK, response);
    }

    List<AuthenticationPolicyConfiguration> configurations =
        authenticationPolicyConfigurationQueryRepository.findList(targetTenant, limit, offset);

    Map<String, Object> response = new HashMap<>();
    response.put(
        "list", configurations.stream().map(AuthenticationPolicyConfiguration::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", limit);
    response.put("offset", offset);

    return new AuthenticationPolicyConfigManagementResponse(
        AuthenticationPolicyConfigManagementStatus.OK, response);
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

    AdminPermissions permissions = getRequiredPermissions("get");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    AuthenticationPolicyConfiguration configuration =
        authenticationPolicyConfigurationQueryRepository.findWithDisabled(
            targetTenant, identifier, true);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgAuthenticationPolicyConfigManagementApi.get",
            "get",
            targetTenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new AuthenticationPolicyConfigManagementResponse(
          AuthenticationPolicyConfigManagementStatus.FORBIDDEN, response);
    }

    if (!configuration.exists()) {
      return new AuthenticationPolicyConfigManagementResponse(
          AuthenticationPolicyConfigManagementStatus.NOT_FOUND, Map.of());
    }

    return new AuthenticationPolicyConfigManagementResponse(
        AuthenticationPolicyConfigManagementStatus.OK, configuration.toMap());
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

    AdminPermissions permissions = getRequiredPermissions("update");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    AuthenticationPolicyConfiguration before =
        authenticationPolicyConfigurationQueryRepository.findWithDisabled(
            targetTenant, identifier, true);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new AuthenticationPolicyConfigManagementResponse(
          AuthenticationPolicyConfigManagementStatus.FORBIDDEN, response);
    }

    if (!before.exists()) {
      return new AuthenticationPolicyConfigManagementResponse(
          AuthenticationPolicyConfigManagementStatus.NOT_FOUND, Map.of());
    }

    // Create context using the existing Context Creator pattern
    AuthenticationPolicyConfigUpdateContextCreator contextCreator =
        new AuthenticationPolicyConfigUpdateContextCreator(targetTenant, before, request, dryRun);
    AuthenticationPolicyConfigUpdateContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "OrgAuthenticationPolicyConfigManagementApi.update",
            targetTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (context.isDryRun()) {
      return context.toResponse();
    }

    authenticationPolicyConfigurationCommandRepository.update(targetTenant, context.after());

    return context.toResponse();
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

    AdminPermissions permissions = getRequiredPermissions("delete");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    AuthenticationPolicyConfiguration configuration =
        authenticationPolicyConfigurationQueryRepository.findWithDisabled(
            targetTenant, identifier, true);

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "OrgAuthenticationPolicyConfigManagementApi.delete",
            "delete",
            targetTenant,
            operator,
            oAuthToken,
            configuration.toMap(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new AuthenticationPolicyConfigManagementResponse(
          AuthenticationPolicyConfigManagementStatus.FORBIDDEN, response);
    }

    if (!configuration.exists()) {
      return new AuthenticationPolicyConfigManagementResponse(
          AuthenticationPolicyConfigManagementStatus.NOT_FOUND, Map.of());
    }

    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put("message", "Deletion simulated successfully");
      response.put("id", configuration.identifier().value());
      response.put("dry_run", true);
      return new AuthenticationPolicyConfigManagementResponse(
          AuthenticationPolicyConfigManagementStatus.OK, response);
    }

    authenticationPolicyConfigurationCommandRepository.delete(targetTenant, configuration);

    return new AuthenticationPolicyConfigManagementResponse(
        AuthenticationPolicyConfigManagementStatus.NO_CONTENT, configuration.toMap());
  }
}

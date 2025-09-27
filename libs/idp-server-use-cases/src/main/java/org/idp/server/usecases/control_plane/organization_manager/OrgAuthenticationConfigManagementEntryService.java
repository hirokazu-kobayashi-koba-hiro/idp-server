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
import org.idp.server.control_plane.management.authentication.configuration.AuthenticationConfigRegistrationContext;
import org.idp.server.control_plane.management.authentication.configuration.AuthenticationConfigRegistrationContextCreator;
import org.idp.server.control_plane.management.authentication.configuration.AuthenticationConfigUpdateContext;
import org.idp.server.control_plane.management.authentication.configuration.AuthenticationConfigUpdateContextCreator;
import org.idp.server.control_plane.management.authentication.configuration.OrgAuthenticationConfigManagementApi;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigManagementStatus;
import org.idp.server.control_plane.management.authentication.configuration.io.AuthenticationConfigRequest;
import org.idp.server.control_plane.organization.access.OrganizationAccessControlResult;
import org.idp.server.control_plane.organization.access.OrganizationAccessVerifier;
import org.idp.server.core.openid.authentication.config.AuthenticationConfiguration;
import org.idp.server.core.openid.authentication.config.AuthenticationConfigurationIdentifier;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationCommandRepository;
import org.idp.server.core.openid.authentication.repository.AuthenticationConfigurationQueryRepository;
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
 * Organization-level authentication configuration management entry service.
 *
 * <p>This service implements organization-scoped authentication configuration management operations
 * that allow organization administrators to manage authentication configurations within their
 * organization boundaries.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       AUTHENTICATION_CONFIG_* permissions
 * </ol>
 *
 * <p>All operations support dry-run functionality for safe preview of changes and comprehensive
 * audit logging for organization-level authentication configuration operations.
 *
 * @see OrgAuthenticationConfigManagementApi
 * @see OrganizationAccessVerifier
 * @see
 *     org.idp.server.usecases.control_plane.system_manager.AuthenticationConfigurationManagementEntryService
 */
@Transaction
public class OrgAuthenticationConfigManagementEntryService
    implements OrgAuthenticationConfigManagementApi {

  TenantQueryRepository tenantQueryRepository;
  OrganizationRepository organizationRepository;
  AuthenticationConfigurationCommandRepository authenticationConfigurationCommandRepository;
  AuthenticationConfigurationQueryRepository authenticationConfigurationQueryRepository;
  AuditLogPublisher auditLogPublisher;
  OrganizationAccessVerifier organizationAccessVerifier;

  LoggerWrapper log = LoggerWrapper.getLogger(OrgAuthenticationConfigManagementEntryService.class);

  /**
   * Creates a new organization authentication configuration management entry service.
   *
   * @param tenantQueryRepository the tenant query repository
   * @param organizationRepository the organization repository
   * @param authenticationConfigurationCommandRepository the authentication configuration command
   *     repository
   * @param authenticationConfigurationQueryRepository the authentication configuration query
   *     repository
   * @param auditLogPublisher the audit log publisher
   */
  public OrgAuthenticationConfigManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      AuthenticationConfigurationCommandRepository authenticationConfigurationCommandRepository,
      AuthenticationConfigurationQueryRepository authenticationConfigurationQueryRepository,
      AuditLogPublisher auditLogPublisher) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.authenticationConfigurationCommandRepository =
        authenticationConfigurationCommandRepository;
    this.authenticationConfigurationQueryRepository = authenticationConfigurationQueryRepository;
    this.auditLogPublisher = auditLogPublisher;
    this.organizationAccessVerifier = new OrganizationAccessVerifier();
  }

  @Override
  public AuthenticationConfigManagementResponse create(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier adminTenant,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("create");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(adminTenant);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(organization, adminTenant, operator, permissions);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new AuthenticationConfigManagementResponse(
          AuthenticationConfigManagementStatus.FORBIDDEN, response);
    }

    // Create context using the existing Context Creator pattern
    AuthenticationConfigRegistrationContextCreator contextCreator =
        new AuthenticationConfigRegistrationContextCreator(targetTenant, request, dryRun);
    AuthenticationConfigRegistrationContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.create(
            "OrgAuthenticationConfigManagementApi.create",
            targetTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (context.isDryRun()) {
      return context.toResponse();
    }

    authenticationConfigurationCommandRepository.register(targetTenant, context.configuration());

    return context.toResponse();
  }

  @Transaction(readOnly = true)
  public AuthenticationConfigManagementResponse findList(
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
            "OrgAuthenticationConfigManagementApi.findList",
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
      return new AuthenticationConfigManagementResponse(
          AuthenticationConfigManagementStatus.FORBIDDEN, response);
    }

    long totalCount = authenticationConfigurationQueryRepository.findTotalCount(targetTenant);
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", limit);
      response.put("offset", offset);
      return new AuthenticationConfigManagementResponse(
          AuthenticationConfigManagementStatus.OK, response);
    }

    List<AuthenticationConfiguration> configurations =
        authenticationConfigurationQueryRepository.findList(targetTenant, limit, offset);

    Map<String, Object> response = new HashMap<>();
    response.put("list", configurations.stream().map(AuthenticationConfiguration::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", limit);
    response.put("offset", offset);

    return new AuthenticationConfigManagementResponse(
        AuthenticationConfigManagementStatus.OK, response);
  }

  @Override
  @Transaction(readOnly = true)
  public AuthenticationConfigManagementResponse get(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("get");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    AuthenticationConfiguration configuration =
        authenticationConfigurationQueryRepository.findWithDisabled(targetTenant, identifier, true);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgAuthenticationConfigManagementApi.get",
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
      return new AuthenticationConfigManagementResponse(
          AuthenticationConfigManagementStatus.FORBIDDEN, response);
    }

    if (!configuration.exists()) {
      return new AuthenticationConfigManagementResponse(
          AuthenticationConfigManagementStatus.NOT_FOUND, Map.of());
    }

    return new AuthenticationConfigManagementResponse(
        AuthenticationConfigManagementStatus.OK, configuration.toMap());
  }

  @Override
  public AuthenticationConfigManagementResponse update(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationConfigurationIdentifier identifier,
      AuthenticationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("update");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    AuthenticationConfiguration before =
        authenticationConfigurationQueryRepository.findWithDisabled(targetTenant, identifier, true);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new AuthenticationConfigManagementResponse(
          AuthenticationConfigManagementStatus.FORBIDDEN, response);
    }

    if (!before.exists()) {
      return new AuthenticationConfigManagementResponse(
          AuthenticationConfigManagementStatus.NOT_FOUND, Map.of());
    }

    // Create context using the existing Context Creator pattern
    AuthenticationConfigUpdateContextCreator contextCreator =
        new AuthenticationConfigUpdateContextCreator(targetTenant, before, request, dryRun);
    AuthenticationConfigUpdateContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "OrgAuthenticationConfigManagementApi.update",
            targetTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (context.isDryRun()) {
      return context.toResponse();
    }

    authenticationConfigurationCommandRepository.update(targetTenant, context.after());

    return context.toResponse();
  }

  @Override
  public AuthenticationConfigManagementResponse delete(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("delete");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    AuthenticationConfiguration configuration =
        authenticationConfigurationQueryRepository.findWithDisabled(targetTenant, identifier, true);

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "OrgAuthenticationConfigManagementApi.delete",
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
      return new AuthenticationConfigManagementResponse(
          AuthenticationConfigManagementStatus.FORBIDDEN, response);
    }

    if (!configuration.exists()) {
      return new AuthenticationConfigManagementResponse(
          AuthenticationConfigManagementStatus.NOT_FOUND, Map.of());
    }

    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put("message", "Authentication configuration deletion simulated successfully");
      response.put("config_id", configuration.identifier().value());
      response.put("dry_run", true);
      return new AuthenticationConfigManagementResponse(
          AuthenticationConfigManagementStatus.OK, response);
    }

    authenticationConfigurationCommandRepository.delete(targetTenant, configuration);

    return new AuthenticationConfigManagementResponse(
        AuthenticationConfigManagementStatus.NO_CONTENT, Map.of());
  }
}

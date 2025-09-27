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
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.oidc.authorization.AuthorizationServerUpdateContext;
import org.idp.server.control_plane.management.oidc.authorization.AuthorizationServerUpdateContextCreator;
import org.idp.server.control_plane.management.oidc.authorization.OrgAuthorizationServerManagementApi;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerManagementResponse;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerManagementStatus;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerUpdateRequest;
import org.idp.server.control_plane.management.oidc.authorization.validator.AuthorizationServerRequestValidationResult;
import org.idp.server.control_plane.management.oidc.authorization.validator.AuthorizationServerRequestValidator;
import org.idp.server.control_plane.organization.access.OrganizationAccessControlResult;
import org.idp.server.control_plane.organization.access.OrganizationAccessVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
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
 * Organization-level authorization server management entry service.
 *
 * <p>This service implements organization-scoped authorization server configuration management
 * operations that allow organization administrators to manage authorization server settings within
 * their organization boundaries.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       TENANT_READ/TENANT_UPDATE permissions
 * </ol>
 *
 * <p>This service provides authorization server configuration retrieval and update operations with
 * comprehensive audit logging for organization-level authorization server management.
 *
 * @see OrgAuthorizationServerManagementApi
 * @see OrganizationAccessVerifier
 * @see
 *     org.idp.server.usecases.control_plane.system_manager.AuthorizationServerManagementEntryService
 */
@Transaction
public class OrgAuthorizationServerManagementEntryService
    implements OrgAuthorizationServerManagementApi {

  TenantQueryRepository tenantQueryRepository;
  OrganizationRepository organizationRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  AuthorizationServerConfigurationCommandRepository
      authorizationServerConfigurationCommandRepository;
  AuditLogPublisher auditLogPublisher;
  OrganizationAccessVerifier organizationAccessVerifier;

  LoggerWrapper log = LoggerWrapper.getLogger(OrgAuthorizationServerManagementEntryService.class);

  /**
   * Creates a new organization authorization server management entry service.
   *
   * @param tenantQueryRepository the tenant query repository
   * @param organizationRepository the organization repository
   * @param authorizationServerConfigurationQueryRepository the authorization server configuration
   *     query repository
   * @param authorizationServerConfigurationCommandRepository the authorization server configuration
   *     command repository
   * @param auditLogPublisher the audit log publisher
   */
  public OrgAuthorizationServerManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      AuthorizationServerConfigurationCommandRepository
          authorizationServerConfigurationCommandRepository,
      AuditLogPublisher auditLogPublisher) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.authorizationServerConfigurationCommandRepository =
        authorizationServerConfigurationCommandRepository;
    this.auditLogPublisher = auditLogPublisher;
    this.organizationAccessVerifier = new OrganizationAccessVerifier();
  }

  @Override
  @Transaction(readOnly = true)
  public AuthorizationServerManagementResponse get(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes) {
    AdminPermissions permissions = getRequiredPermissions("get");

    // Organization access verification
    Organization organization = organizationRepository.get(organizationIdentifier);
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    if (!accessResult.isSuccess()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "access_denied");
      errorResponse.put("error_description", accessResult.getReason());
      return new AuthorizationServerManagementResponse(
          AuthorizationServerManagementStatus.FORBIDDEN, errorResponse);
    }

    // Use existing system-level logic with target tenant
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.getWithDisabled(targetTenant, true);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgAuthorizationServerManagementApi.get",
            "get",
            targetTenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return new AuthorizationServerManagementResponse(
        AuthorizationServerManagementStatus.OK, authorizationServerConfiguration.toMap());
  }

  @Override
  public AuthorizationServerManagementResponse update(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthorizationServerUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    AdminPermissions permissions = getRequiredPermissions("update");

    // Organization access verification
    Organization organization = organizationRepository.get(organizationIdentifier);
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    if (!accessResult.isSuccess()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "access_denied");
      errorResponse.put("error_description", accessResult.getReason());
      return new AuthorizationServerManagementResponse(
          AuthorizationServerManagementStatus.FORBIDDEN, errorResponse);
    }

    // Use existing system-level logic with target tenant
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);
    AuthorizationServerConfiguration before =
        authorizationServerConfigurationQueryRepository.getWithDisabled(targetTenant, true);

    AuthorizationServerRequestValidator validator =
        new AuthorizationServerRequestValidator(request, dryRun);
    AuthorizationServerRequestValidationResult validate = validator.validate();

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    AuthorizationServerUpdateContextCreator contextCreator =
        new AuthorizationServerUpdateContextCreator(targetTenant, before, request, dryRun);
    AuthorizationServerUpdateContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "OrgAuthorizationServerManagementApi.update",
            targetTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (dryRun) {
      return context.toResponse();
    }

    authorizationServerConfigurationCommandRepository.update(targetTenant, context.after());

    return context.toResponse();
  }
}

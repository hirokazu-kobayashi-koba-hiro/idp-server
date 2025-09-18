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
import org.idp.server.control_plane.management.security.hook.*;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementResponse;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementStatus;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookRequest;
import org.idp.server.control_plane.management.security.hook.validator.SecurityEventConfigRequestValidationResult;
import org.idp.server.control_plane.management.security.hook.validator.SecurityEventHookRequestValidator;
import org.idp.server.control_plane.organization.access.OrganizationAccessControlResult;
import org.idp.server.control_plane.organization.access.OrganizationAccessVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogWriters;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfigurationIdentifier;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationCommandRepository;
import org.idp.server.platform.security.repository.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level security event hook configuration management entry service.
 *
 * <p>This service implements organization-scoped security event hook configuration management
 * operations that allow organization administrators to manage security event hook configurations
 * within their organization boundaries.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       SECURITY_EVENT_HOOK_CONFIG_* permissions
 * </ol>
 *
 * <p>All operations support dry-run functionality for safe preview of changes and comprehensive
 * audit logging for organization-level security event hook configuration operations.
 *
 * @see OrgSecurityEventHookConfigManagementApi
 * @see OrganizationAccessVerifier
 * @see
 *     org.idp.server.usecases.control_plane.system_manager.SecurityEventHookConfigurationManagementEntryService
 */
@Transaction
public class OrgSecurityEventHookConfigManagementEntryService
    implements OrgSecurityEventHookConfigManagementApi {

  TenantQueryRepository tenantQueryRepository;
  OrganizationRepository organizationRepository;
  SecurityEventHookConfigurationCommandRepository securityEventHookConfigurationCommandRepository;
  SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository;
  AuditLogWriters auditLogWriters;
  OrganizationAccessVerifier organizationAccessVerifier;

  LoggerWrapper log =
      LoggerWrapper.getLogger(OrgSecurityEventHookConfigManagementEntryService.class);

  /**
   * Creates a new organization security event hook configuration management entry service.
   *
   * @param tenantQueryRepository the tenant query repository
   * @param organizationRepository the organization repository
   * @param securityEventHookConfigurationCommandRepository the security event hook configuration
   *     command repository
   * @param securityEventHookConfigurationQueryRepository the security event hook configuration
   *     query repository
   * @param auditLogWriters the audit log writers
   */
  public OrgSecurityEventHookConfigManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      SecurityEventHookConfigurationCommandRepository
          securityEventHookConfigurationCommandRepository,
      SecurityEventHookConfigurationQueryRepository securityEventHookConfigurationQueryRepository,
      AuditLogWriters auditLogWriters) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.securityEventHookConfigurationCommandRepository =
        securityEventHookConfigurationCommandRepository;
    this.securityEventHookConfigurationQueryRepository =
        securityEventHookConfigurationQueryRepository;
    this.auditLogWriters = auditLogWriters;
    this.organizationAccessVerifier = new OrganizationAccessVerifier();
  }

  @Override
  public SecurityEventHookConfigManagementResponse create(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    AdminPermissions permissions = getRequiredPermissions("create");

    // Organization access verification
    Organization organization = organizationRepository.get(organizationIdentifier);
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    if (!accessResult.isSuccess()) {
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("error", "access_denied");
      errorResponse.put("error_description", accessResult.getReason());
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.FORBIDDEN, errorResponse);
    }

    // Use existing system-level logic with target tenant
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    SecurityEventHookRequestValidator validator =
        new SecurityEventHookRequestValidator(request, dryRun);
    SecurityEventConfigRequestValidationResult validate = validator.validate();
    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    SecurityEventHookConfigRegistrationContextCreator contextCreator =
        new SecurityEventHookConfigRegistrationContextCreator(targetTenant, request, dryRun);
    SecurityEventHookConfigRegistrationContext context = contextCreator.create();

    // Create audit log for organization-level operation
    AuditLog auditLog =
        AuditLogCreator.create(
            "OrgSecurityEventHookConfigManagementApi.create",
            targetTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogWriters.write(targetTenant, auditLog);

    if (context.isDryRun()) {
      return context.toResponse();
    }

    securityEventHookConfigurationCommandRepository.register(targetTenant, context.configuration());

    return context.toResponse();
  }

  @Override
  public SecurityEventHookConfigManagementResponse findList(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {
    AdminPermissions permissions = getRequiredPermissions("findList");

    // Organization access verification
    Organization organization = organizationRepository.get(organizationIdentifier);
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgSecurityEventHookConfigManagementApi.findList",
            "findList",
            targetTenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogWriters.write(targetTenant, auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.FORBIDDEN, response);
    }

    long totalCount = securityEventHookConfigurationQueryRepository.findTotalCount(targetTenant);
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", limit);
      response.put("offset", offset);
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.OK, response);
    }

    List<SecurityEventHookConfiguration> configurations =
        securityEventHookConfigurationQueryRepository.findList(targetTenant, limit, offset);

    Map<String, Object> response = new HashMap<>();
    response.put(
        "list", configurations.stream().map(SecurityEventHookConfiguration::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", limit);
    response.put("offset", offset);

    return new SecurityEventHookConfigManagementResponse(
        SecurityEventHookConfigManagementStatus.OK, response);
  }

  @Override
  public SecurityEventHookConfigManagementResponse get(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookConfigurationIdentifier identifier,
      RequestAttributes requestAttributes) {
    AdminPermissions permissions = getRequiredPermissions("get");

    // Organization access verification
    Organization organization = organizationRepository.get(organizationIdentifier);
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    SecurityEventHookConfiguration configuration =
        securityEventHookConfigurationQueryRepository.find(targetTenant, identifier);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgSecurityEventHookConfigManagementApi.get",
            "get",
            targetTenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogWriters.write(targetTenant, auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.FORBIDDEN, response);
    }

    if (!configuration.exists()) {
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.NOT_FOUND, Map.of());
    }

    return new SecurityEventHookConfigManagementResponse(
        SecurityEventHookConfigManagementStatus.OK, configuration.toMap());
  }

  @Override
  public SecurityEventHookConfigManagementResponse update(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookConfigurationIdentifier identifier,
      SecurityEventHookRequest request,
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
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.FORBIDDEN, errorResponse);
    }

    // Use existing system-level logic with target tenant
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);
    SecurityEventHookConfiguration before =
        securityEventHookConfigurationQueryRepository.find(targetTenant, identifier);

    SecurityEventHookConfigUpdateContextCreator contextCreator =
        new SecurityEventHookConfigUpdateContextCreator(
            targetTenant, before, identifier, request, dryRun);
    SecurityEventHookConfigUpdateContext context = contextCreator.create();

    // Create audit log for organization-level operation
    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "OrgSecurityEventHookConfigManagementApi.update",
            targetTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogWriters.write(targetTenant, auditLog);

    if (!before.exists()) {
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.NOT_FOUND, Map.of());
    }

    if (context.isDryRun()) {
      return context.toResponse();
    }

    securityEventHookConfigurationCommandRepository.update(targetTenant, context.after());

    return context.toResponse();
  }

  @Override
  public SecurityEventHookConfigManagementResponse delete(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    AdminPermissions permissions = getRequiredPermissions("delete");

    // Organization access verification
    Organization organization = organizationRepository.get(organizationIdentifier);
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);
    SecurityEventHookConfiguration configuration =
        securityEventHookConfigurationQueryRepository.find(targetTenant, identifier);

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "OrgSecurityEventHookConfigManagementApi.delete",
            "delete",
            targetTenant,
            operator,
            oAuthToken,
            configuration.toMap(),
            requestAttributes);
    auditLogWriters.write(targetTenant, auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.FORBIDDEN, response);
    }

    if (!configuration.exists()) {
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.NOT_FOUND, Map.of());
    }

    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put("message", "Security event hook configuration deletion simulated successfully");
      response.put("config_id", configuration.identifier().value());
      response.put("dry_run", true);
      return new SecurityEventHookConfigManagementResponse(
          SecurityEventHookConfigManagementStatus.OK, response);
    }

    securityEventHookConfigurationCommandRepository.delete(targetTenant, configuration);

    return new SecurityEventHookConfigManagementResponse(
        SecurityEventHookConfigManagementStatus.NO_CONTENT, Map.of());
  }
}

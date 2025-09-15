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
import org.idp.server.control_plane.management.oidc.client.*;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementResponse;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementStatus;
import org.idp.server.control_plane.management.oidc.client.io.ClientRegistrationRequest;
import org.idp.server.control_plane.management.oidc.client.validator.ClientRegistrationRequestValidationResult;
import org.idp.server.control_plane.management.oidc.client.validator.ClientRegistrationRequestValidator;
import org.idp.server.control_plane.organization.access.OrganizationAccessControlResult;
import org.idp.server.control_plane.organization.access.OrganizationAccessVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationCommandRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.configuration.client.ClientIdentifier;
import org.idp.server.core.openid.oauth.configuration.client.ClientQueries;
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
import org.idp.server.platform.type.RequestAttributes;

/**
 * Organization-level client management entry service.
 *
 * <p>This service implements organization-scoped OIDC client management operations that allow
 * organization administrators to manage OAuth/OIDC clients within their organization boundaries.
 *
 * <p>Organization-level operations follow the standard access control pattern:
 *
 * <ol>
 *   <li><strong>Organization access verification</strong> - Ensures the user has access to the
 *       organization
 *   <li><strong>Permission verification</strong> - Validates the user has necessary
 *       DefaultAdminPermission
 * </ol>
 *
 * <p>All operations support dry-run functionality for safe preview of changes and comprehensive
 * audit logging for organization-level client operations.
 *
 * @see OrgClientManagementApi
 * @see OrganizationAccessVerifier
 * @see org.idp.server.usecases.control_plane.system_manager.ClientManagementEntryService
 */
@Transaction
public class OrgClientManagementEntryService implements OrgClientManagementApi {

  TenantQueryRepository tenantQueryRepository;
  OrganizationRepository organizationRepository;
  ClientConfigurationCommandRepository clientConfigurationCommandRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  AuditLogWriters auditLogWriters;
  OrganizationAccessVerifier organizationAccessVerifier;

  LoggerWrapper log = LoggerWrapper.getLogger(OrgClientManagementEntryService.class);

  /**
   * Creates a new organization client management entry service.
   *
   * @param tenantQueryRepository the tenant query repository
   * @param organizationRepository the organization repository
   * @param clientConfigurationCommandRepository the client configuration command repository
   * @param clientConfigurationQueryRepository the client configuration query repository
   * @param auditLogWriters the audit log writers
   */
  public OrgClientManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      ClientConfigurationCommandRepository clientConfigurationCommandRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      AuditLogWriters auditLogWriters) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.clientConfigurationCommandRepository = clientConfigurationCommandRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.auditLogWriters = auditLogWriters;
    this.organizationAccessVerifier = new OrganizationAccessVerifier();
  }

  @Override
  public ClientManagementResponse create(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("create");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    ClientRegistrationContextCreator contextCreator =
        new ClientRegistrationContextCreator(targetTenant, request, dryRun);
    ClientRegistrationContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.create(
            "OrgClientManagementApi.create",
            targetTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogWriters.write(targetTenant, auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new ClientManagementResponse(ClientManagementStatus.FORBIDDEN, response);
    }

    ClientRegistrationRequestValidator validator =
        new ClientRegistrationRequestValidator(request, dryRun);
    ClientRegistrationRequestValidationResult validate = validator.validate();

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    if (dryRun) {
      return context.toResponse();
    }

    clientConfigurationCommandRepository.register(targetTenant, context.configuration());

    return context.toResponse();
  }

  @Override
  public ClientManagementResponse findList(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientQueries queries,
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
            "OrgClientManagementApi.findList",
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
      return new ClientManagementResponse(ClientManagementStatus.FORBIDDEN, response);
    }

    long totalCount = clientConfigurationQueryRepository.findTotalCount(targetTenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", totalCount);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());
      return new ClientManagementResponse(ClientManagementStatus.OK, response);
    }

    List<ClientConfiguration> clientConfigurations =
        clientConfigurationQueryRepository.findList(targetTenant, queries);

    Map<String, Object> response = new HashMap<>();
    response.put("list", clientConfigurations.stream().map(ClientConfiguration::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());

    return new ClientManagementResponse(ClientManagementStatus.OK, response);
  }

  @Override
  public ClientManagementResponse get(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientIdentifier clientIdentifier,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("get");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.findWithDisabled(targetTenant, clientIdentifier, true);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "OrgClientManagementApi.get",
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
      return new ClientManagementResponse(ClientManagementStatus.FORBIDDEN, response);
    }

    if (!clientConfiguration.exists()) {
      return new ClientManagementResponse(ClientManagementStatus.NOT_FOUND, Map.of());
    }

    return new ClientManagementResponse(ClientManagementStatus.OK, clientConfiguration.toMap());
  }

  @Override
  public ClientManagementResponse update(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientIdentifier clientIdentifier,
      ClientRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("update");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    ClientConfiguration before =
        clientConfigurationQueryRepository.findWithDisabled(targetTenant, clientIdentifier, true);

    ClientRegistrationRequestValidator validator =
        new ClientRegistrationRequestValidator(request, dryRun);
    ClientRegistrationRequestValidationResult validate = validator.validate();

    ClientUpdateContextCreator contextCreator =
        new ClientUpdateContextCreator(targetTenant, before, request, dryRun);
    ClientUpdateContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "OrgClientManagementApi.update",
            targetTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogWriters.write(targetTenant, auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new ClientManagementResponse(ClientManagementStatus.FORBIDDEN, response);
    }

    if (!before.exists()) {
      return new ClientManagementResponse(ClientManagementStatus.NOT_FOUND, Map.of());
    }

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    if (dryRun) {
      return context.toResponse();
    }

    clientConfigurationCommandRepository.update(targetTenant, context.after());

    return context.toResponse();
  }

  @Override
  public ClientManagementResponse delete(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientIdentifier clientIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("delete");

    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.findWithDisabled(targetTenant, clientIdentifier, true);

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "OrgClientManagementApi.delete",
            "delete",
            targetTenant,
            operator,
            oAuthToken,
            clientConfiguration.toMap(),
            requestAttributes);
    auditLogWriters.write(targetTenant, auditLog);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessResult.getReason());
      return new ClientManagementResponse(ClientManagementStatus.FORBIDDEN, response);
    }

    if (!clientConfiguration.exists()) {
      return new ClientManagementResponse(ClientManagementStatus.NOT_FOUND, Map.of());
    }

    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put("message", "Client deletion simulated successfully");
      response.put("client_id", clientConfiguration.clientIdValue());
      return new ClientManagementResponse(ClientManagementStatus.OK, response);
    }

    clientConfigurationCommandRepository.delete(targetTenant, clientConfiguration);

    return new ClientManagementResponse(ClientManagementStatus.NO_CONTENT, Map.of());
  }
}

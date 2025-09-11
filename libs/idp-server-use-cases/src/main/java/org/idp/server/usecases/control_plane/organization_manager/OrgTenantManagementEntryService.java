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
import org.idp.server.control_plane.base.definition.OrganizationAdminPermissions;
import org.idp.server.control_plane.base.verifier.TenantVerifier;
import org.idp.server.control_plane.management.tenant.*;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantManagementStatus;
import org.idp.server.control_plane.management.tenant.io.TenantRequest;
import org.idp.server.control_plane.management.tenant.verifier.TenantManagementVerificationResult;
import org.idp.server.control_plane.management.tenant.verifier.TenantManagementVerifier;
import org.idp.server.control_plane.organization.access.OrganizationAccessControlResult;
import org.idp.server.control_plane.organization.access.OrganizationAccessVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogWriters;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class OrgTenantManagementEntryService implements OrgTenantManagementApi {

  TenantCommandRepository tenantCommandRepository;
  TenantQueryRepository tenantQueryRepository;
  OrganizationRepository organizationRepository;
  AuthorizationServerConfigurationCommandRepository
      authorizationServerConfigurationCommandRepository;
  TenantManagementVerifier tenantManagementVerifier;
  UserCommandRepository userCommandRepository;
  AuditLogWriters auditLogWriters;
  OrganizationAccessVerifier organizationAccessVerifier;

  LoggerWrapper log = LoggerWrapper.getLogger(OrgTenantManagementEntryService.class);

  public OrgTenantManagementEntryService(
      TenantCommandRepository tenantCommandRepository,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      AuthorizationServerConfigurationCommandRepository
          authorizationServerConfigurationCommandRepository,
      UserCommandRepository userCommandRepository,
      AuditLogWriters auditLogWriters) {
    this.tenantCommandRepository = tenantCommandRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.authorizationServerConfigurationCommandRepository =
        authorizationServerConfigurationCommandRepository;
    this.userCommandRepository = userCommandRepository;
    TenantVerifier tenantVerifier = new TenantVerifier(tenantQueryRepository);
    this.tenantManagementVerifier = new TenantManagementVerifier(tenantVerifier);
    this.auditLogWriters = auditLogWriters;
    this.organizationAccessVerifier = new OrganizationAccessVerifier(organizationRepository);
  }

  @Override
  public TenantManagementResponse create(
      OrganizationIdentifier organizationId,
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    OrganizationAdminPermissions permissions = getRequiredPermissions("create");
    Tenant adminTenant = tenantQueryRepository.get(adminTenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organizationId, adminTenantIdentifier, operator, permissions, adminTenant);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put(
          "error", accessResult.isForbidden() ? "access_denied" : "organization_not_found");
      response.put("error_description", accessResult.getReason());
      log.warn(response.toString());
      return new TenantManagementResponse(
          accessResult.isForbidden()
              ? TenantManagementStatus.FORBIDDEN
              : TenantManagementStatus.NOT_FOUND,
          response);
    }

    Organization organization = organizationRepository.get(adminTenant, organizationId);

    TenantManagementRegistrationContextCreator contextCreator =
        new TenantManagementRegistrationContextCreator(
            adminTenant, request, organization, operator, dryRun);
    TenantManagementRegistrationContext context = contextCreator.create();

    TenantManagementVerificationResult verificationResult =
        tenantManagementVerifier.verify(context);

    AuditLog auditLog =
        AuditLogCreator.create(
            "OrganizationTenantManagementApi.create",
            adminTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogWriters.write(adminTenant, auditLog);

    if (!verificationResult.isValid()) {
      return verificationResult.errorResponse();
    }

    if (dryRun) {
      return context.toResponse();
    }

    tenantCommandRepository.register(context.newTenant());
    organizationRepository.update(adminTenant, context.organization());
    authorizationServerConfigurationCommandRepository.register(
        context.newTenant(), context.authorizationServerConfiguration());
    userCommandRepository.update(adminTenant, context.user());

    return context.toResponse();
  }

  @Override
  public TenantManagementResponse findList(
      OrganizationIdentifier organizationId,
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {
    OrganizationAdminPermissions permissions = getRequiredPermissions("findList");
    Tenant adminTenant = tenantQueryRepository.get(adminTenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organizationId, adminTenantIdentifier, operator, permissions, adminTenant);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put(
          "error", accessResult.isForbidden() ? "access_denied" : "organization_not_found");
      response.put("error_description", accessResult.getReason());
      log.warn(response.toString());
      return new TenantManagementResponse(
          accessResult.isForbidden()
              ? TenantManagementStatus.FORBIDDEN
              : TenantManagementStatus.NOT_FOUND,
          response);
    }

    // Get organization to find assigned tenants
    Organization organization = organizationRepository.get(adminTenant, organizationId);
    List<TenantIdentifier> organizationTenantIds =
        organization.assignedTenants().tenantIdentifiers();

    List<Tenant> tenants = tenantQueryRepository.findList(organizationTenantIds);
    Map<String, Object> response = new HashMap<>();
    response.put("list", tenants.stream().map(Tenant::toMap).toList());

    return new TenantManagementResponse(TenantManagementStatus.OK, response);
  }

  @Override
  public TenantManagementResponse get(
      OrganizationIdentifier organizationId,
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantIdentifier tenantIdentifier,
      RequestAttributes requestAttributes) {
    OrganizationAdminPermissions permissions = getRequiredPermissions("get");
    Tenant adminTenant = tenantQueryRepository.get(adminTenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organizationId, tenantIdentifier, operator, permissions, adminTenant);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put(
          "error", accessResult.isForbidden() ? "access_denied" : "organization_not_found");
      response.put("error_description", accessResult.getReason());
      log.warn(response.toString());
      return new TenantManagementResponse(
          accessResult.isForbidden()
              ? TenantManagementStatus.FORBIDDEN
              : TenantManagementStatus.NOT_FOUND,
          response);
    }

    Tenant tenant = tenantQueryRepository.find(tenantIdentifier);

    if (!tenant.exists()) {
      return new TenantManagementResponse(TenantManagementStatus.NOT_FOUND, Map.of());
    }

    return new TenantManagementResponse(TenantManagementStatus.OK, tenant.toMap());
  }

  @Override
  public TenantManagementResponse update(
      OrganizationIdentifier organizationId,
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantIdentifier tenantIdentifier,
      TenantRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    OrganizationAdminPermissions permissions = getRequiredPermissions("update");
    Tenant adminTenant = tenantQueryRepository.get(adminTenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organizationId, tenantIdentifier, operator, permissions, adminTenant);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put(
          "error", accessResult.isForbidden() ? "access_denied" : "organization_not_found");
      response.put("error_description", accessResult.getReason());
      log.warn(response.toString());
      return new TenantManagementResponse(
          accessResult.isForbidden()
              ? TenantManagementStatus.FORBIDDEN
              : TenantManagementStatus.NOT_FOUND,
          response);
    }

    Tenant before = tenantQueryRepository.find(tenantIdentifier);

    TenantManagementUpdateContextCreator contextCreator =
        new TenantManagementUpdateContextCreator(adminTenant, before, request, operator, dryRun);
    TenantManagementUpdateContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "OrganizationTenantManagementApi.update",
            adminTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogWriters.write(adminTenant, auditLog);

    if (!before.exists()) {
      return new TenantManagementResponse(TenantManagementStatus.NOT_FOUND, Map.of());
    }

    if (dryRun) {
      return context.toResponse();
    }

    tenantCommandRepository.update(context.after());

    return context.toResponse();
  }

  @Override
  public TenantManagementResponse delete(
      OrganizationIdentifier organizationId,
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantIdentifier tenantIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    OrganizationAdminPermissions permissions = getRequiredPermissions("delete");
    Tenant adminTenant = tenantQueryRepository.get(adminTenantIdentifier);

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organizationId, tenantIdentifier, operator, permissions, adminTenant);

    if (!accessResult.isSuccess()) {
      Map<String, Object> response = new HashMap<>();
      response.put(
          "error", accessResult.isForbidden() ? "access_denied" : "organization_not_found");
      response.put("error_description", accessResult.getReason());
      log.warn(response.toString());
      return new TenantManagementResponse(
          accessResult.isForbidden()
              ? TenantManagementStatus.FORBIDDEN
              : TenantManagementStatus.NOT_FOUND,
          response);
    }

    Tenant before = tenantQueryRepository.find(tenantIdentifier);

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "OrganizationTenantManagementApi.delete",
            "delete",
            adminTenant,
            operator,
            oAuthToken,
            before.toMap(),
            requestAttributes);
    auditLogWriters.write(adminTenant, auditLog);

    if (!before.exists()) {
      return new TenantManagementResponse(TenantManagementStatus.NOT_FOUND, Map.of());
    }

    return new TenantManagementResponse(TenantManagementStatus.NO_CONTENT, Map.of());
  }
}

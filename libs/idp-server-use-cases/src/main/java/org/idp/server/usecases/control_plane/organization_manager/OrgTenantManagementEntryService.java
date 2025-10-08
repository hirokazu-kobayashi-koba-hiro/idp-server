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
import org.idp.server.control_plane.base.verifier.TenantVerifier;
import org.idp.server.control_plane.management.tenant.*;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantManagementStatus;
import org.idp.server.control_plane.management.tenant.io.TenantRequest;
import org.idp.server.control_plane.management.tenant.validator.TenantRequestValidationResult;
import org.idp.server.control_plane.management.tenant.validator.TenantRequestValidator;
import org.idp.server.control_plane.management.tenant.verifier.TenantManagementVerificationResult;
import org.idp.server.control_plane.management.tenant.verifier.TenantManagementVerifier;
import org.idp.server.control_plane.organization.access.OrganizationAccessControlResult;
import org.idp.server.control_plane.organization.access.OrganizationAccessVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.repository.UserCommandRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
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
  AuditLogPublisher auditLogPublisher;
  OrganizationAccessVerifier organizationAccessVerifier;

  LoggerWrapper log = LoggerWrapper.getLogger(OrgTenantManagementEntryService.class);

  public OrgTenantManagementEntryService(
      TenantCommandRepository tenantCommandRepository,
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      AuthorizationServerConfigurationCommandRepository
          authorizationServerConfigurationCommandRepository,
      UserCommandRepository userCommandRepository,
      AuditLogPublisher auditLogPublisher) {
    this.tenantCommandRepository = tenantCommandRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.authorizationServerConfigurationCommandRepository =
        authorizationServerConfigurationCommandRepository;
    this.userCommandRepository = userCommandRepository;
    TenantVerifier tenantVerifier = new TenantVerifier(tenantQueryRepository);
    this.tenantManagementVerifier = new TenantManagementVerifier(tenantVerifier);
    this.auditLogPublisher = auditLogPublisher;
    this.organizationAccessVerifier = new OrganizationAccessVerifier();
  }

  @Override
  public TenantManagementResponse create(
      OrganizationIdentifier organizationId,
      User operator,
      OAuthToken oAuthToken,
      TenantRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    AdminPermissions permissions = getRequiredPermissions("create");

    Organization organization = organizationRepository.get(organizationId);
    Tenant orgTenant = tenantQueryRepository.get(organization.findOrgTenant().tenantIdentifier());
    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, new TenantIdentifier(), operator, permissions);

    TenantRequestValidator tenantRequestValidator = new TenantRequestValidator(request, dryRun);
    TenantRequestValidationResult validateResult = tenantRequestValidator.validate();

    if (!validateResult.isValid()) {
      return validateResult.errorResponse();
    }

    TenantManagementRegistrationContextCreator contextCreator =
        new TenantManagementRegistrationContextCreator(
            orgTenant, request, organization, operator, dryRun);
    TenantManagementRegistrationContext context = contextCreator.create();

    TenantManagementVerificationResult verificationResult =
        tenantManagementVerifier.verify(context);

    AuditLog auditLog =
        AuditLogCreator.create(
            "OrganizationTenantManagementApi.create",
            orgTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!accessResult.isSuccess()) {
      return accessResult.toErrorResponse();
    }

    if (!verificationResult.isValid()) {
      return verificationResult.errorResponse();
    }

    if (dryRun) {
      return context.toResponse();
    }

    tenantCommandRepository.register(context.newTenant());
    organizationRepository.update(context.organization());
    authorizationServerConfigurationCommandRepository.register(
        context.newTenant(), context.authorizationServerConfiguration());
    userCommandRepository.update(orgTenant, context.user());

    return context.toResponse();
  }

  @Override
  @Transaction(readOnly = true)
  public TenantManagementResponse findList(
      OrganizationIdentifier organizationId,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {
    AdminPermissions permissions = getRequiredPermissions("findList");
    Organization organization = organizationRepository.get(organizationId);
    Tenant orgTenant = tenantQueryRepository.get(organization.findOrgTenant().tenantIdentifier());

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, new TenantIdentifier(), operator, permissions);

    if (!accessResult.isSuccess()) {
      return accessResult.toErrorResponse();
    }

    // Get organization to find assigned tenants
    List<TenantIdentifier> organizationTenantIds =
        organization.assignedTenants().tenantIdentifiers();

    List<Tenant> tenants = tenantQueryRepository.findList(organizationTenantIds);
    Map<String, Object> response = new HashMap<>();
    response.put("list", tenants.stream().map(Tenant::toMap).toList());

    return new TenantManagementResponse(TenantManagementStatus.OK, response);
  }

  @Override
  @Transaction(readOnly = true)
  public TenantManagementResponse get(
      OrganizationIdentifier organizationId,
      User operator,
      OAuthToken oAuthToken,
      TenantIdentifier tenantIdentifier,
      RequestAttributes requestAttributes) {
    AdminPermissions permissions = getRequiredPermissions("get");
    Organization organization = organizationRepository.get(organizationId);
    Tenant orgTenant = tenantQueryRepository.get(organization.findOrgTenant().tenantIdentifier());

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    if (!accessResult.isSuccess()) {
      return accessResult.toErrorResponse();
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
      User operator,
      OAuthToken oAuthToken,
      TenantIdentifier tenantIdentifier,
      TenantRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    AdminPermissions permissions = getRequiredPermissions("update");
    Organization organization = organizationRepository.get(organizationId);
    Tenant orgTenant = tenantQueryRepository.get(organization.findOrgTenant().tenantIdentifier());

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    if (!accessResult.isSuccess()) {
      return accessResult.toErrorResponse();
    }

    Tenant before = tenantQueryRepository.find(tenantIdentifier);

    TenantManagementUpdateContextCreator contextCreator =
        new TenantManagementUpdateContextCreator(orgTenant, before, request, operator, dryRun);
    TenantManagementUpdateContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "OrganizationTenantManagementApi.update",
            orgTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

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
      User operator,
      OAuthToken oAuthToken,
      TenantIdentifier tenantIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    AdminPermissions permissions = getRequiredPermissions("delete");
    Organization organization = organizationRepository.get(organizationId);
    Tenant orgTenant = tenantQueryRepository.get(organization.findOrgTenant().tenantIdentifier());

    // Organization-level access control
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verifyAccess(
            organization, tenantIdentifier, operator, permissions);

    if (!accessResult.isSuccess()) {
      return accessResult.toErrorResponse();
    }

    Tenant before = tenantQueryRepository.find(tenantIdentifier);

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "OrganizationTenantManagementApi.delete",
            "delete",
            orgTenant,
            operator,
            oAuthToken,
            before.toMap(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!before.exists()) {
      return new TenantManagementResponse(TenantManagementStatus.NOT_FOUND, Map.of());
    }

    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put("message", "Deletion simulated successfully");
      response.put("id", tenantIdentifier.value());
      response.put("dry_run", true);
      return new TenantManagementResponse(TenantManagementStatus.OK, response);
    }

    tenantCommandRepository.delete(tenantIdentifier);

    return new TenantManagementResponse(TenantManagementStatus.NO_CONTENT, Map.of());
  }
}

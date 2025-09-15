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
public class TenantManagementEntryService implements TenantManagementApi {

  TenantCommandRepository tenantCommandRepository;
  TenantQueryRepository tenantQueryRepository;
  OrganizationRepository organizationRepository;
  AuthorizationServerConfigurationCommandRepository
      authorizationServerConfigurationCommandRepository;
  TenantManagementVerifier tenantManagementVerifier;
  UserCommandRepository userCommandRepository;
  AuditLogWriters auditLogWriters;

  LoggerWrapper log = LoggerWrapper.getLogger(TenantManagementEntryService.class);

  public TenantManagementEntryService(
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
  }

  @Override
  public TenantManagementResponse create(
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    AdminPermissions permissions = getRequiredPermissions("create");

    Tenant adminTenant = tenantQueryRepository.get(adminTenantIdentifier);
    OrganizationIdentifier organizationIdentifier = operator.currentOrganizationIdentifier();
    Organization organization = organizationRepository.get(organizationIdentifier);

    TenantManagementRegistrationContextCreator contextCreator =
        new TenantManagementRegistrationContextCreator(
            adminTenant, request, organization, operator, dryRun);
    TenantManagementRegistrationContext context = contextCreator.create();

    TenantManagementVerificationResult verificationResult =
        tenantManagementVerifier.verify(context);

    AuditLog auditLog =
        AuditLogCreator.create(
            "TenantManagementApi.create",
            adminTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogWriters.write(adminTenant, auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new TenantManagementResponse(TenantManagementStatus.FORBIDDEN, response);
    }

    TenantRequestValidator tenantRequestValidator = new TenantRequestValidator(request, dryRun);
    TenantRequestValidationResult validateResult = tenantRequestValidator.validate();

    if (!validateResult.isValid()) {
      return validateResult.errorResponse();
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
    userCommandRepository.update(adminTenant, context.user());

    return context.toResponse();
  }

  @Override
  public TenantManagementResponse findList(
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {
    AdminPermissions permissions = getRequiredPermissions("findList");
    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new TenantManagementResponse(TenantManagementStatus.FORBIDDEN, response);
    }

    tenantQueryRepository.get(adminTenantIdentifier);

    List<Tenant> tenants =
        tenantQueryRepository.findList(operator.assignedTenantsAsTenantIdentifiers());
    Map<String, Object> response = new HashMap<>();
    response.put("list", tenants.stream().map(Tenant::toMap).toList());

    return new TenantManagementResponse(TenantManagementStatus.OK, response);
  }

  @Override
  public TenantManagementResponse get(
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantIdentifier tenantIdentifier,
      RequestAttributes requestAttributes) {
    AdminPermissions permissions = getRequiredPermissions("get");
    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new TenantManagementResponse(TenantManagementStatus.FORBIDDEN, response);
    }

    tenantQueryRepository.get(adminTenantIdentifier);

    Tenant tenant = tenantQueryRepository.find(tenantIdentifier);

    if (!tenant.exists()) {
      return new TenantManagementResponse(TenantManagementStatus.NOT_FOUND, Map.of());
    }

    return new TenantManagementResponse(TenantManagementStatus.OK, tenant.toMap());
  }

  @Override
  public TenantManagementResponse update(
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantIdentifier tenantIdentifier,
      TenantRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    AdminPermissions permissions = getRequiredPermissions("update");

    Tenant adminTenant = tenantQueryRepository.get(adminTenantIdentifier);
    Tenant before = tenantQueryRepository.find(tenantIdentifier);

    TenantManagementUpdateContextCreator contextCreator =
        new TenantManagementUpdateContextCreator(adminTenant, before, request, operator, dryRun);
    TenantManagementUpdateContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "TenantManagementApi.update",
            adminTenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogWriters.write(adminTenant, auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new TenantManagementResponse(TenantManagementStatus.FORBIDDEN, response);
    }

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
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantIdentifier tenantIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    AdminPermissions permissions = getRequiredPermissions("delete");

    Tenant adminTenant = tenantQueryRepository.get(adminTenantIdentifier);
    Tenant before = tenantQueryRepository.find(tenantIdentifier);

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "TenantManagementApi.delete",
            "delete",
            adminTenant,
            operator,
            oAuthToken,
            before.toMap(),
            requestAttributes);
    auditLogWriters.write(adminTenant, auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new TenantManagementResponse(TenantManagementStatus.FORBIDDEN, response);
    }

    if (!before.exists()) {
      return new TenantManagementResponse(TenantManagementStatus.NOT_FOUND, Map.of());
    }

    return new TenantManagementResponse(TenantManagementStatus.NO_CONTENT, Map.of());
  }
}

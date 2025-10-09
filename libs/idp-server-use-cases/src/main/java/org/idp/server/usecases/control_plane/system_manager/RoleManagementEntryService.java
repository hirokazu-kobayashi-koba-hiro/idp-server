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
import org.idp.server.control_plane.management.role.*;
import org.idp.server.control_plane.management.role.io.RoleManagementResponse;
import org.idp.server.control_plane.management.role.io.RoleManagementStatus;
import org.idp.server.control_plane.management.role.io.RoleRequest;
import org.idp.server.control_plane.management.role.validator.RoleRemovePermissionsRequestValidator;
import org.idp.server.control_plane.management.role.validator.RoleRequestValidationResult;
import org.idp.server.control_plane.management.role.validator.RoleRequestValidator;
import org.idp.server.control_plane.management.role.verifier.RoleRegistrationVerificationResult;
import org.idp.server.control_plane.management.role.verifier.RoleRegistrationVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.permission.*;
import org.idp.server.core.openid.identity.role.*;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class RoleManagementEntryService implements RoleManagementApi {

  TenantQueryRepository tenantQueryRepository;
  RoleQueryRepository roleQueryRepository;
  RoleCommandRepository roleCommandRepository;
  PermissionQueryRepository permissionQueryRepository;
  AuditLogPublisher auditLogPublisher;
  LoggerWrapper log = LoggerWrapper.getLogger(RoleManagementEntryService.class);

  public RoleManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      RoleQueryRepository roleQueryRepository,
      RoleCommandRepository roleCommandRepository,
      PermissionQueryRepository permissionQueryRepository,
      AuditLogPublisher auditLogPublisher) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.roleQueryRepository = roleQueryRepository;
    this.roleCommandRepository = roleCommandRepository;
    this.permissionQueryRepository = permissionQueryRepository;
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  public RoleManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RoleRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("create");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    RoleRequestValidator validator = new RoleRequestValidator(request, dryRun);
    RoleRequestValidationResult validate = validator.validate();

    Roles roles = roleQueryRepository.findAll(tenant);
    Permissions permissionList = permissionQueryRepository.findAll(tenant);
    RoleRegistrationContextCreator registrationContextCreator =
        new RoleRegistrationContextCreator(tenant, request, roles, permissionList, dryRun);
    RoleRegistrationContext context = registrationContextCreator.create();

    RoleRegistrationVerifier verifier = new RoleRegistrationVerifier();
    RoleRegistrationVerificationResult verificationResult = verifier.verify(context);

    AuditLog auditLog =
        AuditLogCreator.create(
            "RoleManagementApi.create", tenant, operator, oAuthToken, context, requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new RoleManagementResponse(RoleManagementStatus.FORBIDDEN, response);
    }

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    if (!verificationResult.isValid()) {
      return verificationResult.errorResponse();
    }

    if (dryRun) {
      return context.toResponse();
    }

    roleCommandRepository.register(tenant, context.role());

    return context.toResponse();
  }

  @Override
  @Transaction(readOnly = true)
  public RoleManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RoleQueries queries,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("findList");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "RoleManagementApi.findList",
            "findList",
            tenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new RoleManagementResponse(RoleManagementStatus.FORBIDDEN, response);
    }

    long totalCount = roleQueryRepository.findTotalCount(tenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());
      return new RoleManagementResponse(RoleManagementStatus.OK, response);
    }

    List<Role> permissionList = roleQueryRepository.findList(tenant, queries);
    Map<String, Object> response = new HashMap<>();
    response.put("list", permissionList.stream().map(Role::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());

    return new RoleManagementResponse(RoleManagementStatus.OK, response);
  }

  @Override
  @Transaction(readOnly = true)
  public RoleManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RoleIdentifier identifier,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("get");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    Role role = roleQueryRepository.find(tenant, identifier);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "RoleManagementApi.get", "get", tenant, operator, oAuthToken, requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new RoleManagementResponse(RoleManagementStatus.FORBIDDEN, response);
    }

    if (!role.exists()) {
      return new RoleManagementResponse(RoleManagementStatus.NOT_FOUND, Map.of());
    }

    return new RoleManagementResponse(RoleManagementStatus.OK, role.toMap());
  }

  @Override
  public RoleManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RoleIdentifier identifier,
      RoleRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("update");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    Role before = roleQueryRepository.find(tenant, identifier);

    RoleRequestValidator validator = new RoleRequestValidator(request, dryRun);
    RoleRequestValidationResult validate = validator.validate();

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    Roles roles = roleQueryRepository.findAll(tenant);
    Permissions permissionList = permissionQueryRepository.findAll(tenant);
    RoleUpdateContextCreator contextCreator =
        new RoleUpdateContextCreator(tenant, before, request, roles, permissionList, dryRun);
    RoleUpdateContext context = contextCreator.create();

    RoleRegistrationVerifier verifier = new RoleRegistrationVerifier();
    RoleRegistrationVerificationResult verificationResult = verifier.verify(context);

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "RoleManagementApi.update", tenant, operator, oAuthToken, context, requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new RoleManagementResponse(RoleManagementStatus.FORBIDDEN, response);
    }

    if (!before.exists()) {
      return new RoleManagementResponse(RoleManagementStatus.NOT_FOUND, Map.of());
    }

    if (!verificationResult.isValid()) {
      return verificationResult.errorResponse();
    }

    if (context.isDryRun()) {
      return context.toResponse();
    }
    roleCommandRepository.update(tenant, context.after());

    return context.toResponse();
  }

  @Override
  public RoleManagementResponse removePermissions(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RoleIdentifier identifier,
      RoleRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("update");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    Role before = roleQueryRepository.find(tenant, identifier);

    RoleRemovePermissionsRequestValidator validator =
        new RoleRemovePermissionsRequestValidator(request, dryRun);
    RoleRequestValidationResult validate = validator.validate();

    Permissions permissionList = permissionQueryRepository.findAll(tenant);
    RoleRemovePermissionContextCreator updateContextCreator =
        new RoleRemovePermissionContextCreator(tenant, before, request, permissionList, dryRun);
    RoleRemovePermissionContext context = updateContextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "RoleManagementApi.removePermissions",
            tenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new RoleManagementResponse(RoleManagementStatus.FORBIDDEN, response);
    }

    if (!before.exists()) {
      return new RoleManagementResponse(RoleManagementStatus.NOT_FOUND, Map.of());
    }

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    if (context.isDryRun()) {
      return context.toResponse();
    }
    roleCommandRepository.removePermissions(tenant, context.after(), context.removedPermissions());

    return context.toResponse();
  }

  @Override
  public RoleManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RoleIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("delete");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    Role role = roleQueryRepository.find(tenant, identifier);

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "RoleManagementApi.delete",
            "delete",
            tenant,
            operator,
            oAuthToken,
            role.toMap(),
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new RoleManagementResponse(RoleManagementStatus.FORBIDDEN, response);
    }

    if (!role.exists()) {
      return new RoleManagementResponse(RoleManagementStatus.NOT_FOUND, Map.of());
    }

    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put("message", "Deletion simulated successfully");
      response.put("id", role.id());
      response.put("dry_run", true);
      return new RoleManagementResponse(RoleManagementStatus.OK, response);
    }

    roleCommandRepository.delete(tenant, role);

    return new RoleManagementResponse(RoleManagementStatus.NO_CONTENT, Map.of());
  }
}

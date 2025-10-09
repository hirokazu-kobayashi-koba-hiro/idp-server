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
import org.idp.server.control_plane.management.permission.*;
import org.idp.server.control_plane.management.permission.io.PermissionManagementResponse;
import org.idp.server.control_plane.management.permission.io.PermissionManagementStatus;
import org.idp.server.control_plane.management.permission.io.PermissionRequest;
import org.idp.server.control_plane.management.permission.validator.PermissionRequestValidationResult;
import org.idp.server.control_plane.management.permission.validator.PermissionRequestValidator;
import org.idp.server.control_plane.management.permission.validator.PermissionUpdateRequestValidator;
import org.idp.server.control_plane.management.permission.verifier.PermissionRegistrationVerificationResult;
import org.idp.server.control_plane.management.permission.verifier.PermissionRegistrationVerifier;
import org.idp.server.control_plane.management.permission.verifier.PermissionVerifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.permission.*;
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
public class PermissionManagementEntryService implements PermissionManagementApi {

  TenantQueryRepository tenantQueryRepository;
  PermissionQueryRepository permissionQueryRepository;
  PermissionCommandRepository permissionCommandRepository;
  PermissionRegistrationVerifier verifier;
  AuditLogPublisher auditLogPublisher;
  LoggerWrapper log = LoggerWrapper.getLogger(PermissionManagementEntryService.class);

  public PermissionManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      PermissionQueryRepository permissionQueryRepository,
      PermissionCommandRepository permissionCommandRepository,
      AuditLogPublisher auditLogPublisher) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.permissionQueryRepository = permissionQueryRepository;
    this.permissionCommandRepository = permissionCommandRepository;
    PermissionVerifier permissionVerifier = new PermissionVerifier(permissionQueryRepository);
    this.verifier = new PermissionRegistrationVerifier(permissionVerifier);
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  public PermissionManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      PermissionRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("create");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    PermissionRequestValidator validator = new PermissionRequestValidator(request, dryRun);
    PermissionRequestValidationResult validate = validator.validate();

    PermissionRegistrationContextCreator permissionRegistrationContextCreator =
        new PermissionRegistrationContextCreator(tenant, request, dryRun);
    PermissionRegistrationContext context = permissionRegistrationContextCreator.create();

    PermissionRegistrationVerificationResult verificationResult = verifier.verify(context);

    AuditLog auditLog =
        AuditLogCreator.create(
            "PermissionManagementApi.create",
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
      return new PermissionManagementResponse(PermissionManagementStatus.FORBIDDEN, response);
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

    permissionCommandRepository.register(tenant, context.permission());

    return context.toResponse();
  }

  @Override
  @Transaction(readOnly = true)
  public PermissionManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      PermissionQueries queries,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("findList");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "PermissionManagementApi.findList",
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
      return new PermissionManagementResponse(PermissionManagementStatus.FORBIDDEN, response);
    }

    long totalCount = permissionQueryRepository.findTotalCount(tenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());
      return new PermissionManagementResponse(PermissionManagementStatus.OK, response);
    }

    List<Permission> permissionList = permissionQueryRepository.findList(tenant, queries);
    Map<String, Object> response = new HashMap<>();
    response.put("list", permissionList.stream().map(Permission::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());

    return new PermissionManagementResponse(PermissionManagementStatus.OK, response);
  }

  @Override
  @Transaction(readOnly = true)
  public PermissionManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      PermissionIdentifier identifier,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("get");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    Permission permission = permissionQueryRepository.find(tenant, identifier);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "PermissionManagementApi.get", "get", tenant, operator, oAuthToken, requestAttributes);
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
      return new PermissionManagementResponse(PermissionManagementStatus.FORBIDDEN, response);
    }

    if (!permission.exists()) {
      return new PermissionManagementResponse(PermissionManagementStatus.NOT_FOUND, Map.of());
    }

    return new PermissionManagementResponse(PermissionManagementStatus.OK, permission.toMap());
  }

  @Override
  public PermissionManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      PermissionIdentifier identifier,
      PermissionRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("update");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    Permission before = permissionQueryRepository.find(tenant, identifier);

    PermissionUpdateRequestValidator validator =
        new PermissionUpdateRequestValidator(request, dryRun);
    PermissionRequestValidationResult validate = validator.validate();

    PermissionUpdateContextCreator permissionUpdateContextCreator =
        new PermissionUpdateContextCreator(tenant, before, request, dryRun);
    PermissionUpdateContext context = permissionUpdateContextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "PermissionManagementApi.update",
            "update",
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
      return new PermissionManagementResponse(PermissionManagementStatus.FORBIDDEN, response);
    }

    if (!before.exists()) {
      return new PermissionManagementResponse(PermissionManagementStatus.NOT_FOUND, Map.of());
    }

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    if (context.isDryRun()) {
      return context.toResponse();
    }
    permissionCommandRepository.update(tenant, context.after());

    return context.toResponse();
  }

  @Override
  public PermissionManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      PermissionIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("delete");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    Permission permission = permissionQueryRepository.find(tenant, identifier);

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "PermissionManagementApi.delete",
            "delete",
            tenant,
            operator,
            oAuthToken,
            permission.toMap(),
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
      return new PermissionManagementResponse(PermissionManagementStatus.FORBIDDEN, response);
    }

    if (!permission.exists()) {
      return new PermissionManagementResponse(PermissionManagementStatus.NOT_FOUND, Map.of());
    }

    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put("message", "Deletion simulated successfully");
      response.put("id", permission.id());
      response.put("dry_run", true);
      return new PermissionManagementResponse(PermissionManagementStatus.OK, response);
    }

    permissionCommandRepository.delete(tenant, permission);

    return new PermissionManagementResponse(PermissionManagementStatus.NO_CONTENT, Map.of());
  }
}

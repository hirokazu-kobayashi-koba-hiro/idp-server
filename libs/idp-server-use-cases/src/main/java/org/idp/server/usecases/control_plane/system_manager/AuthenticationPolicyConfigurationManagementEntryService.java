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
import org.idp.server.control_plane.management.authentication.policy.*;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigManagementStatus;
import org.idp.server.control_plane.management.authentication.policy.io.AuthenticationPolicyConfigRequest;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantManagementStatus;
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
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class AuthenticationPolicyConfigurationManagementEntryService
    implements AuthenticationPolicyConfigurationManagementApi {

  AuthenticationPolicyConfigurationCommandRepository
      authenticationPolicyConfigurationCommandRepository;
  AuthenticationPolicyConfigurationQueryRepository authenticationPolicyConfigurationQueryRepository;
  TenantQueryRepository tenantQueryRepository;
  AuditLogPublisher auditLogPublisher;
  LoggerWrapper log =
      LoggerWrapper.getLogger(AuthenticationPolicyConfigurationManagementEntryService.class);

  public AuthenticationPolicyConfigurationManagementEntryService(
      AuthenticationPolicyConfigurationCommandRepository
          authenticationPolicyConfigurationCommandRepository,
      AuthenticationPolicyConfigurationQueryRepository
          authenticationPolicyConfigurationQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      AuditLogPublisher auditLogPublisher) {
    this.authenticationPolicyConfigurationCommandRepository =
        authenticationPolicyConfigurationCommandRepository;
    this.authenticationPolicyConfigurationQueryRepository =
        authenticationPolicyConfigurationQueryRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  public AuthenticationPolicyConfigManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationPolicyConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("create");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    AuthenticationPolicyConfigRegistrationContextCreator contextCreator =
        new AuthenticationPolicyConfigRegistrationContextCreator(tenant, request, dryRun);
    AuthenticationPolicyConfigRegistrationContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.create(
            "AuthenticationPolicyConfigurationManagementApi.create",
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
      return new AuthenticationPolicyConfigManagementResponse(
          AuthenticationPolicyConfigManagementStatus.FORBIDDEN, response);
    }

    if (context.isDryRun()) {
      return context.toResponse();
    }

    authenticationPolicyConfigurationCommandRepository.register(tenant, context.configuration());

    return context.toResponse();
  }

  @Override
  @Transaction(readOnly = true)
  public AuthenticationPolicyConfigManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("findList");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    long totalCount = authenticationPolicyConfigurationQueryRepository.findTotalCount(tenant);
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
        authenticationPolicyConfigurationQueryRepository.findList(tenant, limit, offset);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "AuthenticationPolicyConfigurationManagementApi.findList",
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
      return new AuthenticationPolicyConfigManagementResponse(
          AuthenticationPolicyConfigManagementStatus.FORBIDDEN, response);
    }

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
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationPolicyConfigurationIdentifier identifier,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("get");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    AuthenticationPolicyConfiguration configuration =
        authenticationPolicyConfigurationQueryRepository.find(tenant, identifier);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "AuthenticationPolicyConfigurationManagementApi.get",
            "get",
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
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationPolicyConfigurationIdentifier identifier,
      AuthenticationPolicyConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("update");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    AuthenticationPolicyConfiguration before =
        authenticationPolicyConfigurationQueryRepository.find(tenant, identifier);

    AuthenticationPolicyConfigUpdateContextCreator contextCreator =
        new AuthenticationPolicyConfigUpdateContextCreator(tenant, before, request, dryRun);
    AuthenticationPolicyConfigUpdateContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "AuthenticationPolicyConfigurationManagementApi.update",
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
      return new AuthenticationPolicyConfigManagementResponse(
          AuthenticationPolicyConfigManagementStatus.FORBIDDEN, response);
    }

    if (context.isDryRun()) {
      return context.toResponse();
    }

    authenticationPolicyConfigurationCommandRepository.update(tenant, context.after());

    return context.toResponse();
  }

  @Override
  public AuthenticationPolicyConfigManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationPolicyConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("delete");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    AuthenticationPolicyConfiguration configuration =
        authenticationPolicyConfigurationQueryRepository.find(tenant, identifier);

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "AuthenticationPolicyConfigurationManagementApi.delete",
            "delete",
            tenant,
            operator,
            oAuthToken,
            configuration.toMap(),
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
      return new AuthenticationPolicyConfigManagementResponse(
          AuthenticationPolicyConfigManagementStatus.FORBIDDEN, response);
    }

    if (!configuration.exists()) {
      return new AuthenticationPolicyConfigManagementResponse(
          AuthenticationPolicyConfigManagementStatus.NOT_FOUND, Map.of());
    }

    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put(
              "message", "Deletion simulated successfully");
      response.put("id", configuration.id());
      response.put("dry_run", true);
      return new AuthenticationPolicyConfigManagementResponse(
              AuthenticationPolicyConfigManagementStatus.OK, response);
    }

    authenticationPolicyConfigurationCommandRepository.delete(tenant, configuration);

    return new AuthenticationPolicyConfigManagementResponse(
        AuthenticationPolicyConfigManagementStatus.NO_CONTENT, configuration.toMap());
  }
}

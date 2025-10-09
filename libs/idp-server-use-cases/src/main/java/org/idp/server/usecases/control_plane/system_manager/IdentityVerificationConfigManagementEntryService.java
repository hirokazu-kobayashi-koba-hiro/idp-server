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
import org.idp.server.control_plane.management.identity.verification.*;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementResponse;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementStatus;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigRegistrationRequest;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigUpdateRequest;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfigurationIdentifier;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationQueries;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationConfigurationCommandRepository;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationConfigurationQueryRepository;
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
public class IdentityVerificationConfigManagementEntryService
    implements IdentityVerificationConfigManagementApi {

  IdentityVerificationConfigurationCommandRepository
      identityVerificationConfigurationCommandRepository;
  IdentityVerificationConfigurationQueryRepository identityVerificationConfigurationQueryRepository;
  TenantQueryRepository tenantQueryRepository;
  AuditLogPublisher auditLogPublisher;
  LoggerWrapper log =
      LoggerWrapper.getLogger(IdentityVerificationConfigManagementEntryService.class);

  public IdentityVerificationConfigManagementEntryService(
      IdentityVerificationConfigurationCommandRepository
          identityVerificationConfigurationCommandRepository,
      IdentityVerificationConfigurationQueryRepository
          identityVerificationConfigurationQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      AuditLogPublisher auditLogPublisher) {
    this.identityVerificationConfigurationCommandRepository =
        identityVerificationConfigurationCommandRepository;
    this.identityVerificationConfigurationQueryRepository =
        identityVerificationConfigurationQueryRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  public IdentityVerificationConfigManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationConfigRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("create");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    IdentityVerificationConfigRegistrationContextCreator contextCreator =
        new IdentityVerificationConfigRegistrationContextCreator(tenant, request, dryRun);
    IdentityVerificationConfigRegistrationContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.create(
            "IdentityVerificationConfigManagementApi.create",
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
      return new IdentityVerificationConfigManagementResponse(
          IdentityVerificationConfigManagementStatus.FORBIDDEN, response);
    }

    if (dryRun) {
      return context.toResponse();
    }

    identityVerificationConfigurationCommandRepository.register(
        tenant, context.identityVerificationType(), context.configuration());

    return context.toResponse();
  }

  @Override
  @Transaction(readOnly = true)
  public IdentityVerificationConfigManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationQueries queries,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("findList");
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "IdentityVerificationConfigManagementApi.findList",
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
      return new IdentityVerificationConfigManagementResponse(
          IdentityVerificationConfigManagementStatus.FORBIDDEN, response);
    }

    long totalCount =
        identityVerificationConfigurationQueryRepository.findTotalCount(tenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", totalCount);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());
      return new IdentityVerificationConfigManagementResponse(
          IdentityVerificationConfigManagementStatus.OK, response);
    }

    List<IdentityVerificationConfiguration> configurations =
        identityVerificationConfigurationQueryRepository.findList(tenant, queries);

    Map<String, Object> response = new HashMap<>();
    response.put(
        "list", configurations.stream().map(IdentityVerificationConfiguration::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());

    return new IdentityVerificationConfigManagementResponse(
        IdentityVerificationConfigManagementStatus.OK, response);
  }

  @Override
  @Transaction(readOnly = true)
  public IdentityVerificationConfigManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("get");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    IdentityVerificationConfiguration configuration =
        identityVerificationConfigurationQueryRepository.find(tenant, identifier);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "IdentityVerificationConfigManagementApi.get",
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
      return new IdentityVerificationConfigManagementResponse(
          IdentityVerificationConfigManagementStatus.FORBIDDEN, response);
    }

    if (!configuration.exists()) {
      return new IdentityVerificationConfigManagementResponse(
          IdentityVerificationConfigManagementStatus.NOT_FOUND, Map.of());
    }

    return new IdentityVerificationConfigManagementResponse(
        IdentityVerificationConfigManagementStatus.OK, configuration.toMap());
  }

  @Override
  public IdentityVerificationConfigManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationConfigurationIdentifier identifier,
      IdentityVerificationConfigUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("update");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    IdentityVerificationConfiguration configuration =
        identityVerificationConfigurationQueryRepository.find(tenant, identifier);

    IdentityVerificationConfigUpdateContextCreator contextCreator =
        new IdentityVerificationConfigUpdateContextCreator(tenant, request, configuration, dryRun);
    IdentityVerificationConfigUpdateContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "IdentityVerificationConfigManagementApi.update",
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
      return new IdentityVerificationConfigManagementResponse(
          IdentityVerificationConfigManagementStatus.FORBIDDEN, response);
    }

    if (!configuration.exists()) {
      return new IdentityVerificationConfigManagementResponse(
          IdentityVerificationConfigManagementStatus.NOT_FOUND, Map.of());
    }

    if (context.isDryRun()) {
      return context.toResponse();
    }

    identityVerificationConfigurationCommandRepository.update(
        tenant, context.afterType(), context.after());

    return context.toResponse();
  }

  @Override
  public IdentityVerificationConfigManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("delete");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    IdentityVerificationConfiguration configuration =
        identityVerificationConfigurationQueryRepository.find(tenant, identifier);

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "IdentityVerificationConfigManagementApi.delete",
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
      return new IdentityVerificationConfigManagementResponse(
          IdentityVerificationConfigManagementStatus.FORBIDDEN, response);
    }

    if (!configuration.exists()) {
      return new IdentityVerificationConfigManagementResponse(
          IdentityVerificationConfigManagementStatus.NOT_FOUND, Map.of());
    }

    if (dryRun) {
      Map<String, Object> response = new HashMap<>();
      response.put("message", "Deletion simulated successfully");
      response.put("id", configuration.id());
      response.put("dry_run", true);
      return new IdentityVerificationConfigManagementResponse(
          IdentityVerificationConfigManagementStatus.OK, response);
    }

    identityVerificationConfigurationCommandRepository.delete(
        tenant, configuration.type(), configuration);

    return new IdentityVerificationConfigManagementResponse(
        IdentityVerificationConfigManagementStatus.NO_CONTENT, Map.of());
  }
}

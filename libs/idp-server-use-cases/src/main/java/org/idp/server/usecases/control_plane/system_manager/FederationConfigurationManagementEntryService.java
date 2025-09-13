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
import org.idp.server.control_plane.management.federation.*;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementResponse;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementStatus;
import org.idp.server.control_plane.management.federation.io.FederationConfigRequest;
import org.idp.server.core.openid.federation.FederationConfiguration;
import org.idp.server.core.openid.federation.FederationConfigurationIdentifier;
import org.idp.server.core.openid.federation.FederationQueries;
import org.idp.server.core.openid.federation.repository.FederationConfigurationCommandRepository;
import org.idp.server.core.openid.federation.repository.FederationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogWriters;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class FederationConfigurationManagementEntryService
    implements FederationConfigurationManagementApi {

  FederationConfigurationQueryRepository federationConfigurationQueryRepository;
  FederationConfigurationCommandRepository federationConfigurationCommandRepository;
  TenantQueryRepository tenantQueryRepository;
  AuditLogWriters auditLogWriters;
  LoggerWrapper log = LoggerWrapper.getLogger(FederationConfigurationManagementEntryService.class);

  public FederationConfigurationManagementEntryService(
      FederationConfigurationQueryRepository federationConfigurationQueryRepository,
      FederationConfigurationCommandRepository federationConfigurationCommandRepository,
      TenantQueryRepository tenantQueryRepository,
      AuditLogWriters auditLogWriters) {
    this.federationConfigurationQueryRepository = federationConfigurationQueryRepository;
    this.federationConfigurationCommandRepository = federationConfigurationCommandRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.auditLogWriters = auditLogWriters;
  }

  @Override
  public FederationConfigManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    AdminPermissions permissions = getRequiredPermissions("create");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    FederationConfigRegistrationContextCreator contextCreator =
        new FederationConfigRegistrationContextCreator(tenant, request, dryRun);
    FederationConfigRegistrationContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.create(
            "FederationConfigurationManagementApi.create",
            tenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogWriters.write(tenant, auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new FederationConfigManagementResponse(
          FederationConfigManagementStatus.FORBIDDEN, response);
    }

    if (context.isDryRun()) {
      return context.toResponse();
    }

    federationConfigurationCommandRepository.register(tenant, context.configuration());

    return context.toResponse();
  }

  @Override
  public FederationConfigManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationQueries queries,
      RequestAttributes requestAttributes) {
    AdminPermissions permissions = getRequiredPermissions("findList");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "FederationConfigurationManagementApi.findList",
            "findList",
            tenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogWriters.write(tenant, auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new FederationConfigManagementResponse(
          FederationConfigManagementStatus.FORBIDDEN, response);
    }

    long totalCount = federationConfigurationQueryRepository.findTotalCount(tenant, queries);

    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", totalCount);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());
      return new FederationConfigManagementResponse(FederationConfigManagementStatus.OK, response);
    }

    List<FederationConfiguration> configurations =
        federationConfigurationQueryRepository.findList(tenant, queries);

    Map<String, Object> response = new HashMap<>();
    response.put("list", configurations.stream().map(FederationConfiguration::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());

    return new FederationConfigManagementResponse(FederationConfigManagementStatus.OK, response);
  }

  @Override
  public FederationConfigManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes) {
    AdminPermissions permissions = getRequiredPermissions("get");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    FederationConfiguration configuration =
        federationConfigurationQueryRepository.findWithDisabled(tenant, identifier, true);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "FederationConfigurationManagementApi.get",
            "get",
            tenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogWriters.write(tenant, auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new FederationConfigManagementResponse(
          FederationConfigManagementStatus.FORBIDDEN, response);
    }

    if (!configuration.exists()) {
      return new FederationConfigManagementResponse(
          FederationConfigManagementStatus.NOT_FOUND, Map.of());
    }

    return new FederationConfigManagementResponse(
        FederationConfigManagementStatus.OK, configuration.toMap());
  }

  @Override
  public FederationConfigManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigurationIdentifier identifier,
      FederationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    AdminPermissions permissions = getRequiredPermissions("update");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    FederationConfiguration before =
        federationConfigurationQueryRepository.findWithDisabled(tenant, identifier, true);

    FederationConfigUpdateContextCreator contextCreator =
        new FederationConfigUpdateContextCreator(tenant, before, request, dryRun);
    FederationConfigUpdateContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "FederationConfigurationManagementApi.update",
            tenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogWriters.write(tenant, auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new FederationConfigManagementResponse(
          FederationConfigManagementStatus.FORBIDDEN, response);
    }

    if (!before.exists()) {
      return new FederationConfigManagementResponse(
          FederationConfigManagementStatus.NOT_FOUND, Map.of());
    }

    if (context.isDryRun()) {
      return context.toResponse();
    }

    federationConfigurationCommandRepository.update(tenant, context.after());

    return context.toResponse();
  }

  @Override
  public FederationConfigManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {
    AdminPermissions permissions = getRequiredPermissions("delete");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    FederationConfiguration configuration =
        federationConfigurationQueryRepository.findWithDisabled(tenant, identifier, true);

    AuditLog auditLog =
        AuditLogCreator.createOnDeletion(
            "FederationConfigurationManagementApi.delete",
            "delete",
            tenant,
            operator,
            oAuthToken,
            configuration.payload(),
            requestAttributes);
    auditLogWriters.write(tenant, auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new FederationConfigManagementResponse(
          FederationConfigManagementStatus.FORBIDDEN, response);
    }

    if (!configuration.exists()) {
      return new FederationConfigManagementResponse(
          FederationConfigManagementStatus.NOT_FOUND, Map.of());
    }

    federationConfigurationCommandRepository.delete(tenant, configuration);

    return new FederationConfigManagementResponse(
        FederationConfigManagementStatus.OK, configuration.payload());
  }
}

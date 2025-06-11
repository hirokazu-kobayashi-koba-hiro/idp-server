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

package org.idp.server.usecases.control_plane.tenant_manager;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.oidc.authorization.AuthorizationServerManagementApi;
import org.idp.server.control_plane.management.oidc.authorization.AuthorizationServerUpdateContext;
import org.idp.server.control_plane.management.oidc.authorization.AuthorizationServerUpdateContextCreator;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerManagementResponse;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerManagementStatus;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerUpdateRequest;
import org.idp.server.control_plane.management.oidc.authorization.validator.AuthorizationServerRequestValidationResult;
import org.idp.server.control_plane.management.oidc.authorization.validator.AuthorizationServerRequestValidator;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogWriters;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.type.RequestAttributes;

@Transaction
public class AuthorizationServerManagementEntryService implements AuthorizationServerManagementApi {

  TenantQueryRepository tenantQueryRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  AuthorizationServerConfigurationCommandRepository
      authorizationServerConfigurationCommandRepository;
  AuditLogWriters auditLogWriters;
  LoggerWrapper log = LoggerWrapper.getLogger(AuthorizationServerManagementEntryService.class);

  public AuthorizationServerManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      AuthorizationServerConfigurationQueryRepository
          authorizationServerConfigurationQueryRepository,
      AuthorizationServerConfigurationCommandRepository
          authorizationServerConfigurationCommandRepository,
      AuditLogWriters auditLogWriters) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.authorizationServerConfigurationQueryRepository =
        authorizationServerConfigurationQueryRepository;
    this.authorizationServerConfigurationCommandRepository =
        authorizationServerConfigurationCommandRepository;
    this.auditLogWriters = auditLogWriters;
  }

  @Override
  public AuthorizationServerManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("get");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "AuthorizationServerManagementApi.get",
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
      return new AuthorizationServerManagementResponse(
          AuthorizationServerManagementStatus.FORBIDDEN, response);
    }

    return new AuthorizationServerManagementResponse(
        AuthorizationServerManagementStatus.OK, authorizationServerConfiguration.toMap());
  }

  @Override
  public AuthorizationServerManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthorizationServerUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("update");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    AuthorizationServerConfiguration before =
        authorizationServerConfigurationQueryRepository.get(tenant);

    AuthorizationServerRequestValidator validator =
        new AuthorizationServerRequestValidator(request, dryRun);
    AuthorizationServerRequestValidationResult validate = validator.validate();

    AuthorizationServerUpdateContextCreator contextCreator =
        new AuthorizationServerUpdateContextCreator(tenant, before, request, dryRun);
    AuthorizationServerUpdateContext context = contextCreator.create();

    AuditLog auditLog =
        AuditLogCreator.createOnUpdate(
            "AuthorizationServerManagementApi.update",
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
      return new AuthorizationServerManagementResponse(
          AuthorizationServerManagementStatus.FORBIDDEN, response);
    }

    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    if (dryRun) {
      return context.toResponse();
    }

    authorizationServerConfigurationCommandRepository.update(tenant, context.after());

    return context.toResponse();
  }
}

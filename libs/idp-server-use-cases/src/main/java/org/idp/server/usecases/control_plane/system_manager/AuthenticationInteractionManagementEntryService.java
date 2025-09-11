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
import org.idp.server.control_plane.management.authentication.interaction.AuthenticationInteractionManagementApi;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionManagementResponse;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionManagementStatus;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.interaction.AuthenticationInteraction;
import org.idp.server.core.openid.authentication.interaction.AuthenticationInteractionQueries;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.*;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class AuthenticationInteractionManagementEntryService
    implements AuthenticationInteractionManagementApi {

  AuthenticationInteractionQueryRepository authenticationInteractionQueryRepository;
  TenantQueryRepository tenantQueryRepository;
  AuditLogWriters auditLogWriters;
  LoggerWrapper log =
      LoggerWrapper.getLogger(AuthenticationInteractionManagementEntryService.class);

  public AuthenticationInteractionManagementEntryService(
      AuthenticationInteractionQueryRepository authenticationInteractionQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      AuditLogWriters auditLogWriters) {
    this.authenticationInteractionQueryRepository = authenticationInteractionQueryRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.auditLogWriters = auditLogWriters;
  }

  @Override
  public AuthenticationInteractionManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationInteractionQueries queries,
      RequestAttributes requestAttributes) {
    AdminPermissions permissions = getRequiredPermissions("findList");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "AuthenticationInteractionManagementApi.findList",
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
      return new AuthenticationInteractionManagementResponse(
          AuthenticationInteractionManagementStatus.FORBIDDEN, response);
    }

    long totalCount = authenticationInteractionQueryRepository.findTotalCount(tenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());
      return new AuthenticationInteractionManagementResponse(
          AuthenticationInteractionManagementStatus.OK, response);
    }

    List<AuthenticationInteraction> authenticationInteractions =
        authenticationInteractionQueryRepository.findList(tenant, queries);

    Map<String, Object> response = new HashMap<>();
    response.put(
        "list", authenticationInteractions.stream().map(AuthenticationInteraction::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());

    return new AuthenticationInteractionManagementResponse(
        AuthenticationInteractionManagementStatus.OK, response);
  }

  @Override
  public AuthenticationInteractionManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationTransactionIdentifier identifier,
      String key,
      RequestAttributes requestAttributes) {
    AdminPermissions permissions = getRequiredPermissions("get");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    AuthenticationInteraction authenticationInteraction =
        authenticationInteractionQueryRepository.find(tenant, identifier, key);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "AuthenticationInteractionManagementApi.get",
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
      return new AuthenticationInteractionManagementResponse(
          AuthenticationInteractionManagementStatus.FORBIDDEN, response);
    }

    if (!authenticationInteraction.exists()) {
      return new AuthenticationInteractionManagementResponse(
          AuthenticationInteractionManagementStatus.NOT_FOUND, Map.of());
    }

    return new AuthenticationInteractionManagementResponse(
        AuthenticationInteractionManagementStatus.OK, authenticationInteraction.toMap());
  }
}

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
import java.util.Map;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.management.authentication.interaction.AuthenticationInteractionManagementApi;
import org.idp.server.control_plane.management.authentication.interaction.handler.*;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionManagementResponse;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.interaction.AuthenticationInteractionQueries;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.*;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class AuthenticationInteractionManagementEntryService
    implements AuthenticationInteractionManagementApi {

  AuditLogPublisher auditLogPublisher;
  LoggerWrapper log =
      LoggerWrapper.getLogger(AuthenticationInteractionManagementEntryService.class);

  // Handler/Service pattern
  private AuthenticationInteractionManagementHandler handler;

  public AuthenticationInteractionManagementEntryService(
      AuthenticationInteractionQueryRepository authenticationInteractionQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      AuditLogPublisher auditLogPublisher) {
    this.auditLogPublisher = auditLogPublisher;

    this.handler = createHandler(authenticationInteractionQueryRepository, tenantQueryRepository);
  }

  private AuthenticationInteractionManagementHandler createHandler(
      AuthenticationInteractionQueryRepository authenticationInteractionQueryRepository,
      TenantQueryRepository tenantQueryRepository) {

    Map<String, AuthenticationInteractionManagementService<?>> services = new HashMap<>();
    services.put(
        "findList",
        new AuthenticationInteractionFindListService(authenticationInteractionQueryRepository));
    services.put(
        "get", new AuthenticationInteractionFindService(authenticationInteractionQueryRepository));

    return new AuthenticationInteractionManagementHandler(services, this, tenantQueryRepository);
  }

  @Override
  @Transaction(readOnly = true)
  public AuthenticationInteractionManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationInteractionQueries queries,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern
    AuthenticationInteractionManagementResult result =
        handler.handle(
            "findList", tenantIdentifier, operator, oAuthToken, queries, requestAttributes);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "AuthenticationInteractionManagementApi.findList",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse();
    }

    // Record audit log (read operation)
    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "AuthenticationInteractionManagementApi.findList",
            "findList",
            result.tenant(),
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }

  @Override
  @Transaction(readOnly = true)
  public AuthenticationInteractionManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationTransactionIdentifier identifier,
      String type,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern
    AuthenticationInteractionFindRequest request =
        new AuthenticationInteractionFindRequest(identifier, type);
    AuthenticationInteractionManagementResult result =
        handler.handle("get", tenantIdentifier, operator, oAuthToken, request, requestAttributes);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "AuthenticationInteractionManagementApi.get",
              result.tenant(),
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      return result.toResponse();
    }

    // Record audit log (read operation)
    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "AuthenticationInteractionManagementApi.get",
            "get",
            result.tenant(),
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }
}

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
import org.idp.server.control_plane.management.authentication.transaction.AuthenticationTransactionManagementApi;
import org.idp.server.control_plane.management.authentication.transaction.handler.AuthenticationTransactionFindListService;
import org.idp.server.control_plane.management.authentication.transaction.handler.AuthenticationTransactionFindService;
import org.idp.server.control_plane.management.authentication.transaction.handler.AuthenticationTransactionManagementHandler;
import org.idp.server.control_plane.management.authentication.transaction.handler.AuthenticationTransactionManagementResult;
import org.idp.server.control_plane.management.authentication.transaction.handler.AuthenticationTransactionManagementService;
import org.idp.server.control_plane.management.authentication.transaction.io.AuthenticationTransactionManagementResponse;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.AuthenticationTransactionQueries;
import org.idp.server.core.openid.authentication.repository.AuthenticationTransactionQueryRepository;
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
public class AuthenticationTransactionManagementEntryService
    implements AuthenticationTransactionManagementApi {

  AuditLogPublisher auditLogPublisher;
  TenantQueryRepository tenantQueryRepository;
  LoggerWrapper log =
      LoggerWrapper.getLogger(AuthenticationTransactionManagementEntryService.class);

  // Handler/Service pattern
  private AuthenticationTransactionManagementHandler handler;

  public AuthenticationTransactionManagementEntryService(
      AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      AuditLogPublisher auditLogPublisher) {
    this.auditLogPublisher = auditLogPublisher;
    this.tenantQueryRepository = tenantQueryRepository;

    this.handler = createHandler(authenticationTransactionQueryRepository, tenantQueryRepository);
  }

  private AuthenticationTransactionManagementHandler createHandler(
      AuthenticationTransactionQueryRepository authenticationTransactionQueryRepository,
      TenantQueryRepository tenantQueryRepository) {

    Map<String, AuthenticationTransactionManagementService<?>> services = new HashMap<>();
    services.put(
        "findList",
        new AuthenticationTransactionFindListService(authenticationTransactionQueryRepository));
    services.put(
        "get", new AuthenticationTransactionFindService(authenticationTransactionQueryRepository));

    return new AuthenticationTransactionManagementHandler(services, this, tenantQueryRepository);
  }

  @Override
  @Transaction(readOnly = true)
  public AuthenticationTransactionManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationTransactionQueries queries,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern
    AuthenticationTransactionManagementResult result =
        handler.handle(
            "findList", tenantIdentifier, operator, oAuthToken, queries, requestAttributes, false);

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "AuthenticationTransactionManagementApi.findList",
              tenant,
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      throw result.getException();
    }

    // Record audit log (read operation)
    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "AuthenticationTransactionManagementApi.findList",
            "findList",
            tenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public AuthenticationTransactionManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationTransactionIdentifier identifier,
      RequestAttributes requestAttributes) {

    // Delegate to Handler/Service pattern
    AuthenticationTransactionManagementResult result =
        handler.handle(
            "get", tenantIdentifier, operator, oAuthToken, identifier, requestAttributes, false);

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    if (result.hasException()) {
      AuditLog auditLog =
          AuditLogCreator.createOnError(
              "AuthenticationTransactionManagementApi.get",
              tenant,
              operator,
              oAuthToken,
              result.getException(),
              requestAttributes);
      auditLogPublisher.publish(auditLog);
      throw result.getException();
    }

    // Record audit log (read operation)
    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "AuthenticationTransactionManagementApi.get",
            "get",
            tenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }
}

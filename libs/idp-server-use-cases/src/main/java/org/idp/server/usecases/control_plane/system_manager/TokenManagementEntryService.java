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
import org.idp.server.control_plane.base.AdminAuthenticationContext;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.management.token.TokenManagementApi;
import org.idp.server.control_plane.management.token.handler.*;
import org.idp.server.control_plane.management.token.io.*;
import org.idp.server.core.openid.token.OAuthTokenIdentifier;
import org.idp.server.core.openid.token.OAuthTokenQueries;
import org.idp.server.core.openid.token.repository.OAuthTokenManagementCommandRepository;
import org.idp.server.core.openid.token.repository.OAuthTokenManagementQueryRepository;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class TokenManagementEntryService implements TokenManagementApi {

  private final TokenManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  public TokenManagementEntryService(
      OAuthTokenManagementQueryRepository queryRepository,
      OAuthTokenManagementCommandRepository commandRepository,
      TenantQueryRepository tenantQueryRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, TokenManagementService<?>> services = new HashMap<>();
    services.put("findList", new TokenFindListService(queryRepository));
    services.put("get", new TokenFindService(queryRepository));
    services.put("delete", new TokenDeletionService(queryRepository, commandRepository));
    services.put(
        "deleteUserTokens", new TokenUserDeletionService(queryRepository, commandRepository));

    this.handler = new TokenManagementHandler(services, this, tenantQueryRepository);
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  @Transaction(readOnly = true)
  public TokenManagementResponse findList(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      OAuthTokenQueries queries,
      RequestAttributes requestAttributes) {

    TokenFindListRequest findListRequest = new TokenFindListRequest(queries);
    TokenManagementResult result =
        handler.handle(
            "findList",
            authenticationContext,
            tenantIdentifier,
            findListRequest,
            requestAttributes,
            false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  @Transaction(readOnly = true)
  public TokenManagementResponse get(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      OAuthTokenIdentifier identifier,
      RequestAttributes requestAttributes) {

    TokenFindRequest findRequest = new TokenFindRequest(identifier);
    TokenManagementResult result =
        handler.handle(
            "get", authenticationContext, tenantIdentifier, findRequest, requestAttributes, false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public TokenManagementResponse delete(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      OAuthTokenIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    TokenDeleteRequest deleteRequest = new TokenDeleteRequest(identifier);
    TokenManagementResult result =
        handler.handle(
            "delete",
            authenticationContext,
            tenantIdentifier,
            deleteRequest,
            requestAttributes,
            dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }

  @Override
  public TokenManagementResponse deleteUserTokens(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      String userId,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    TokenUserDeleteRequest deleteRequest = new TokenUserDeleteRequest(userId);
    TokenManagementResult result =
        handler.handle(
            "deleteUserTokens",
            authenticationContext,
            tenantIdentifier,
            deleteRequest,
            requestAttributes,
            dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}

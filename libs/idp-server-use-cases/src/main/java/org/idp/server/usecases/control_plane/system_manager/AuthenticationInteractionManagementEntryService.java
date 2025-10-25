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
import org.idp.server.control_plane.management.authentication.interaction.AuthenticationInteractionManagementApi;
import org.idp.server.control_plane.management.authentication.interaction.handler.*;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionFindListRequest;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionFindRequest;
import org.idp.server.control_plane.management.authentication.interaction.io.AuthenticationInteractionManagementResponse;
import org.idp.server.core.openid.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.openid.authentication.interaction.AuthenticationInteractionQueries;
import org.idp.server.core.openid.authentication.repository.AuthenticationInteractionQueryRepository;
import org.idp.server.platform.audit.*;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class AuthenticationInteractionManagementEntryService
    implements AuthenticationInteractionManagementApi {

  private final AuthenticationInteractionManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  public AuthenticationInteractionManagementEntryService(
      AuthenticationInteractionQueryRepository authenticationInteractionQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, AuthenticationInteractionManagementService<?>> services = new HashMap<>();
    services.put(
        "findList",
        new AuthenticationInteractionFindListService(authenticationInteractionQueryRepository));
    services.put(
        "get", new AuthenticationInteractionFindService(authenticationInteractionQueryRepository));

    this.handler =
        new AuthenticationInteractionManagementHandler(services, this, tenantQueryRepository);
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  @Transaction(readOnly = true)
  public AuthenticationInteractionManagementResponse findList(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationInteractionQueries queries,
      RequestAttributes requestAttributes) {

    AuthenticationInteractionFindListRequest findListRequest =
        new AuthenticationInteractionFindListRequest(queries);
    AuthenticationInteractionManagementResult result =
        handler.handle(
            "findList",
            authenticationContext,
            tenantIdentifier,
            findListRequest,
            requestAttributes);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }

  @Override
  @Transaction(readOnly = true)
  public AuthenticationInteractionManagementResponse get(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthenticationTransactionIdentifier identifier,
      String type,
      RequestAttributes requestAttributes) {

    AuthenticationInteractionFindRequest findRequest =
        new AuthenticationInteractionFindRequest(identifier, type);
    AuthenticationInteractionManagementResult result =
        handler.handle(
            "get", authenticationContext, tenantIdentifier, findRequest, requestAttributes);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse();
  }
}

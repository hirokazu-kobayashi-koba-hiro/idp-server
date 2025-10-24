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
import org.idp.server.control_plane.management.oidc.authorization.AuthorizationServerManagementApi;
import org.idp.server.control_plane.management.oidc.authorization.handler.*;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerFindRequest;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerManagementResponse;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerUpdateRequest;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class AuthorizationServerManagementEntryService implements AuthorizationServerManagementApi {

  private final AuthorizationServerManagementHandler handler;
  private final TenantQueryRepository tenantQueryRepository;
  private final AuditLogPublisher auditLogPublisher;

  public AuthorizationServerManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      AuthorizationServerConfigurationQueryRepository queryRepository,
      AuthorizationServerConfigurationCommandRepository commandRepository,
      AuditLogPublisher auditLogPublisher) {

    Map<String, AuthorizationServerManagementService<?>> services = new HashMap<>();
    services.put("get", new AuthorizationServerFindService(queryRepository));
    services.put(
        "update", new AuthorizationServerUpdateService(queryRepository, commandRepository));

    this.handler = new AuthorizationServerManagementHandler(services, this, tenantQueryRepository);

    this.tenantQueryRepository = tenantQueryRepository;
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  @Transaction(readOnly = true)
  public AuthorizationServerManagementResponse get(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      RequestAttributes requestAttributes) {

    AuthorizationServerFindRequest findRequest = new AuthorizationServerFindRequest();
    AuthorizationServerManagementResult result =
        handler.handle(
            "get", authenticationContext, tenantIdentifier, findRequest, requestAttributes, false);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(false);
  }

  @Override
  public AuthorizationServerManagementResponse update(
      AdminAuthenticationContext authenticationContext,
      TenantIdentifier tenantIdentifier,
      AuthorizationServerUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AuthorizationServerManagementResult result =
        handler.handle(
            "update", authenticationContext, tenantIdentifier, request, requestAttributes, dryRun);

    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}

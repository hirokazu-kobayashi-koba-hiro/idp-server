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
import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.security.event.SecurityEventManagementApi;
import org.idp.server.control_plane.management.security.event.io.SecurityEventManagementResponse;
import org.idp.server.control_plane.management.security.event.io.SecurityEventManagementStatus;
import org.idp.server.control_plane.management.security.hook.*;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogWriters;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.SecurityEventQueries;
import org.idp.server.platform.security.event.SecurityEventIdentifier;
import org.idp.server.platform.security.repository.SecurityEventQueryRepository;
import org.idp.server.platform.security.type.RequestAttributes;

@Transaction(readOnly = true)
public class SecurityEventManagementEntryService implements SecurityEventManagementApi {

  SecurityEventQueryRepository securityEventQueryRepository;
  TenantQueryRepository tenantQueryRepository;
  AuditLogWriters auditLogWriters;
  LoggerWrapper log = LoggerWrapper.getLogger(SecurityEventManagementEntryService.class);

  public SecurityEventManagementEntryService(
      SecurityEventQueryRepository securityEventQueryRepository,
      TenantQueryRepository tenantQueryRepository,
      AuditLogWriters auditLogWriters) {
    this.securityEventQueryRepository = securityEventQueryRepository;
    this.tenantQueryRepository = tenantQueryRepository;
    this.auditLogWriters = auditLogWriters;
  }

  @Override
  public SecurityEventManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventQueries queries,
      RequestAttributes requestAttributes) {
    AdminPermissions permissions = getRequiredPermissions("findList");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "SecurityEventManagementApi.findList",
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
      return new SecurityEventManagementResponse(SecurityEventManagementStatus.FORBIDDEN, response);
    }

    long totalCount = securityEventQueryRepository.findTotalCount(tenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response = new HashMap<>();
      response.put("list", List.of());
      response.put("total_count", 0);
      response.put("limit", queries.limit());
      response.put("offset", queries.offset());
      return new SecurityEventManagementResponse(SecurityEventManagementStatus.OK, response);
    }

    List<SecurityEvent> events = securityEventQueryRepository.findList(tenant, queries);

    Map<String, Object> response = new HashMap<>();
    response.put("list", events.stream().map(SecurityEvent::toMap).toList());
    response.put("total_count", totalCount);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());

    return new SecurityEventManagementResponse(SecurityEventManagementStatus.OK, response);
  }

  @Override
  public SecurityEventManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventIdentifier identifier,
      RequestAttributes requestAttributes) {
    AdminPermissions permissions = getRequiredPermissions("get");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    SecurityEvent securityEvent = securityEventQueryRepository.find(tenant, identifier);

    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "SecurityEventManagementApi.get",
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
      return new SecurityEventManagementResponse(SecurityEventManagementStatus.FORBIDDEN, response);
    }

    if (!securityEvent.exists()) {
      return new SecurityEventManagementResponse(SecurityEventManagementStatus.NOT_FOUND, Map.of());
    }

    return new SecurityEventManagementResponse(
        SecurityEventManagementStatus.OK, securityEvent.toMap());
  }
}

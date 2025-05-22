/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.management.oidc.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.definition.AdminPermission;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.oidc.client.io.ClientManagementResponse;
import org.idp.server.control_plane.management.oidc.client.io.ClientRegistrationRequest;
import org.idp.server.core.oidc.client.ClientIdentifier;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.token.OAuthToken;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.type.RequestAttributes;

public interface ClientManagementApi {

  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put("create", new AdminPermissions(Set.of(AdminPermission.CLIENT_CREATE)));
    map.put("findList", new AdminPermissions(Set.of(AdminPermission.CLIENT_READ)));
    map.put("get", new AdminPermissions(Set.of(AdminPermission.CLIENT_READ)));
    map.put("update", new AdminPermissions(Set.of(AdminPermission.CLIENT_UPDATE)));
    map.put("delete", new AdminPermissions(Set.of(AdminPermission.CLIENT_DELETE)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  ClientManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  ClientManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes);

  ClientManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientIdentifier clientIdentifier,
      RequestAttributes requestAttributes);

  ClientManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientIdentifier clientIdentifier,
      ClientRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  ClientManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientIdentifier clientIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}

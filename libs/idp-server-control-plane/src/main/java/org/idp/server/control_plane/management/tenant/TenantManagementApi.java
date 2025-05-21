package org.idp.server.control_plane.management.tenant;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.base.definition.AdminPermission;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantRequest;
import org.idp.server.core.identity.User;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public interface TenantManagementApi {
  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put("create", new AdminPermissions(Set.of(AdminPermission.TENANT_CREATE)));
    map.put("findList", new AdminPermissions(Set.of(AdminPermission.TENANT_READ)));
    map.put("get", new AdminPermissions(Set.of(AdminPermission.TENANT_READ)));
    map.put("update", new AdminPermissions(Set.of(AdminPermission.TENANT_UPDATE)));
    map.put("delete", new AdminPermissions(Set.of(AdminPermission.TENANT_DELETE)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  TenantManagementResponse create(
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  TenantManagementResponse findList(
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes);

  TenantManagementResponse get(
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantIdentifier tenantIdentifier,
      RequestAttributes requestAttributes);

  TenantManagementResponse update(
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantIdentifier tenantIdentifier,
      TenantRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  TenantManagementResponse delete(
      TenantIdentifier adminTenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      TenantIdentifier tenantIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}

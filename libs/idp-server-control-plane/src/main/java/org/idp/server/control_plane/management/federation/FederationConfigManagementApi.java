package org.idp.server.control_plane.management.federation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.basic.exception.UnSupportedException;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.base.definition.AdminPermission;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.federation.io.FederationConfigManagementResponse;
import org.idp.server.control_plane.management.federation.io.FederationConfigRequest;
import org.idp.server.core.federation.FederationConfigurationIdentifier;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.token.OAuthToken;

public interface FederationConfigManagementApi {

  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put("create", new AdminPermissions(Set.of(AdminPermission.FEDERATION_CONFIG_CREATE)));
    map.put("findList", new AdminPermissions(Set.of(AdminPermission.FEDERATION_CONFIG_READ)));
    map.put("get", new AdminPermissions(Set.of(AdminPermission.FEDERATION_CONFIG_READ)));
    map.put("update", new AdminPermissions(Set.of(AdminPermission.FEDERATION_CONFIG_UPDATE)));
    map.put("delete", new AdminPermissions(Set.of(AdminPermission.FEDERATION_CONFIG_DELETE)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  FederationConfigManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  FederationConfigManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes);

  FederationConfigManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes);

  FederationConfigManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigurationIdentifier identifier,
      FederationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  FederationConfigManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      FederationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}

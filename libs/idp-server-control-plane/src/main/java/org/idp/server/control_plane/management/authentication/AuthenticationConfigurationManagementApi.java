package org.idp.server.control_plane.management.authentication;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.base.definition.AdminPermission;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.authentication.io.AuthenticationConfigManagementResponse;
import org.idp.server.control_plane.management.authentication.io.AuthenticationConfigRequest;
import org.idp.server.core.authentication.AuthenticationConfigurationIdentifier;
import org.idp.server.core.identity.User;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public interface AuthenticationConfigurationManagementApi {

  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put("create", new AdminPermissions(Set.of(AdminPermission.AUTHENTICATION_CONFIG_CREATE)));
    map.put("findList", new AdminPermissions(Set.of(AdminPermission.AUTHENTICATION_CONFIG_READ)));
    map.put("get", new AdminPermissions(Set.of(AdminPermission.AUTHENTICATION_CONFIG_READ)));
    map.put("update", new AdminPermissions(Set.of(AdminPermission.AUTHENTICATION_CONFIG_UPDATE)));
    map.put("delete", new AdminPermissions(Set.of(AdminPermission.AUTHENTICATION_CONFIG_DELETE)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  AuthenticationConfigManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  AuthenticationConfigManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes);

  AuthenticationConfigManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes);

  AuthenticationConfigManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationConfigurationIdentifier identifier,
      AuthenticationConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  AuthenticationConfigManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      AuthenticationConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}

package org.idp.server.control_plane.management.security.hook;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.definition.AdminPermission;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigManagementResponse;
import org.idp.server.control_plane.management.security.hook.io.SecurityEventHookConfigRequest;
import org.idp.server.core.identity.User;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.security.hook.SecurityEventHookConfigurationIdentifier;
import org.idp.server.platform.security.type.RequestAttributes;

public interface SecurityEventHookConfigurationManagementApi {
  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put(
        "create", new AdminPermissions(Set.of(AdminPermission.SECURITY_EVENT_HOOK_CONFIG_CREATE)));
    map.put(
        "findList", new AdminPermissions(Set.of(AdminPermission.SECURITY_EVENT_HOOK_CONFIG_READ)));
    map.put("get", new AdminPermissions(Set.of(AdminPermission.SECURITY_EVENT_HOOK_CONFIG_READ)));
    map.put(
        "update", new AdminPermissions(Set.of(AdminPermission.SECURITY_EVENT_HOOK_CONFIG_UPDATE)));
    map.put(
        "delete", new AdminPermissions(Set.of(AdminPermission.SECURITY_EVENT_HOOK_CONFIG_DELETE)));
    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  SecurityEventHookConfigManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  SecurityEventHookConfigManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes);

  SecurityEventHookConfigManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookConfigurationIdentifier identifier,
      RequestAttributes requestAttributes);

  SecurityEventHookConfigManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookConfigurationIdentifier identifier,
      SecurityEventHookConfigRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  SecurityEventHookConfigManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      SecurityEventHookConfigurationIdentifier identifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}

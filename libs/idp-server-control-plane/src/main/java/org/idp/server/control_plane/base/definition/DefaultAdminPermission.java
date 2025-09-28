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

package org.idp.server.control_plane.base.definition;

import java.util.*;
import java.util.stream.Collectors;
import org.idp.server.core.openid.identity.permission.Permission;
import org.idp.server.core.openid.identity.permission.Permissions;

public enum DefaultAdminPermission {
  ORGANIZATION_CREATE("organization:create", "Admin Create a organization"),
  ORGANIZATION_READ("organization:read", "Admin Read organization information"),
  ORGANIZATION_UPDATE("organization:update", "Admin Update organization"),
  ORGANIZATION_DELETE("organization:delete", "Admin Delete organization"),

  TENANT_INVITATION_CREATE("tenant-invitation:create", "Admin Create a tenant-invitation"),
  TENANT_INVITATION_READ("tenant-invitation:read", "Admin Read tenant-invitation information"),
  TENANT_INVITATION_UPDATE("tenant-invitation:update", "Admin Update tenant-invitation"),
  TENANT_INVITATION_DELETE("tenant-invitation:delete", "Admin Delete tenant-invitation"),

  TENANT_CREATE("tenant:create", "Admin Create a tenant"),
  TENANT_READ("tenant:read", "Admin Read tenant information"),
  TENANT_UPDATE("tenant:update", "Admin Update tenant"),
  TENANT_DELETE("tenant:delete", "Admin Delete tenant"),

  AUTHORIZATION_SERVER_CREATE("authorization-server:create", "Admin Create a authorization-server"),
  AUTHORIZATION_SERVER_READ(
      "authorization-server:read", "Admin Read authorization-server information"),
  AUTHORIZATION_SERVER_UPDATE("authorization-server:update", "Admin Update authorization-server"),
  AUTHORIZATION_SERVER_DELETE("authorization-server:delete", "Admin Delete authorization-server"),

  CLIENT_CREATE("client:create", "Admin Create a client"),
  CLIENT_READ("client:read", "Admin Read client information"),
  CLIENT_UPDATE("client:update", "Admin Update client"),
  CLIENT_DELETE("client:delete", "Admin Delete client"),

  USER_CREATE("user:create", "Admin Create a user"),
  USER_READ("user:read", "Admin Read user information"),
  USER_UPDATE("user:update", "Admin Update user"),
  USER_DELETE("user:delete", "Admin Delete user"),
  USER_INVITE("user:invite", "Admin Invite a user"),
  USER_SUSPEND("user:suspend", "Admin Suspend user account"),

  PERMISSION_CREATE("permission:create", "Admin Create a permission"),
  PERMISSION_READ("permission:read", "Admin Read permission information"),
  PERMISSION_UPDATE("permission:update", "Admin Update permission"),
  PERMISSION_DELETE("permission:delete", "Admin Delete permission"),

  ROLE_CREATE("role:create", "Admin Create a role"),
  ROLE_READ("role:read", "Admin Read role information"),
  ROLE_UPDATE("role:update", "Admin Update role"),
  ROLE_DELETE("role:delete", "Admin Delete role"),

  AUTHENTICATION_CONFIG_CREATE(
      "authentication-config:create", "Admin Create a authentication-config"),
  AUTHENTICATION_CONFIG_READ(
      "authentication-config:read", "Admin Read authentication-config information"),
  AUTHENTICATION_CONFIG_UPDATE(
      "authentication-config:update", "Admin Update authentication-config"),
  AUTHENTICATION_CONFIG_DELETE(
      "authentication-config:delete", "Admin Delete authentication-config"),

  AUTHENTICATION_POLICY_CONFIG_CREATE(
      "authentication-policy-config:create", "Admin Create a authentication-policy-config"),
  AUTHENTICATION_POLICY_CONFIG_READ(
      "authentication-policy-config:read", "Admin Read authentication-policy-config information"),
  AUTHENTICATION_POLICY_CONFIG_UPDATE(
      "authentication-policy-config:update", "Admin Update authentication-policy-config"),
  AUTHENTICATION_POLICY_CONFIG_DELETE(
      "authentication-policy-config:delete", "Admin Delete authentication-policy-config"),

  IDENTITY_VERIFICATION_CONFIG_CREATE(
      "identity-verification-config:create", "Admin Create a identity-verification-config"),
  IDENTITY_VERIFICATION_CONFIG_READ(
      "identity-verification-config:read", "Admin Read identity-verification-config information"),
  IDENTITY_VERIFICATION_CONFIG_UPDATE(
      "identity-verification-config:update", "Admin Update identity-verification-config"),
  IDENTITY_VERIFICATION_CONFIG_DELETE(
      "identity-verification-config:delete", "Admin Delete identity-verification-config"),

  FEDERATION_CONFIG_CREATE("federation-config:create", "Admin Create a federation-config"),
  FEDERATION_CONFIG_READ("federation-config:read", "Admin Read federation-config information"),
  FEDERATION_CONFIG_UPDATE("federation-config:update", "Admin Update federation-config"),
  FEDERATION_CONFIG_DELETE("federation-config:delete", "Admin Delete federation-config"),

  SECURITY_EVENT_HOOK_CONFIG_CREATE(
      "security-event-hook-config:create", "Admin Create a security-event-hook-config"),
  SECURITY_EVENT_HOOK_CONFIG_READ(
      "security-event-hook-config:read", "Admin Read security-event-hook-config information"),
  SECURITY_EVENT_HOOK_CONFIG_UPDATE(
      "security-event-hook-config:update", "Admin Update security-event-hook-config"),
  SECURITY_EVENT_HOOK_CONFIG_DELETE(
      "security-event-hook-config:delete", "Admin Delete security-event-hook-config"),

  SECURITY_EVENT_HOOK_READ(
      "security-event-hook:read", "Admin Read security-event-hook information"),
  SECURITY_EVENT_HOOK_RETRY(
      "security-event-hook:retry", "Admin Retry failed security-event-hook execution"),

  SECURITY_EVENT_READ("security-event:read", "Admin Read security-event information"),
  AUDIT_LOG_READ("audit-log:read", "Admin Read audit-log information"),
  AUTHENTICATION_TRANSACTION_READ(
      "authentication-transaction:read", "Admin Read authentication-transaction information"),
  AUTHENTICATION_INTERACTION_READ(
      "authentication-interaction:read", "Admin Read authentication-interaction information");

  private final String value;
  private final String description;

  DefaultAdminPermission(String value, String description) {
    this.value = value;
    this.description = description;
  }

  public String value() {
    return value;
  }

  public String description() {
    return description;
  }

  public boolean match(String permission) {
    return value.equals(permission);
  }

  public static Set<DefaultAdminPermission> getAll() {
    return Set.of(values());
  }

  public static Set<DefaultAdminPermission> findCreatePermissions() {
    return Arrays.stream(values())
        .filter(p -> p.value.endsWith(":create"))
        .collect(Collectors.toSet());
  }

  public static Set<DefaultAdminPermission> findReadPermissions() {
    return Arrays.stream(values())
        .filter(p -> p.value.endsWith(":read"))
        .collect(Collectors.toSet());
  }

  public static Set<DefaultAdminPermission> findUpdatePermissions() {
    return Arrays.stream(values())
        .filter(p -> p.value.endsWith(":update"))
        .collect(Collectors.toSet());
  }

  public static Set<DefaultAdminPermission> findDeletePermissions() {
    return Arrays.stream(values())
        .filter(p -> p.value.endsWith(":delete"))
        .collect(Collectors.toSet());
  }

  public static Set<DefaultAdminPermission> findByResource(String resource) {
    return Arrays.stream(values())
        .filter(p -> p.value.startsWith(resource + ":"))
        .collect(Collectors.toSet());
  }

  private Permission toPermission() {
    return new Permission(UUID.randomUUID().toString(), value, description);
  }

  public static Permissions toPermissions() {
    List<Permission> permissions = new ArrayList<>();
    for (DefaultAdminPermission defaultAdminPermission : values()) {
      permissions.add(defaultAdminPermission.toPermission());
    }
    return new Permissions(permissions);
  }
}

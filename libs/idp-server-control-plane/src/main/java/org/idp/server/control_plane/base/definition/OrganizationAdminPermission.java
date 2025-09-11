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

public enum OrganizationAdminPermission {
  // Organization-level tenant management
  ORG_TENANT_CREATE("org-tenant:create", "Organization Admin Create a tenant"),
  ORG_TENANT_READ("org-tenant:read", "Organization Admin Read tenant information"),
  ORG_TENANT_UPDATE("org-tenant:update", "Organization Admin Update tenant"),
  ORG_TENANT_DELETE("org-tenant:delete", "Organization Admin Delete tenant"),

  // Organization-level client management
  ORG_CLIENT_CREATE("org-client:create", "Organization Admin Create a client"),
  ORG_CLIENT_READ("org-client:read", "Organization Admin Read client information"),
  ORG_CLIENT_UPDATE("org-client:update", "Organization Admin Update client"),
  ORG_CLIENT_DELETE("org-client:delete", "Organization Admin Delete client"),

  // Organization-level user management
  ORG_USER_CREATE("org-user:create", "Organization Admin Create a user"),
  ORG_USER_READ("org-user:read", "Organization Admin Read user information"),
  ORG_USER_UPDATE("org-user:update", "Organization Admin Update user"),
  ORG_USER_DELETE("org-user:delete", "Organization Admin Delete user"),

  // Organization member management
  ORG_MEMBER_READ("org-member:read", "Organization Admin Read member information"),
  ORG_MEMBER_INVITE("org-member:invite", "Organization Admin Invite member"),
  ORG_MEMBER_UPDATE("org-member:update", "Organization Admin Update member"),
  ORG_MEMBER_REMOVE("org-member:remove", "Organization Admin Remove member"),

  // Organization-level configuration management
  ORG_IDENTITY_VERIFICATION_CONFIG_CREATE(
      "org-identity-verification-config:create",
      "Organization Admin Create identity verification config"),
  ORG_IDENTITY_VERIFICATION_CONFIG_READ(
      "org-identity-verification-config:read",
      "Organization Admin Read identity verification config"),
  ORG_IDENTITY_VERIFICATION_CONFIG_UPDATE(
      "org-identity-verification-config:update",
      "Organization Admin Update identity verification config"),
  ORG_IDENTITY_VERIFICATION_CONFIG_DELETE(
      "org-identity-verification-config:delete",
      "Organization Admin Delete identity verification config"),

  ORG_FEDERATION_CONFIG_CREATE(
      "org-federation-config:create", "Organization Admin Create federation config"),
  ORG_FEDERATION_CONFIG_READ(
      "org-federation-config:read", "Organization Admin Read federation config"),
  ORG_FEDERATION_CONFIG_UPDATE(
      "org-federation-config:update", "Organization Admin Update federation config"),
  ORG_FEDERATION_CONFIG_DELETE(
      "org-federation-config:delete", "Organization Admin Delete federation config"),

  // Organization-level monitoring and audit
  ORG_SECURITY_EVENT_READ("org-security-event:read", "Organization Admin Read security events"),
  ORG_AUDIT_LOG_READ("org-audit-log:read", "Organization Admin Read audit logs");

  private final String value;
  private final String description;

  OrganizationAdminPermission(String value, String description) {
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

  public static Set<OrganizationAdminPermission> getAll() {
    return Set.of(values());
  }

  public static Set<OrganizationAdminPermission> findCreatePermissions() {
    return Arrays.stream(values())
        .filter(p -> p.value.endsWith(":create"))
        .collect(Collectors.toSet());
  }

  public static Set<OrganizationAdminPermission> findReadPermissions() {
    return Arrays.stream(values())
        .filter(p -> p.value.endsWith(":read"))
        .collect(Collectors.toSet());
  }

  public static Set<OrganizationAdminPermission> findUpdatePermissions() {
    return Arrays.stream(values())
        .filter(p -> p.value.endsWith(":update"))
        .collect(Collectors.toSet());
  }

  public static Set<OrganizationAdminPermission> findDeletePermissions() {
    return Arrays.stream(values())
        .filter(p -> p.value.endsWith(":delete"))
        .collect(Collectors.toSet());
  }

  private Permission toPermission() {
    return new Permission(UUID.randomUUID().toString(), value, description);
  }

  public static Permissions toPermissions() {
    List<Permission> permissions = new ArrayList<>();
    for (OrganizationAdminPermission organizationAdminPermission : values()) {
      permissions.add(organizationAdminPermission.toPermission());
    }
    return new Permissions(permissions);
  }
}

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.idp.server.core.oidc.identity.permission.Permission;
import org.idp.server.core.oidc.identity.permission.Permissions;

public enum AdminPermission {
  ORGANIZATION_CREATE(
      "d20e02ab-f24b-410c-8acf-161843eb78b9", "organization:create", "Admin Create a organization"),
  ORGANIZATION_READ(
      "edb4b49f-5ea3-45e5-b504-a21af71864bb",
      "organization:read",
      "Admin Read organization information"),
  ORGANIZATION_UPDATE(
      "cef58e53-fa12-4133-b931-f850fbdd92d0", "organization:update", "Admin Update organization"),
  ORGANIZATION_DELETE(
      "91973295-6104-45e6-a288-7499840d02c3", "organization:delete", "Admin Delete organization"),

  TENANT_INVITATION_CREATE(
      "598ee28f-bafe-4ec3-9fc7-babd2441ba54",
      "tenant-invitation:create",
      "Admin Create a tenant-invitation"),
  TENANT_INVITATION_READ(
      "d2726fc3-c338-4ef4-b84e-bb4ae279dc90",
      "tenant-invitation:read",
      "Admin Read tenant-invitation information"),
  TENANT_INVITATION_UPDATE(
      "bb4cf988-0d3c-4fbb-b9b9-9cb0f20c743c",
      "tenant-invitation:update",
      "Admin Update tenant-invitation"),
  TENANT_INVITATION_DELETE(
      "66a31e20-1dad-44d0-835d-8608572f5386",
      "tenant-invitation:delete",
      "Admin Delete tenant-invitation"),

  TENANT_CREATE("87cb7a53-0fde-4e92-a685-59aa67b47829", "tenant:create", "Admin Create a tenant"),
  TENANT_READ(
      "fdebbb38-05b1-4d14-8029-aaf7819b41e8", "tenant:read", "Admin Read tenant information"),
  TENANT_UPDATE("d43e3b2e-d8b9-4b47-83cd-2f17d5acbfd6", "tenant:update", "Admin Update tenant"),
  TENANT_DELETE("2f7fcccf-9d54-4e83-bf66-e49bd4670d5e", "tenant:delete", "Admin Delete tenant"),

  AUTHORIZATION_SERVER_CREATE(
      "2cc4b3d3-6e3c-479e-8e23-7d2927ec0ba3",
      "authorization-server:create",
      "Admin Create a authorization-server"),
  AUTHORIZATION_SERVER_READ(
      "a06953d0-c249-49d1-940d-f32d6a06838a",
      "authorization-server:read",
      "Admin Read authorization-server information"),
  AUTHORIZATION_SERVER_UPDATE(
      "1940bf56-4fa2-4e31-aa05-bc048af1f9ae",
      "authorization-server:update",
      "Admin Update authorization-server"),
  AUTHORIZATION_SERVER_DELETE(
      "a85f28c7-02a9-4977-9e6c-a4b968dfde9f",
      "authorization-server:delete",
      "Admin Delete authorization-server"),

  CLIENT_CREATE("77c26d65-e4e0-4ddf-8f04-263d51f1a44e", "client:create", "Admin Create a client"),
  CLIENT_READ(
      "e1ecdd27-3029-4184-9dc9-f35623763cce", "client:read", "Admin Read client information"),
  CLIENT_UPDATE("9f49f806-f41b-4f26-adf6-ebcca08f2f23", "client:update", "Admin Update client"),
  CLIENT_DELETE("f1795881-93f4-4de7-ac16-2ef09d348057", "client:delete", "Admin Delete client"),

  USER_CREATE("1fbf59e5-82c9-4c46-9da2-9f055b435a6d", "user:create", "Admin Create a user"),
  USER_READ("d49a8c34-5a48-49bf-87fb-b2a510cf6ce8", "user:read", "Admin Read user information"),
  USER_UPDATE("2430c236-0f76-4d4d-972f-86aab19464d0", "user:update", "Admin Update user"),
  USER_DELETE("469bd351-3c5b-43c4-b42d-0f8d6e65566f", "user:delete", "Admin Delete user"),

  PAYMENT_CREATE(
      "4db331c8-f25a-435a-877b-b85d485cecb9", "payment:create", "Admin Create a payment"),
  PAYMENT_READ(
      "674a898a-c6a9-42bf-a647-37dda58beb9a", "payment:read", "Admin Read payment information"),
  PAYMENT_UPDATE("767424c4-0912-425b-a8ef-641067826c2f", "payment:update", "Admin Update payment"),
  PAYMENT_DELETE("c4ad66fe-ac7a-434f-843d-6a3d33f9a4bb", "payment:delete", "Admin Delete payment"),

  PERMISSION_CREATE(
      "1cad5fe0-075a-4c5c-98fe-6be660a730aa", "permission:create", "Admin Create a permission"),
  PERMISSION_READ(
      "36167cad-5d98-40cd-94b5-0398f55f5ef1",
      "permission:read",
      "Admin Read permission information"),
  PERMISSION_UPDATE(
      "ac7ead71-6edf-4920-b5d7-26fd4c2342f7", "permission:update", "Admin Update permission"),
  PERMISSION_DELETE(
      "00a5c4ab-d93e-4289-8745-ec349fe0c7d5", "permission:delete", "Admin Delete permission"),

  ROLE_CREATE("8859b27f-7249-430e-aebd-5c08827bef0b", "role:create", "Admin Create a role"),
  ROLE_READ("773b3e89-eaa3-4307-b623-b4451c08cb37", "role:read", "Admin Read role information"),
  ROLE_UPDATE("500d7a50-593b-4220-8589-bd0b5e90798d", "role:update", "Admin Update role"),
  ROLE_DELETE("46e33b99-daa3-44ef-9fe9-945aabc9ad3e", "role:delete", "Admin Delete role"),

  AUTHENTICATION_CONFIG_CREATE(
      "3ee109b4-ed6e-4dec-b541-2de7b47729bc",
      "authentication-config:create",
      "Admin Create a authentication-config"),
  AUTHENTICATION_CONFIG_READ(
      "c9d5256f-0df6-4698-b91a-dc67ffbbde18",
      "authentication-config:read",
      "Admin Read authentication-config information"),
  AUTHENTICATION_CONFIG_UPDATE(
      "cf390318-f6dd-4a64-b7a3-94f9a795d420",
      "authentication-config:update",
      "Admin Update authentication-config"),
  AUTHENTICATION_CONFIG_DELETE(
      "4ffae163-0d50-45f1-865c-59704626aef2",
      "authentication-config:delete",
      "Admin Delete authentication-config"),

  IDENTITY_VERIFICATION_CONFIG_CREATE(
      "73d96f0e-6971-468d-b97c-59b3789c9479",
      "identity-verification-config:create",
      "Admin Create a identity-verification-config"),
  IDENTITY_VERIFICATION_CONFIG_READ(
      "c7719370-11e1-46b0-8e37-f58a125bafc1",
      "identity-verification-config:read",
      "Admin Read identity-verification-config information"),
  IDENTITY_VERIFICATION_CONFIG_UPDATE(
      "3514733f-6259-4630-a3ff-6f486daf0266",
      "identity-verification-config:update",
      "Admin Update identity-verification-config"),
  IDENTITY_VERIFICATION_CONFIG_DELETE(
      "1052b34b-d045-498e-beda-97beab10661c",
      "identity-verification-config:delete",
      "Admin Delete identity-verification-config"),

  FEDERATION_CONFIG_CREATE(
      "03d5739b-5ca0-4455-a8bb-4af788dd417d",
      "federation-config:create",
      "Admin Create a federation-config"),
  FEDERATION_CONFIG_READ(
      "9ee87765-da60-48bd-af28-bc47d95da8a4",
      "federation-config:read",
      "Admin Read federation-config information"),
  FEDERATION_CONFIG_UPDATE(
      "728de86b-0576-4b8a-8cf5-1b0ce17cdd3d",
      "federation-config:update",
      "Admin Update federation-config"),
  FEDERATION_CONFIG_DELETE(
      "33c99336-390a-4d7f-bf8c-e4ca0e4a9ee4",
      "federation-config:delete",
      "Admin Delete federation-config"),

  SECURITY_EVENT_HOOK_CONFIG_CREATE(
      "5dffe047-5f34-4e36-a6cc-a56e56c73163",
      "security-event-hook-config:create",
      "Admin Create a security-event-hook-config"),
  SECURITY_EVENT_HOOK_CONFIG_READ(
      "895a630c-bea0-4cb5-9f98-8b4dc89a61c9",
      "security-event-hook-config:read",
      "Admin Read security-event-hook-config information"),
  SECURITY_EVENT_HOOK_CONFIG_UPDATE(
      "1c821f8b-a0d6-40f7-b4d0-becf2b120a19",
      "security-event-hook-config:update",
      "Admin Update security-event-hook-config"),
  SECURITY_EVENT_HOOK_CONFIG_DELETE(
      "dd26ac14-66b3-4f02-837d-cc0c1346b41e",
      "security-event-hook-config:delete",
      "Admin Delete security-event-hook-config");

  private final String id;
  private final String value;
  private final String description;

  AdminPermission(String id, String value, String description) {
    this.id = id;
    this.value = value;
    this.description = description;
  }

  public String id() {
    return id;
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

  public static Set<AdminPermission> getAll() {
    return Set.of(values());
  }

  public static Set<AdminPermission> findCreatePermissions() {
    return Arrays.stream(values())
        .filter(p -> p.value.endsWith(":create"))
        .collect(Collectors.toSet());
  }

  public static Set<AdminPermission> findReadPermissions() {
    return Arrays.stream(values())
        .filter(p -> p.value.endsWith(":read"))
        .collect(Collectors.toSet());
  }

  public static Set<AdminPermission> findUpdatePermissions() {
    return Arrays.stream(values())
        .filter(p -> p.value.endsWith(":update"))
        .collect(Collectors.toSet());
  }

  public static Set<AdminPermission> findDeletePermissions() {
    return Arrays.stream(values())
        .filter(p -> p.value.endsWith(":delete"))
        .collect(Collectors.toSet());
  }

  public Permission toPermission() {
    return new Permission(id, value, description);
  }

  public static Permissions toPermissions() {
    List<Permission> permissions = new ArrayList<>();
    for (AdminPermission adminPermission : values()) {
      permissions.add(adminPermission.toPermission());
    }
    return new Permissions(permissions);
  }
}

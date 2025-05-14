package org.idp.server.control_plane.base.definition;

public enum AdminPermission {
  ORGANIZATION_CREATE("organization:create"),
  ORGANIZATION_READ("organization:read"),
  ORGANIZATION_UPDATE("organization:update"),
  ORGANIZATION_DELETE("organization:delete"),

  TENANT_CREATE("tenant:create"),
  TENANT_READ("tenant:read"),
  TENANT_UPDATE("tenant:update"),
  TENANT_DELETE("tenant:delete"),

  AUTHORIZATION_SERVER_CREATE("authorization-server:create"),
  AUTHORIZATION_SERVER_READ("authorization-server:read"),
  AUTHORIZATION_SERVER_UPDATE("authorization-server:update"),
  AUTHORIZATION_SERVER_DELETE("authorization-server:delete"),

  CLIENT_CREATE("client:create"),
  CLIENT_READ("client:read"),
  CLIENT_UPDATE("client:update"),
  CLIENT_DELETE("client:delete"),

  USER_CREATE("user:create"),
  USER_READ("user:read"),
  USER_UPDATE("user:update"),
  USER_DELETE("user:delete"),

  PAYMENT_CREATE("payment:create"),
  PAYMENT_READ("payment:read"),
  PAYMENT_UPDATE("payment:update"),
  PAYMENT_DELETE("payment:delete"),

  PERMISSION_CREATE("permission:create"),
  PERMISSION_READ("permission:read"),
  PERMISSION_UPDATE("permission:update"),
  PERMISSION_DELETE("permission:delete"),

  ROLE_CREATE("role:create"),
  ROLE_READ("role:read"),
  ROLE_UPDATE("role:update"),
  ROLE_DELETE("role:delete"),

  AUTHENTICATION_CONFIG_CREATE("authentication-config:create"),
  AUTHENTICATION_CONFIG_READ("authentication-config:read"),
  AUTHENTICATION_CONFIG_UPDATE("authentication-config:update"),
  AUTHENTICATION_CONFIG_DELETE("authentication-config:delete"),

  FEDERATION_CONFIG_CREATE("federation-config:create"),
  FEDERATION_CONFIG_READ("federation-config:read"),
  FEDERATION_CONFIG_UPDATE("federation-config:update"),
  FEDERATION_CONFIG_DELETE("federation-config:delete"),

  IDENTITY_VERIFICATION_CONFIG_CREATE("identity-verification-config:create"),
  IDENTITY_VERIFICATION_CONFIG_READ("identity-verification-config:read"),
  IDENTITY_VERIFICATION_CONFIG_UPDATE("identity-verification-config:update"),
  IDENTITY_VERIFICATION_CONFIG_DELETE("identity-verification-config:delete"),

  SECURITY_EVENT_HOOK_CONFIG_CREATE("security-event-hook-config:create"),
  SECURITY_EVENT_HOOK_CONFIG_READ("security-event-hook-config:read"),
  SECURITY_EVENT_HOOK_CONFIG_UPDATE("security-event-hook-config:update"),
  SECURITY_EVENT_HOOK_CONFIG_DELETE("security-event-hook-config:delete");

  private final String value;

  AdminPermission(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean match(String permission) {
    return value.equals(permission);
  }
}

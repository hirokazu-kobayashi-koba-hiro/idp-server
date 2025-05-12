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
  ROLE_DELETE("role:delete");

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

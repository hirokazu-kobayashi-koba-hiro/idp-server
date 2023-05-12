package org.idp.sample;

public enum Tenant {
  sample("123", "https://server.example.com/123"),
  unsupported("999", "https://server.example.com/999");

  String id;
  String issuer;

  Tenant(String id, String issuer) {
    this.id = id;
    this.issuer = issuer;
  }

  public static Tenant of(String id) {
    for (Tenant tenant : Tenant.values()) {
      if (tenant.id.equals(id)) {
        return tenant;
      }
    }
    throw new RuntimeException(String.format("unregistered tenant (%s)", id));
  }

  public String id() {
    return id;
  }

  public String issuer() {
    return issuer;
  }
}

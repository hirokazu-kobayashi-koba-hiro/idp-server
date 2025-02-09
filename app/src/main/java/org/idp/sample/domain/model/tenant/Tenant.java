package org.idp.sample.domain.model.tenant;

public class Tenant {
  TenantIdentifier identifier;
  TenantName name;
  TenantType type;
  String issuer;

  public Tenant() {}

  public Tenant(TenantIdentifier identifier, TenantName name, TenantType type, String issuer) {
    this.identifier = identifier;
    this.name = name;
    this.type = type;
    this.issuer = issuer;
  }

  public TenantIdentifier identifier() {
    return identifier;
  }

  public String identifierValue() {
    return identifier.value();
  }

  public TenantName name() {
    return name;
  }

  public TenantType type() {
    return type;
  }

  public String issuer() {
    return issuer;
  }
}

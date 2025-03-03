package org.idp.server.domain.model.tenant;

import java.util.Objects;
import org.idp.server.core.type.oauth.TokenIssuer;

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

  public TokenIssuer tokenIssuer() {
    return new TokenIssuer(issuer);
  }

  public boolean isAdmin() {
    return type == TenantType.ADMIN;
  }

  public boolean isPublic() {
    return type == TenantType.PUBLIC;
  }

  public boolean exists() {
    return Objects.nonNull(identifier) && identifier.exists();
  }
}

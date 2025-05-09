package org.idp.server.core.multi_tenancy.tenant;

import java.net.URI;
import org.idp.server.basic.type.oauth.TokenIssuer;

public class TenantDomain {
  String value;

  public TenantDomain() {}

  public TenantDomain(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public TokenIssuer toTokenIssuer() {
    return new TokenIssuer(value);
  }

  public String host() {
    URI uri = URI.create(value);
    return uri.getHost();
  }
}

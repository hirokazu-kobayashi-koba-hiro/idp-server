package org.idp.server.platform.multi_tenancy.tenant;

import java.net.URI;

public class TenantDomain {
  String value;

  public TenantDomain() {}

  public TenantDomain(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public String toTokenIssuer() {
    return value;
  }

  public String host() {
    URI uri = URI.create(value);
    return uri.getHost();
  }
}

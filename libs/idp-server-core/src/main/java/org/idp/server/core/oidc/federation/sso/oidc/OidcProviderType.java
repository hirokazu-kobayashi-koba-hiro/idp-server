package org.idp.server.core.oidc.federation.sso.oidc;

import java.util.Objects;

public class OidcProviderType {
  String name;

  public OidcProviderType() {}

  public OidcProviderType(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    OidcProviderType federationType = (OidcProviderType) o;
    return Objects.equals(name, federationType.name);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name);
  }
}

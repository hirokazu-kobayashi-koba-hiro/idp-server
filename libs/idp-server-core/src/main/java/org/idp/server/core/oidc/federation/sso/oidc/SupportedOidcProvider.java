package org.idp.server.core.oidc.federation.sso.oidc;

import org.idp.server.core.oidc.federation.sso.SsoProvider;

public enum SupportedOidcProvider {
  Goggle("Standard"),
  Facebook("Facebook"),
  Yahoo("Yahoo");

  String type;

  SupportedOidcProvider(String type) {
    this.type = type;
  }

  public SsoProvider toSsoProvider() {
    return new SsoProvider(name());
  }
}

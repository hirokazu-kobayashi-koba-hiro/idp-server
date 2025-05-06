package org.idp.server.core.federation.oidc;

import org.idp.server.core.federation.SsoProvider;

public enum SupportedOidcProvider {
  Goggle("Standard"), Facebook("Facebook"), Yahoo("Yahoo");

  String type;

  SupportedOidcProvider(String type) {
    this.type = type;
  }

  public SsoProvider toSsoProvider() {
    return new SsoProvider(name());
  }
}

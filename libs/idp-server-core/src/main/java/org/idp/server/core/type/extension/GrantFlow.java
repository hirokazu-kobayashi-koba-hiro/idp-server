package org.idp.server.core.type.extension;

public enum GrantFlow {
  authorization_code,
  oauth_implicit,
  oidc_implicit,
  oidc_id_token_only_implicit,
  resource_owner_password,
  hybrid,
  ciba;

  public boolean isAuthorizationCodeFlow() {
    return this == authorization_code;
  }

  public boolean isOidcIdTokenOnlyImplicitFlow() {
    return this == oidc_id_token_only_implicit;
  }
}

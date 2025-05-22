package org.idp.server.federation.sso.oidc;

import org.idp.server.core.oidc.federation.sso.SsoProvider;

public interface OidcSsoExecutor {

  SsoProvider type();

  OidcTokenResponse requestToken(OidcTokenRequest oidcTokenRequest);

  OidcJwksResponse getJwks(OidcJwksRequest oidcJwksRequest);

  OidcUserinfoResponse requestUserInfo(OidcUserinfoRequest oidcUserinfoRequest);
}

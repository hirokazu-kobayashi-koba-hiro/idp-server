package org.idp.server.core.federation.sso.oidc;

import org.idp.server.core.federation.sso.SsoProvider;

public interface OidcSsoExecutor {

  SsoProvider type();

  OidcTokenResponse requestToken(OidcTokenRequest oidcTokenRequest);

  OidcJwksResponse getJwks(OidcJwksRequest oidcJwksRequest);

  OidcUserinfoResponse requestUserInfo(OidcUserinfoRequest oidcUserinfoRequest);
}

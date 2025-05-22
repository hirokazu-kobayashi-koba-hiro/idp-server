/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.federation.sso.oidc;

import org.idp.server.core.oidc.federation.sso.SsoProvider;

public interface OidcSsoExecutor {

  SsoProvider type();

  OidcTokenResponse requestToken(OidcTokenRequest oidcTokenRequest);

  OidcJwksResponse getJwks(OidcJwksRequest oidcJwksRequest);

  OidcUserinfoResponse requestUserInfo(OidcUserinfoRequest oidcUserinfoRequest);
}

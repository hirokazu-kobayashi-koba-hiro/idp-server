/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.verifier;

import org.idp.server.core.oidc.AuthorizationProfile;
import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.core.oidc.verifier.base.OidcRequestBaseVerifier;

public class OidcRequestVerifier implements AuthorizationRequestVerifier {

  OidcRequestBaseVerifier baseVerifier = new OidcRequestBaseVerifier();

  public AuthorizationProfile profile() {
    return AuthorizationProfile.OIDC;
  }

  @Override
  public void verify(OAuthRequestContext context) {
    baseVerifier.verify(context);
  }
}

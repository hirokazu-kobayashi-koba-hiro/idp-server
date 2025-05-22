/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.verifier.extension;

import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.core.oidc.verifier.AuthorizationRequestExtensionVerifier;

public class JarmVerifier implements AuthorizationRequestExtensionVerifier {

  public boolean shouldNotVerify(OAuthRequestContext oAuthRequestContext) {
    return !oAuthRequestContext.responseMode().isJwtMode();
  }

  @Override
  public void verify(OAuthRequestContext oAuthRequestContext) {}
}

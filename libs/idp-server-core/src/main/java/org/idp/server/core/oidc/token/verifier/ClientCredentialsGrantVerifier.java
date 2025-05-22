/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.token.verifier;

import org.idp.server.basic.type.oauth.Scopes;
import org.idp.server.core.oidc.token.exception.TokenBadRequestException;

public class ClientCredentialsGrantVerifier {

  Scopes scopes;

  public ClientCredentialsGrantVerifier(Scopes scopes) {
    this.scopes = scopes;
  }

  public void verify() {
    throwExceptionIfInvalidScope();
  }

  void throwExceptionIfInvalidScope() {
    if (!scopes.exists()) {
      throw new TokenBadRequestException(
          "invalid_scope", "token request does not contains valid scope");
    }
  }
}

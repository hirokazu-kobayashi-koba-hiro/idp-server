/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.token.tokenrevocation.validator;

import org.idp.server.core.oidc.token.tokenrevocation.TokenRevocationRequestParameters;
import org.idp.server.core.oidc.token.tokenrevocation.exception.TokenRevocationBadRequestException;

public class TokenRevocationValidator {

  TokenRevocationRequestParameters parameters;

  public TokenRevocationValidator(TokenRevocationRequestParameters parameters) {
    this.parameters = parameters;
  }

  public void validate() {
    if (!parameters.hasToken()) {
      throw new TokenRevocationBadRequestException(
          "invalid_request", "token revocation request must contains token parameters");
    }
  }
}

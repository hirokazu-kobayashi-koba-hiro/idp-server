/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.token.tokenintrospection.validator;

import org.idp.server.core.oidc.token.tokenintrospection.TokenIntrospectionRequestParameters;
import org.idp.server.core.oidc.token.tokenintrospection.exception.TokenIntrospectionBadRequestException;

public class TokenIntrospectionValidator {

  TokenIntrospectionRequestParameters parameters;

  public TokenIntrospectionValidator(TokenIntrospectionRequestParameters parameters) {
    this.parameters = parameters;
  }

  public void validate() {
    if (!parameters.hasToken()) {
      throw new TokenIntrospectionBadRequestException(
          "invalid_request", "token introspection request must contains token parameters");
    }
  }
}

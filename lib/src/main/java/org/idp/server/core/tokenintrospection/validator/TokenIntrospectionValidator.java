package org.idp.server.core.tokenintrospection.validator;

import org.idp.server.core.tokenintrospection.TokenIntrospectionRequestParameters;
import org.idp.server.core.tokenintrospection.exception.TokenIntrospectionBadRequestException;

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

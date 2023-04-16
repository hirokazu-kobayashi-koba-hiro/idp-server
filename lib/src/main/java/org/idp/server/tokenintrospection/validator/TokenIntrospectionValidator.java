package org.idp.server.tokenintrospection.validator;

import org.idp.server.tokenintrospection.TokenIntrospectionRequestParameters;
import org.idp.server.tokenintrospection.exception.TokenIntrospectionBadRequestException;

public class TokenIntrospectionValidator {

  public void validate(TokenIntrospectionRequestParameters parameters) {
    if (!parameters.hasToken()) {
      throw new TokenIntrospectionBadRequestException(
          "invalid_request", "token introspection request must contains token parameters");
    }
  }
}

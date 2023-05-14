package org.idp.server.token.validator;

import org.idp.server.token.TokenRequestParameters;
import org.idp.server.token.exception.TokenBadRequestException;

public class TokenRequestCodeGrantValidator {
  TokenRequestParameters parameters;

  public TokenRequestCodeGrantValidator(TokenRequestParameters parameters) {
    this.parameters = parameters;
  }

  public void validate() {
    throwExceptionIfNotContainsAuthorizationCode();
  }

  void throwExceptionIfNotContainsAuthorizationCode() {
    if (!parameters.hasCode()) {
      throw new TokenBadRequestException(
          "token request does not contains code, authorization_code grant must contains code");
    }
  }
}

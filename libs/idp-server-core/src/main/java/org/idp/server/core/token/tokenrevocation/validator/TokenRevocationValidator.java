package org.idp.server.core.token.tokenrevocation.validator;

import org.idp.server.core.token.tokenrevocation.TokenRevocationRequestParameters;
import org.idp.server.core.token.tokenrevocation.exception.TokenRevocationBadRequestException;

public class TokenRevocationValidator {

  TokenRevocationRequestParameters parameters;

  public TokenRevocationValidator(TokenRevocationRequestParameters parameters) {
    this.parameters = parameters;
  }

  public void validate() {
    if (!parameters.hasToken()) {
      throw new TokenRevocationBadRequestException("invalid_request", "token revocation request must contains token parameters");
    }
  }
}

package org.idp.server.tokenrevocation.validator;

import org.idp.server.tokenrevocation.TokenRevocationRequestParameters;
import org.idp.server.tokenrevocation.exception.TokenRevocationBadRequestException;

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

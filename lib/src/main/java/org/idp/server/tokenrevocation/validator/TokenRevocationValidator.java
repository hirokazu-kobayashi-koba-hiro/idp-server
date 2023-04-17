package org.idp.server.tokenrevocation.validator;

import org.idp.server.tokenrevocation.TokenRevocationRequestParameters;
import org.idp.server.tokenrevocation.exception.TokenRevocationBadRequestException;

public class TokenRevocationValidator {

  public void validate(TokenRevocationRequestParameters parameters) {
    if (!parameters.hasToken()) {
      throw new TokenRevocationBadRequestException(
          "invalid_request", "token revocation request must contains token parameters");
    }
  }
}

package org.idp.server.token.validator;

import org.idp.server.token.TokenRequestContext;

public class TokenRequestValidator {

  TokenRequestContext tokenRequestContext;

  public TokenRequestValidator(TokenRequestContext tokenRequestContext) {
    this.tokenRequestContext = tokenRequestContext;
  }

  public void validate() {}
}

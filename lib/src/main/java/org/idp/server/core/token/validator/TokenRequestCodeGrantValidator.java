package org.idp.server.core.token.validator;

import org.idp.server.core.token.TokenRequestContext;
import org.idp.server.core.token.exception.TokenBadRequestException;
import org.idp.server.core.type.oauth.GrantType;

public class TokenRequestCodeGrantValidator {

  public void validate(TokenRequestContext tokenRequestContext) {
    throwIfNotContainsAuthorizationCode(tokenRequestContext);
    throwIfUnSupportedGrantTypeWithServer(tokenRequestContext);
  }

  void throwIfUnSupportedGrantTypeWithClient(TokenRequestContext tokenRequestContext) {
    if (!tokenRequestContext.isSupportedGrantTypeWithClient(GrantType.authorization_code)) {
      throw new TokenBadRequestException(
          "this request grant_type is authorization_code, but client does not support");
    }
  }

  void throwIfUnSupportedGrantTypeWithServer(TokenRequestContext tokenRequestContext) {
    if (!tokenRequestContext.isSupportedGrantTypeWithServer(GrantType.authorization_code)) {
      throw new TokenBadRequestException(
          "this request grant_type is authorization_code, but authorization server does not support");
    }
  }

  void throwIfNotContainsAuthorizationCode(TokenRequestContext tokenRequestContext) {
    if (!tokenRequestContext.hasCode()) {
      throw new TokenBadRequestException(
          "token request does not contains code, authorization_code grant must contains code");
    }
  }
}

package org.idp.server.core.oauth.validator;

import org.idp.server.core.oauth.TokenRequestContext;
import org.idp.server.core.oauth.exception.TokenBadRequestException;
import org.idp.server.core.type.GrantType;

public class TokenRequestCodeGrantValidator {

  public void validate(TokenRequestContext tokenRequestContext) {
    throwIfNotContainsAuthorizationCode(tokenRequestContext);
    throwIfUnSupportedGrantTypeWithServer(tokenRequestContext);
  }

  void throwIfUnSupportedGrantTypeWithClient(TokenRequestContext tokenRequestContext) {
    if (tokenRequestContext.isSupportedGrantTypeWithClient(GrantType.authorization_code)) {
      throw new TokenBadRequestException("client ");
    }
  }

  void throwIfUnSupportedGrantTypeWithServer(TokenRequestContext tokenRequestContext) {
    if (tokenRequestContext.isSupportedGrantTypeWithServer(GrantType.authorization_code)) {
      throw new TokenBadRequestException("");
    }
  }

  void throwIfNotContainsAuthorizationCode(TokenRequestContext tokenRequestContext) {
    if (!tokenRequestContext.hasCode()) {
      throw new TokenBadRequestException(
          "token request does not contains code, authorization_code grant must contains code");
    }
  }
}

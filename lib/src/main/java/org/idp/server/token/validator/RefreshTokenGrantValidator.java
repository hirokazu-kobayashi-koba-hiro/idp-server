package org.idp.server.token.validator;

import org.idp.server.token.TokenRequestContext;
import org.idp.server.token.exception.TokenBadRequestException;
import org.idp.server.type.oauth.GrantType;

public class RefreshTokenGrantValidator {

  public void validate(TokenRequestContext tokenRequestContext) {
    throwIfNotContainsRefreshToken(tokenRequestContext);
    throwIfUnSupportedGrantTypeWithServer(tokenRequestContext);
    throwIfUnSupportedGrantTypeWithClient(tokenRequestContext);
  }

  void throwIfUnSupportedGrantTypeWithClient(TokenRequestContext tokenRequestContext) {
    if (!tokenRequestContext.isSupportedGrantTypeWithClient(GrantType.refresh_token)) {
      throw new TokenBadRequestException(
          "this request grant_type is refresh_token, but client does not support");
    }
  }

  void throwIfUnSupportedGrantTypeWithServer(TokenRequestContext tokenRequestContext) {
    if (!tokenRequestContext.isSupportedGrantTypeWithServer(GrantType.refresh_token)) {
      throw new TokenBadRequestException(
          "this request grant_type is refresh_token, but authorization server does not support");
    }
  }

  void throwIfNotContainsRefreshToken(TokenRequestContext tokenRequestContext) {
    if (!tokenRequestContext.hasRefreshToken()) {
      throw new TokenBadRequestException(
          "token request does not contains code, authorization_code grant must contains code");
    }
  }
}

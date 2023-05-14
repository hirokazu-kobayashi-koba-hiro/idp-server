package org.idp.server.token.validator;

import org.idp.server.token.TokenRequestContext;
import org.idp.server.token.exception.TokenBadRequestException;
import org.idp.server.type.oauth.GrantType;

public class RefreshTokenGrantValidator {

  TokenRequestContext tokenRequestContext;

  public RefreshTokenGrantValidator(TokenRequestContext tokenRequestContext) {
    this.tokenRequestContext = tokenRequestContext;
  }

  public void validate() {
    throwExceptionIfNotContainsRefreshToken();
    throwExceptionIfUnSupportedGrantTypeWithServer();
    throwExceptionIfUnSupportedGrantTypeWithClient();
    throwExceptionIfNotContainsUsername();
    throwExceptionIfNotContainsPassword();
    throwExceptionIfNotContainsClientId();
  }

  void throwExceptionIfUnSupportedGrantTypeWithClient() {
    if (!tokenRequestContext.isSupportedGrantTypeWithClient(GrantType.refresh_token)) {
      throw new TokenBadRequestException(
          "this request grant_type is refresh_token, but client does not support");
    }
  }

  void throwExceptionIfUnSupportedGrantTypeWithServer() {
    if (!tokenRequestContext.isSupportedGrantTypeWithServer(GrantType.refresh_token)) {
      throw new TokenBadRequestException(
          "this request grant_type is refresh_token, but authorization server does not support");
    }
  }

  void throwExceptionIfNotContainsRefreshToken() {
    if (!tokenRequestContext.hasRefreshToken()) {
      throw new TokenBadRequestException(
          "token request does not contains code, authorization_code grant must contains code");
    }
  }

  void throwExceptionIfNotContainsUsername() {
    if (!tokenRequestContext.hasUsername()) {
      throw new TokenBadRequestException(
              "token request does not contains username, password grant must contains username");
    }
  }

  void throwExceptionIfNotContainsPassword() {
    if (!tokenRequestContext.hasPassword()) {
      throw new TokenBadRequestException(
              "token request does not contains password, password grant must contains password");
    }
  }

  void throwExceptionIfNotContainsClientId() {
    if (tokenRequestContext.hasClientSecretBasic()) {
      return;
    }
    if (!tokenRequestContext.hasClientId()) {
      throw new TokenBadRequestException(
              "token request does not contains client_id, password must contains client_id");
    }
  }
}

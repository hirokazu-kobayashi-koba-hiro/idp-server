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
    throwExceptionIfNotContainsClientId();
  }

  void throwExceptionIfUnSupportedGrantTypeWithClient() {
    if (!tokenRequestContext.isSupportedGrantTypeWithClient(GrantType.refresh_token)) {
      throw new TokenBadRequestException(
          "unauthorized_client",
          "this request grant_type is refresh_token, but client does not support");
    }
  }

  void throwExceptionIfUnSupportedGrantTypeWithServer() {
    if (!tokenRequestContext.isSupportedGrantTypeWithServer(GrantType.refresh_token)) {
      throw new TokenBadRequestException(
          "unsupported_grant_type",
          "this request grant_type is refresh_token, but authorization server does not support");
    }
  }

  void throwExceptionIfNotContainsRefreshToken() {
    if (!tokenRequestContext.hasRefreshToken()) {
      throw new TokenBadRequestException(
          "token request does not contains refresh_token, refresh_token grant must contains refresh_token");
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

package org.idp.server.token.validator;

import org.idp.server.token.TokenRequestContext;
import org.idp.server.token.exception.TokenBadRequestException;
import org.idp.server.type.oauth.GrantType;

public class CibaGrantValidator {

  TokenRequestContext tokenRequestContext;

  public CibaGrantValidator(TokenRequestContext tokenRequestContext) {
    this.tokenRequestContext = tokenRequestContext;
  }

  public void validate() {
    throwExceptionIfUnSupportedGrantTypeWithServer();
    throwExceptionIfUnSupportedGrantTypeWithClient();
    throwExceptionIfNotContainsAuthReqId();
    throwExceptionIfNotContainsClientId();
  }

  void throwExceptionIfUnSupportedGrantTypeWithClient() {
    if (!tokenRequestContext.isSupportedGrantTypeWithClient(GrantType.ciba)) {
      throw new TokenBadRequestException(
           "unauthorized_client",
          "this request grant_type is ciba, but client does not authorize");
    }
  }

  void throwExceptionIfUnSupportedGrantTypeWithServer() {
    if (!tokenRequestContext.isSupportedGrantTypeWithServer(GrantType.ciba)) {
      throw new TokenBadRequestException(
          "unsupported_grant_type",
          "this request grant_type is ciba, but authorization server does not support");
    }
  }

  void throwExceptionIfNotContainsAuthReqId() {
    if (!tokenRequestContext.hasAuthReqId()) {
      throw new TokenBadRequestException(
          "token request does not contains auth_req_id, ciba grant must contains auth_req_id");
    }
  }

  void throwExceptionIfNotContainsClientId() {
    if (tokenRequestContext.hasClientSecretBasic()) {
      return;
    }
    if (!tokenRequestContext.hasClientId()) {
      throw new TokenBadRequestException(
          "token request does not contains client_id, ciba grant must contains client_id");
    }
  }
}

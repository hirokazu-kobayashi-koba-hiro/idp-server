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
    throwIfUnSupportedGrantTypeWithServer();
    throwIfUnSupportedGrantTypeWithClient();
    throwIfNotContainsAuthReqId();
    throwIfNotContainsClientId();
  }

  void throwIfUnSupportedGrantTypeWithClient() {
    if (!tokenRequestContext.isSupportedGrantTypeWithClient(GrantType.ciba)) {
      throw new TokenBadRequestException(
          "this request grant_type is ciba, but client does not support");
    }
  }

  void throwIfUnSupportedGrantTypeWithServer() {
    if (!tokenRequestContext.isSupportedGrantTypeWithServer(GrantType.ciba)) {
      throw new TokenBadRequestException(
          "this request grant_type is ciba, but authorization server does not support");
    }
  }

  void throwIfNotContainsAuthReqId() {
    if (!tokenRequestContext.hasAuthReqId()) {
      throw new TokenBadRequestException(
          "token request does not contains code, ciba grant must contains auth_req_id");
    }
  }

  void throwIfNotContainsClientId() {
    if (tokenRequestContext.hasClientSecretBasic()) {
      return;
    }
    if (!tokenRequestContext.hasClientId()) {
      throw new TokenBadRequestException(
          "token request does not contains client_id, ciba grant must contains client_id");
    }
  }
}

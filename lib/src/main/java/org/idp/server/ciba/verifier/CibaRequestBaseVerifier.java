package org.idp.server.ciba.verifier;

import org.idp.server.ciba.CibaRequestContext;
import org.idp.server.ciba.exception.BackchannelAuthenticationBadRequestException;

public class CibaRequestBaseVerifier {

  public void verify(CibaRequestContext context) {
    throwIfNotContainsOpenidScope(context);
    throwIfNotContainsAnyHint(context);
  }

  void throwIfNotContainsOpenidScope(CibaRequestContext context) {
    if (!context.hasOpenidScope()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_scope",
          "backchannel request does not contains openid scope. OpenID Connect implements authentication as an extension to OAuth 2.0 by including the openid scope value in the authorization requests.");
    }
  }

  void throwIfNotContainsAnyHint(CibaRequestContext context) {
    if (!context.hasAnyHint()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request",
          "backchannel request does not have any hint, must contains login_hint or login_hint_token or id_token_hint");
    }
  }
}

package org.idp.server.core.ciba.service;

import org.idp.server.core.ciba.CibaRequestContext;
import org.idp.server.core.ciba.CibaRequestDelegate;
import org.idp.server.core.ciba.UserCriteria;
import org.idp.server.core.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.core.oauth.identity.User;

public class UserService {
  CibaRequestDelegate cibaRequestDelegate;
  CibaRequestContext context;

  public UserService(CibaRequestDelegate cibaRequestDelegate, CibaRequestContext context) {
    this.cibaRequestDelegate = cibaRequestDelegate;
    this.context = context;
  }

  public User getAndNotify() {
    BackchannelAuthenticationRequest backchannelAuthenticationRequest =
        context.backchannelAuthenticationRequest();
    User user =
        cibaRequestDelegate.find(
            context.tokenIssuer(),
            new UserCriteria(
                backchannelAuthenticationRequest.loginHint(),
                backchannelAuthenticationRequest.loginHintToken(),
                backchannelAuthenticationRequest.idTokenHint()));
    if (!user.exists()) {
      throw new BackchannelAuthenticationBadRequestException(
          "unknown_user_id",
          "The OpenID Provider is not able to identify which end-user the Client wishes to be authenticated by means of the hint provided in the request (login_hint_token, id_token_hint, or login_hint).");
    }
    if (context.hasUserCode()) {
      boolean authenticationResult =
          cibaRequestDelegate.authenticate(context.tokenIssuer(), user, context.userCode());
      if (!authenticationResult) {
        throw new BackchannelAuthenticationBadRequestException(
            "invalid_user_code", "backchannel authentication request user_code is invalid");
      }
    }

    cibaRequestDelegate.notify(context.tokenIssuer(), user, backchannelAuthenticationRequest);
    return user;
  }
}

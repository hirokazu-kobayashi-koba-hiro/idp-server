package org.idp.server.core.protocol;

import org.idp.server.core.handler.oauth.io.*;
import org.idp.server.core.oauth.OAuthRequestDelegate;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;

public interface OAuthProtocol {

  OAuthRequestResponse request(OAuthRequest oAuthRequest);

  OAuthViewDataResponse getViewData(OAuthViewDataRequest request);

  AuthorizationRequest get(AuthorizationRequestIdentifier authorizationRequestIdentifier);

  OAuthAuthorizeResponse authorize(OAuthAuthorizeRequest request);

  OAuthDenyResponse deny(OAuthDenyRequest request);

  void setOAuthRequestDelegate(OAuthRequestDelegate oAuthRequestDelegate);

  OAuthLogoutResponse logout(OAuthLogoutRequest oAuthLogoutRequest);
}

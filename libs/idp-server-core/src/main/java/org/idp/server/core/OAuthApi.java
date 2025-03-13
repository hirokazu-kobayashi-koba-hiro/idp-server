package org.idp.server.core;

import org.idp.server.core.handler.oauth.io.*;
import org.idp.server.core.oauth.OAuthRequestDelegate;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;

public interface OAuthApi {

  OAuthRequestResponse request(OAuthRequest oAuthRequest);

  OAuthViewDataResponse getViewData(OAuthViewDataRequest request);

  AuthorizationRequest get(AuthorizationRequestIdentifier authorizationRequestIdentifier);

  OAuthAuthenticationUpdateResponse updateAuthentication(OAuthAuthenticationUpdateRequest request);

  OAuthAuthorizeResponse authorize(OAuthAuthorizeRequest request);

  OAuthDenyResponse deny(OAuthDenyRequest request);

  void setOAuthRequestDelegate(OAuthRequestDelegate oAuthRequestDelegate);

  OAuthLogoutResponse logout(OAuthLogoutRequest oAuthLogoutRequest);
}

package org.idp.server.api;

import org.idp.server.handler.oauth.io.*;
import org.idp.server.oauth.OAuthRequestDelegate;

public interface OAuthApi {

  OAuthRequestResponse request(OAuthRequest oAuthRequest);

  OAuthAuthorizeResponse authorize(OAuthAuthorizeRequest request);

  OAuthDenyResponse deny(OAuthDenyRequest request);

  void setOAuthRequestDelegate(OAuthRequestDelegate oAuthRequestDelegate);
}

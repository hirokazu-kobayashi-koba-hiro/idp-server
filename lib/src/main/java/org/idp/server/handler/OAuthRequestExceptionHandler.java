package org.idp.server.handler;

import org.idp.server.core.oauth.exception.OAuthException;
import org.idp.server.io.OAuthRequestResponse;

public class OAuthRequestExceptionHandler {

  public OAuthRequestResponse handle(OAuthException oAuthException) {

    return new OAuthRequestResponse();
  }
}

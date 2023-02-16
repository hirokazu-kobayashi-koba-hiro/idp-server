package org.idp.server.io;

import org.idp.server.core.oauth.params.OAuthRequestResult;

/** OAuthRequestResponse */
public class OAuthRequestResponse {
  OAuthRequestResult result;

  public OAuthRequestResponse() {}

  public OAuthRequestResponse(OAuthRequestResult result) {
    this.result = result;
  }

  public OAuthRequestResult getResult() {
    return result;
  }
}

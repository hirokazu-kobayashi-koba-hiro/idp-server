package org.idp.server.core.oauth;

import org.idp.server.core.oauth.request.OAuthRequestParameters;

/** OAuthRequestAnalyzer */
public class OAuthRequestAnalyzer {

  OAuthRequestParameters parameters;

  public OAuthRequestAnalyzer(OAuthRequestParameters parameters) {
    this.parameters = parameters;
  }

  public OAuthRequestPattern analyzePattern() {
    if (parameters.hasRequest()) {
      return OAuthRequestPattern.REQUEST_OBJECT;
    }
    if (parameters.hasRequestUri()) {
      return OAuthRequestPattern.REQUEST_URI;
    }
    return OAuthRequestPattern.NORMAL;
  }

  public AuthorizationProfile analyzeProfile() {

    return AuthorizationProfile.OAUTH2;
  }
}

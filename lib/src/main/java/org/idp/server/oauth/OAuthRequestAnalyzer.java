package org.idp.server.oauth;

/** OAuthRequestAnalyzer */
public class OAuthRequestAnalyzer {

  public OAuthRequestPattern analyzePattern(OAuthRequestParameters parameters) {
    if (parameters.hasRequest()) {
      return OAuthRequestPattern.REQUEST_OBJECT;
    }
    if (parameters.hasRequestUri()) {
      return OAuthRequestPattern.REQUEST_URI;
    }
    return OAuthRequestPattern.NORMAL;
  }

  public AuthorizationProfile analyzeProfile(OAuthRequestParameters parameters) {

    return AuthorizationProfile.OAUTH2;
  }
}

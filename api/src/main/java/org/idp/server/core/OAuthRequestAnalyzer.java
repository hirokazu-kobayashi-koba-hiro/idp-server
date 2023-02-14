package org.idp.server.core;

/**
 * OAuthRequestAnalyzer
 */
public class OAuthRequestAnalyzer {

    public AuthorizationProfile analyze(OAuthRequestParameters oAuthRequestParameters) {
        return AuthorizationProfile.OAUTH;
    }
}

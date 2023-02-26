package org.idp.server.oauth;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.type.OAuthRequestParameters;

/** AuthorizationProfileAnalyzable */
public interface AuthorizationProfileAnalyzable {

  default AuthorizationProfile analyze(
      OAuthRequestParameters parameters,
      JoseContext joseContext,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {

    return AuthorizationProfile.OAUTH2;
  }
}

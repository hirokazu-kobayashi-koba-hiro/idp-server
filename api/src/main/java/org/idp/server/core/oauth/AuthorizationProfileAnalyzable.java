package org.idp.server.core.oauth;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.type.OAuthRequestParameters;

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

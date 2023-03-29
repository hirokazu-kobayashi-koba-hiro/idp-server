package org.idp.server.core.oauth.factory;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.AuthorizationProfile;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.type.OAuthRequestParameters;

/** AuthorizationRequestFactory */
public interface AuthorizationRequestFactory {
  AuthorizationRequest create(
          AuthorizationProfile profile,
      OAuthRequestParameters parameters,
      JoseContext joseContext,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration);
}

package org.idp.server.service;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JoseHandler;
import org.idp.server.basic.jose.JoseInvalidException;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.AuthorizationProfile;
import org.idp.server.oauth.AuthorizationProfileAnalyzable;
import org.idp.server.oauth.OAuthBadRequestException;
import org.idp.server.oauth.OAuthRequestPattern;
import org.idp.server.oauth.request.OAuthRequestContext;
import org.idp.server.oauth.request.OAuthRequestContextService;
import org.idp.server.type.OAuthRequestParameters;

/** RequestObjectPatternContextService */
public class RequestObjectPatternContextService
    implements OAuthRequestContextService, AuthorizationProfileAnalyzable {

  @Override
  public OAuthRequestContext create(
      OAuthRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    try {
      JoseHandler joseHandler = new JoseHandler();
      JoseContext joseContext =
          joseHandler.handle(
              parameters.request(),
              clientConfiguration.jwks(),
              serverConfiguration.jwks(),
              clientConfiguration.clientSecret());
      AuthorizationProfile profile =
          analyze(parameters, joseContext, serverConfiguration, clientConfiguration);
      return new OAuthRequestContext(
          profile,
          OAuthRequestPattern.NORMAL,
          parameters,
          joseContext,
          serverConfiguration,
          clientConfiguration);
    } catch (JoseInvalidException exception) {
      throw new OAuthBadRequestException(exception.getMessage(), exception);
    }
  }
}

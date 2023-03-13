package org.idp.server.service;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JoseHandler;
import org.idp.server.basic.jose.JoseInvalidException;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.AuthorizationProfile;
import org.idp.server.core.oauth.AuthorizationProfileAnalyzable;
import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.oauth.OAuthRequestPattern;
import org.idp.server.core.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.oauth.factory.RequestObjectPatternFactory;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.OAuthRequestContextService;
import org.idp.server.core.type.OAuthRequestParameters;

/** RequestObjectPatternContextService */
public class RequestObjectPatternContextService
    implements OAuthRequestContextService, AuthorizationProfileAnalyzable {

  RequestObjectPatternFactory requestObjectPatternFactory = new RequestObjectPatternFactory();

  @Override
  public OAuthRequestContext create(
      OAuthRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    try {
      JoseHandler joseHandler = new JoseHandler();
      JoseContext joseContext =
          joseHandler.handle(
              parameters.request().value(),
              clientConfiguration.jwks(),
              serverConfiguration.jwks(),
              clientConfiguration.clientSecret());
      joseContext.verifySignature();
      AuthorizationProfile profile =
          analyze(parameters, joseContext, serverConfiguration, clientConfiguration);
      AuthorizationRequest authorizationRequest =
          requestObjectPatternFactory.create(
              parameters, joseContext, serverConfiguration, clientConfiguration);

      return new OAuthRequestContext(
          profile,
          OAuthRequestPattern.NORMAL,
          parameters,
          joseContext,
          authorizationRequest,
          serverConfiguration,
          clientConfiguration);
    } catch (JoseInvalidException exception) {
      throw new OAuthBadRequestException(exception.getMessage(), exception);
    }
  }
}

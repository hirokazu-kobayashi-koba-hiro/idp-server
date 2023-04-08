package org.idp.server.service;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JoseHandler;
import org.idp.server.basic.jose.JoseInvalidException;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.gateway.RequestObjectGateway;
import org.idp.server.core.oauth.*;
import org.idp.server.core.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.oauth.factory.RequestObjectPatternFactory;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.OAuthRequestContextService;
import org.idp.server.core.type.OAuthRequestParameters;
import org.idp.server.core.type.oidc.RequestObject;

/** RequestUriPatternContextService */
public class RequestUriPatternContextService
    implements OAuthRequestContextService, AuthorizationProfileAnalyzable {

  RequestObjectGateway requestObjectGateway;
  RequestObjectPatternFactory requestObjectPatternFactory = new RequestObjectPatternFactory();

  public RequestUriPatternContextService(RequestObjectGateway requestObjectGateway) {
    this.requestObjectGateway = requestObjectGateway;
  }

  @Override
  public OAuthRequestContext create(
      OAuthRequestParameters parameters,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    try {
      if (!clientConfiguration.isRegisteredRequestUri(parameters.requestUri().value())) {
        throw new OAuthBadRequestException(
            String.format(
                "request uri does not registered (%s)", parameters.redirectUri().value()));
      }
      RequestObject requestObject = requestObjectGateway.get(parameters.requestUri());
      JoseHandler joseHandler = new JoseHandler();
      JoseContext joseContext =
          joseHandler.handle(
              requestObject.value(),
              clientConfiguration.jwks(),
              serverConfiguration.jwks(),
              clientConfiguration.clientSecret());
      joseContext.verifySignature();
      AuthorizationProfile profile =
          analyze(parameters, joseContext, serverConfiguration, clientConfiguration);
      AuthorizationRequest authorizationRequest =
          requestObjectPatternFactory.create(
              profile, parameters, joseContext, serverConfiguration, clientConfiguration);
      return new OAuthRequestContext(
          OAuthRequestPattern.REQUEST_URI,
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

package org.idp.server.oauth.service;

import java.util.Set;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JoseHandler;
import org.idp.server.basic.jose.JoseInvalidException;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.AuthorizationProfile;
import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.OAuthRequestParameters;
import org.idp.server.oauth.OAuthRequestPattern;
import org.idp.server.oauth.exception.OAuthBadRequestException;
import org.idp.server.oauth.factory.AuthorizationRequestFactory;
import org.idp.server.oauth.gateway.RequestObjectGateway;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.type.oidc.RequestObject;

/** RequestUriPatternContextService */
public class RequestUriPatternContextService implements OAuthRequestContextService {

  RequestObjectGateway requestObjectGateway;

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
            "invalid_request",
            String.format("request uri does not registered (%s)", parameters.requestUri().value()));
      }
      RequestObject requestObject = requestObjectGateway.get(parameters.requestUri());
      JoseHandler joseHandler = new JoseHandler();
      JoseContext joseContext =
          joseHandler.handle(
              requestObject.value(),
              clientConfiguration.jwks(),
              serverConfiguration.jwks(),
              clientConfiguration.clientSecretValue());
      joseContext.verifySignature();
      OAuthRequestPattern pattern = OAuthRequestPattern.REQUEST_URI;
      Set<String> filteredScopes =
          filterScopes(pattern, parameters, joseContext, clientConfiguration);
      AuthorizationProfile profile = analyze(filteredScopes, serverConfiguration);
      AuthorizationRequestFactory requestFactory =
          selectAuthorizationRequestFactory(profile, serverConfiguration, clientConfiguration);
      AuthorizationRequest authorizationRequest =
          requestFactory.create(
              profile,
              parameters,
              joseContext,
              filteredScopes,
              serverConfiguration,
              clientConfiguration);
      return new OAuthRequestContext(
          pattern,
          parameters,
          joseContext,
          authorizationRequest,
          serverConfiguration,
          clientConfiguration);
    } catch (JoseInvalidException exception) {
      throw new OAuthBadRequestException("invalid_request", exception.getMessage(), exception);
    }
  }
}

package org.idp.server.core.oidc.context;

import java.util.Set;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JoseHandler;
import org.idp.server.basic.jose.JoseInvalidException;
import org.idp.server.basic.type.oidc.RequestObject;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.AuthorizationProfile;
import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.core.oidc.OAuthRequestPattern;
import org.idp.server.core.oidc.configuration.ClientConfiguration;
import org.idp.server.core.oidc.configuration.ServerConfiguration;
import org.idp.server.core.oidc.exception.OAuthBadRequestException;
import org.idp.server.core.oidc.factory.AuthorizationRequestFactory;
import org.idp.server.core.oidc.gateway.RequestObjectGateway;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.OAuthRequestParameters;

/** RequestUriPatternContextService */
public class RequestUriPatternContextCreator implements OAuthRequestContextCreator {

  RequestObjectGateway requestObjectGateway;

  public RequestUriPatternContextCreator(RequestObjectGateway requestObjectGateway) {
    this.requestObjectGateway = requestObjectGateway;
  }

  @Override
  public OAuthRequestContext create(
      Tenant tenant,
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
          tenant,
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

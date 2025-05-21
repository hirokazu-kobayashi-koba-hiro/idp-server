package org.idp.server.core.oidc.context;

import java.util.Set;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JoseHandler;
import org.idp.server.basic.jose.JoseInvalidException;
import org.idp.server.core.oidc.AuthorizationProfile;
import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.core.oidc.OAuthRequestPattern;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.exception.OAuthBadRequestException;
import org.idp.server.core.oidc.factory.AuthorizationRequestFactory;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.OAuthRequestParameters;
import org.idp.server.core.oidc.validator.RequestObjectValidator;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/** RequestObjectPatternContextService */
public class RequestObjectPatternContextCreator implements OAuthRequestContextCreator {

  @Override
  public OAuthRequestContext create(
      Tenant tenant,
      OAuthRequestParameters parameters,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    try {
      RequestObjectValidator validator =
          new RequestObjectValidator(
              parameters, authorizationServerConfiguration, clientConfiguration);
      validator.validate();

      JoseHandler joseHandler = new JoseHandler();
      JoseContext joseContext =
          joseHandler.handle(
              parameters.request().value(),
              clientConfiguration.jwks(),
              authorizationServerConfiguration.jwks(),
              clientConfiguration.clientSecretValue());
      joseContext.verifySignature();

      OAuthRequestPattern pattern = OAuthRequestPattern.REQUEST_OBJECT;
      Set<String> filteredScopes =
          filterScopes(pattern, parameters, joseContext, clientConfiguration);
      AuthorizationProfile profile = analyze(filteredScopes, authorizationServerConfiguration);
      AuthorizationRequestFactory requestFactory =
          selectAuthorizationRequestFactory(
              profile, authorizationServerConfiguration, clientConfiguration);
      AuthorizationRequest authorizationRequest =
          requestFactory.create(
              tenant,
              profile,
              parameters,
              joseContext,
              filteredScopes,
              authorizationServerConfiguration,
              clientConfiguration);

      return new OAuthRequestContext(
          tenant,
          pattern,
          parameters,
          joseContext,
          authorizationRequest,
          authorizationServerConfiguration,
          clientConfiguration);
    } catch (JoseInvalidException exception) {
      throw new OAuthBadRequestException(
          "invalid_request", exception.getMessage(), exception, tenant);
    }
  }
}

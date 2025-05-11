package org.idp.server.core.oidc.factory;

import java.util.Set;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JsonWebTokenClaims;
import org.idp.server.basic.type.oauth.*;
import org.idp.server.basic.type.oidc.*;
import org.idp.server.basic.type.pkce.CodeChallenge;
import org.idp.server.basic.type.pkce.CodeChallengeMethod;
import org.idp.server.basic.type.rar.AuthorizationDetailsEntity;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.AuthorizationProfile;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.request.AuthorizationRequestBuilder;
import org.idp.server.core.oidc.request.OAuthRequestParameters;
import org.idp.server.core.oidc.request.RequestObjectParameters;

/**
 * shall only use the parameters included in the signed request object passed via the request or
 * request_uri parameter;
 */
public class FapiAdvanceRequestObjectPatternFactory implements AuthorizationRequestFactory {

  @Override
  public AuthorizationRequest create(
      Tenant tenant,
      AuthorizationProfile profile,
      OAuthRequestParameters parameters,
      JoseContext joseContext,
      Set<String> filteredScopes,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    JsonWebTokenClaims jsonWebTokenClaims = joseContext.claims();
    RequestObjectParameters requestObjectParameters =
        new RequestObjectParameters(jsonWebTokenClaims.payload());
    Scopes scopes = new Scopes(filteredScopes);
    ResponseType responseType = requestObjectParameters.responseType();
    RequestedClientId requestedClientId = requestObjectParameters.clientId();
    RedirectUri redirectUri = requestObjectParameters.redirectUri();
    State state = requestObjectParameters.state();
    ResponseMode responseMode = requestObjectParameters.responseMode();
    Nonce nonce = requestObjectParameters.nonce();
    Display display = requestObjectParameters.display();
    Prompts prompts = requestObjectParameters.prompts();
    MaxAge maxAge = requestObjectParameters.maxAge();
    UiLocales uiLocales = requestObjectParameters.uiLocales();
    IdTokenHint idTokenHint = requestObjectParameters.idTokenHint();
    LoginHint loginHint = requestObjectParameters.loginHint();
    AcrValues acrValues = requestObjectParameters.acrValues();
    ClaimsValue claimsValue = requestObjectParameters.claims();
    RequestObject requestObject = parameters.request();
    RequestUri requestUri = parameters.requestUri();
    CodeChallenge codeChallenge = requestObjectParameters.codeChallenge();
    CodeChallengeMethod codeChallengeMethod = requestObjectParameters.codeChallengeMethod();
    AuthorizationDetailsEntity authorizationDetailsEntity =
        requestObjectParameters.authorizationDetailsEntity();

    AuthorizationRequestBuilder builder = new AuthorizationRequestBuilder();
    builder.add(createIdentifier());
    builder.add(tenant.identifier());
    builder.add(profile);
    builder.add(scopes);
    builder.add(responseType);
    builder.add(requestedClientId);
    builder.add(clientConfiguration.client());
    builder.add(redirectUri);
    builder.add(state);
    builder.add(responseMode);
    builder.add(nonce);
    builder.add(display);
    builder.add(prompts);
    if (maxAge.exists()) {
      builder.add(maxAge);
    } else {
      builder.add(new MaxAge(authorizationServerConfiguration.defaultMaxAge()));
    }
    builder.add(uiLocales);
    builder.add(idTokenHint);
    builder.add(loginHint);
    builder.add(acrValues);
    builder.add(claimsValue);
    builder.add(requestObject);
    builder.add(requestUri);
    builder.add(convertClaimsPayload(claimsValue));
    builder.add(codeChallenge);
    builder.add(codeChallengeMethod);
    builder.add(convertAuthorizationDetails(authorizationDetailsEntity));
    builder.add(parameters.customParams());
    return builder.build();
  }
}

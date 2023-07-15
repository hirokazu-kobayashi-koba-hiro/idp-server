package org.idp.server.oauth.factory;

import java.util.Set;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JsonWebTokenClaims;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.AuthorizationProfile;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.request.AuthorizationRequestBuilder;
import org.idp.server.oauth.request.OAuthRequestParameters;
import org.idp.server.oauth.request.RequestObjectParameters;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.*;
import org.idp.server.type.pkce.CodeChallenge;
import org.idp.server.type.pkce.CodeChallengeMethod;
import org.idp.server.type.rar.AuthorizationDetailsEntity;

/**
 * RequestObjectPatternFactory
 *
 * <p>6.3.3. Request Parameter Assembly and Validation
 *
 * <p>The Authorization Server MUST assemble the set of Authorization Request parameters to be used
 * from the Request Object value and the OAuth 2.0 Authorization Request parameters (minus the
 * request or request_uri parameters). If the same parameter exists both in the Request Object and
 * the OAuth Authorization Request parameters, the parameter in the Request Object is used. Using
 * the assembled set of Authorization Request parameters, the Authorization Server then validates
 * the request the normal manner for the flow being used, as specified in Sections 3.1.2.2, 3.2.2.2,
 * or 3.3.2.2.
 */
public class RequestObjectPatternFactory implements AuthorizationRequestFactory {

  @Override
  public AuthorizationRequest create(
      AuthorizationProfile profile,
      OAuthRequestParameters parameters,
      JoseContext joseContext,
      Set<String> filteredScopes,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    JsonWebTokenClaims jsonWebTokenClaims = joseContext.claims();
    RequestObjectParameters requestObjectParameters =
        new RequestObjectParameters(jsonWebTokenClaims.payload());
    Scopes scopes = new Scopes(filteredScopes);
    ResponseType responseType =
        requestObjectParameters.hasResponseType()
            ? requestObjectParameters.responseType()
            : parameters.responseType();
    ClientId clientId =
        requestObjectParameters.hasClientId()
            ? requestObjectParameters.clientId()
            : parameters.clientId();
    RedirectUri redirectUri =
        requestObjectParameters.hasRedirectUri()
            ? requestObjectParameters.redirectUri()
            : parameters.redirectUri();
    State state =
        requestObjectParameters.hasState() ? requestObjectParameters.state() : parameters.state();
    ResponseMode responseMode =
        requestObjectParameters.hasResponseMode()
            ? requestObjectParameters.responseMode()
            : parameters.responseMode();
    Nonce nonce =
        requestObjectParameters.hasNonce() ? requestObjectParameters.nonce() : parameters.nonce();
    Display display =
        requestObjectParameters.hasDisplay()
            ? requestObjectParameters.display()
            : parameters.display();
    Prompts prompts =
        requestObjectParameters.hasPrompt()
            ? requestObjectParameters.prompts()
            : parameters.prompts();
    MaxAge maxAge =
        requestObjectParameters.hasMaxAge()
            ? requestObjectParameters.maxAge()
            : parameters.maxAge();
    UiLocales uiLocales =
        requestObjectParameters.hasUiLocales()
            ? requestObjectParameters.uiLocales()
            : parameters.uiLocales();
    IdTokenHint idTokenHint =
        requestObjectParameters.hasIdTokenHint()
            ? requestObjectParameters.idTokenHint()
            : parameters.idTokenHint();
    LoginHint loginHint =
        requestObjectParameters.hasLoginHint()
            ? requestObjectParameters.loginHint()
            : parameters.loginHint();
    AcrValues acrValues =
        requestObjectParameters.hasAcrValues()
            ? requestObjectParameters.acrValues()
            : parameters.acrValues();
    ClaimsValue claimsValue =
        requestObjectParameters.hasClaims()
            ? requestObjectParameters.claims()
            : parameters.claims();
    RequestObject requestObject = parameters.request();
    RequestUri requestUri = parameters.requestUri();
    CodeChallenge codeChallenge =
        requestObjectParameters.hasCodeChallenge()
            ? requestObjectParameters.codeChallenge()
            : parameters.codeChallenge();
    CodeChallengeMethod codeChallengeMethod =
        requestObjectParameters.hasCodeChallengeMethod()
            ? requestObjectParameters.codeChallengeMethod()
            : parameters.codeChallengeMethod();
    AuthorizationDetailsEntity authorizationDetailsEntity =
        requestObjectParameters.hasAuthorizationDetailsValue()
            ? requestObjectParameters.authorizationDetailsEntity()
            : parameters.authorizationDetailsValue();

    AuthorizationRequestBuilder builder = new AuthorizationRequestBuilder();
    builder.add(createIdentifier());
    builder.add(serverConfiguration.tokenIssuer());
    builder.add(profile);
    builder.add(scopes);
    builder.add(responseType);
    builder.add(clientId);
    builder.add(redirectUri);
    builder.add(state);
    builder.add(responseMode);
    builder.add(nonce);
    builder.add(display);
    builder.add(prompts);
    if (maxAge.exists()) {
      builder.add(maxAge);
    } else {
      builder.add(new MaxAge(serverConfiguration.defaultMaxAge()));
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
    return builder.build();
  }
}

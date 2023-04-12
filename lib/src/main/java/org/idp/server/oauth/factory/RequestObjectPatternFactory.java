package org.idp.server.oauth.factory;

import java.util.Set;
import java.util.UUID;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JsonWebTokenClaims;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.AuthorizationProfile;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.request.AuthorizationRequestBuilder;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.type.OAuthRequestParameters;
import org.idp.server.type.RequestObjectParameters;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.*;

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
    Prompt prompt =
        requestObjectParameters.hasPrompt()
            ? requestObjectParameters.prompt()
            : parameters.prompt();
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
    Claims claims =
        requestObjectParameters.hasClaims()
            ? requestObjectParameters.claims()
            : parameters.claims();
    RequestObject requestObject = new RequestObject();
    RequestUri requestUri = new RequestUri();

    AuthorizationRequestBuilder builder = new AuthorizationRequestBuilder();
    builder.add(new AuthorizationRequestIdentifier(UUID.randomUUID().toString()));
    builder.add(serverConfiguration.issuer());
    builder.add(profile);
    builder.add(scopes);
    builder.add(responseType);
    builder.add(clientId);
    builder.add(redirectUri);
    builder.add(state);
    builder.add(responseMode);
    builder.add(nonce);
    builder.add(display);
    builder.add(prompt);
    builder.add(maxAge);
    builder.add(uiLocales);
    builder.add(idTokenHint);
    builder.add(loginHint);
    builder.add(acrValues);
    builder.add(claims);
    builder.add(requestObject);
    builder.add(requestUri);
    return builder.build();
  }
}

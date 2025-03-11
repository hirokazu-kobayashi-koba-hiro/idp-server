package org.idp.server.core.oauth.request;

import org.idp.server.core.oauth.AuthorizationProfile;
import org.idp.server.core.oauth.OAuthSessionKey;
import org.idp.server.core.oauth.identity.ClaimsPayload;
import org.idp.server.core.oauth.rar.AuthorizationDetails;
import org.idp.server.core.oauth.vp.request.PresentationDefinition;
import org.idp.server.core.type.oauth.*;
import org.idp.server.core.type.oidc.*;
import org.idp.server.core.type.pkce.CodeChallenge;
import org.idp.server.core.type.pkce.CodeChallengeMethod;
import org.idp.server.core.type.verifiablepresentation.PresentationDefinitionUri;

/** AuthorizationRequest */
public class AuthorizationRequest {

  AuthorizationRequestIdentifier identifier = new AuthorizationRequestIdentifier();
  TokenIssuer tokenIssuer;
  AuthorizationProfile profile = AuthorizationProfile.UNDEFINED;
  Scopes scopes;
  ResponseType responseType;
  ClientId clientId;
  RedirectUri redirectUri;
  State state;
  ResponseMode responseMode;
  Nonce nonce;
  Display display;
  Prompts prompts;
  MaxAge maxAge;
  UiLocales uiLocales;
  IdTokenHint idTokenHint;
  LoginHint loginHint;
  AcrValues acrValues;
  ClaimsValue claimsValue;
  RequestObject requestObject;
  RequestUri requestUri;
  ClaimsPayload claimsPayload;
  CodeChallenge codeChallenge;
  CodeChallengeMethod codeChallengeMethod;
  AuthorizationDetails authorizationDetails;
  PresentationDefinition presentationDefinition;
  PresentationDefinitionUri presentationDefinitionUri;
  CustomParams customParams;

  public AuthorizationRequest() {}

  AuthorizationRequest(
      AuthorizationRequestIdentifier identifier,
      TokenIssuer tokenIssuer,
      AuthorizationProfile profile,
      Scopes scopes,
      ResponseType responseType,
      ClientId clientId,
      RedirectUri redirectUri,
      State state,
      ResponseMode responseMode,
      Nonce nonce,
      Display display,
      Prompts prompts,
      MaxAge maxAge,
      UiLocales uiLocales,
      IdTokenHint idTokenHint,
      LoginHint loginHint,
      AcrValues acrValues,
      ClaimsValue claimsValue,
      RequestObject requestObject,
      RequestUri requestUri,
      ClaimsPayload claimsPayload,
      CodeChallenge codeChallenge,
      CodeChallengeMethod codeChallengeMethod,
      AuthorizationDetails authorizationDetails,
      PresentationDefinition presentationDefinition,
      PresentationDefinitionUri presentationDefinitionUri,
      CustomParams customParams) {
    this.identifier = identifier;
    this.tokenIssuer = tokenIssuer;
    this.profile = profile;
    this.scopes = scopes;
    this.responseType = responseType;
    this.clientId = clientId;
    this.redirectUri = redirectUri;
    this.state = state;
    this.responseMode = responseMode;
    this.nonce = nonce;
    this.display = display;
    this.prompts = prompts;
    this.maxAge = maxAge;
    this.uiLocales = uiLocales;
    this.idTokenHint = idTokenHint;
    this.loginHint = loginHint;
    this.acrValues = acrValues;
    this.claimsValue = claimsValue;
    this.requestObject = requestObject;
    this.requestUri = requestUri;
    this.claimsPayload = claimsPayload;
    this.codeChallenge = codeChallenge;
    this.codeChallengeMethod = codeChallengeMethod;
    this.authorizationDetails = authorizationDetails;
    this.presentationDefinition = presentationDefinition;
    this.presentationDefinitionUri = presentationDefinitionUri;
    this.customParams = customParams;
  }

  public AuthorizationRequestIdentifier identifier() {
    return identifier;
  }

  public TokenIssuer tokenIssuer() {
    return tokenIssuer;
  }

  public boolean hasTokenIssuer() {
    return tokenIssuer.exists();
  }

  public AuthorizationProfile profile() {
    return profile;
  }

  public boolean hasProfile() {
    return profile.isDefined();
  }

  public Scopes scope() {
    return scopes;
  }

  public boolean hasScope() {
    return scopes.exists();
  }

  public ResponseType responseType() {
    return responseType;
  }

  public boolean hasResponseType() {
    return !responseType.isUndefined();
  }

  public ClientId clientId() {
    return clientId;
  }

  public boolean hasClientId() {
    return clientId.exists();
  }

  public RedirectUri redirectUri() {
    return redirectUri;
  }

  public boolean hasRedirectUri() {
    return redirectUri.exists();
  }

  public State state() {
    return state;
  }

  public boolean hasState() {
    return state.exists();
  }

  public ResponseMode responseMode() {
    return responseMode;
  }

  public boolean hasResponseMode() {
    return responseMode.isDefined();
  }

  public Nonce nonce() {
    return nonce;
  }

  public boolean hasNonce() {
    return nonce.exists();
  }

  public Display display() {
    return display;
  }

  public boolean hasDisplay() {
    return display.isDefined();
  }

  public Prompts prompts() {
    return prompts;
  }

  public boolean hasPrompts() {
    return prompts.exists();
  }

  public MaxAge maxAge() {
    return maxAge;
  }

  public boolean hasMaxAge() {
    return maxAge.exists();
  }

  public UiLocales uiLocales() {
    return uiLocales;
  }

  public boolean hasUilocales() {
    return uiLocales.exists();
  }

  public IdTokenHint idTokenHint() {
    return idTokenHint;
  }

  public boolean hasIdTokenHint() {
    return idTokenHint.exists();
  }

  public LoginHint loginHint() {
    return loginHint;
  }

  public boolean hasLoginHint() {
    return loginHint.exists();
  }

  public AcrValues acrValues() {
    return acrValues;
  }

  public boolean hasAcrValues() {
    return acrValues.exists();
  }

  public ClaimsValue claims() {
    return claimsValue;
  }

  public boolean hasClaims() {
    return claimsValue.exists();
  }

  public RequestObject request() {
    return requestObject;
  }

  public boolean hasRequest() {
    return requestObject.exists();
  }

  public RequestUri requestUri() {
    return requestUri;
  }

  public boolean hasRequestUri() {
    return requestUri.exists();
  }

  public ClaimsPayload claimsPayload() {
    return claimsPayload;
  }

  public boolean hasClaimsPayload() {
    return claimsPayload.exists();
  }

  public CodeChallenge codeChallenge() {
    return codeChallenge;
  }

  public CustomParams customParams() {
    return customParams;
  }

  public boolean hasCodeChallenge() {
    return codeChallenge.exists();
  }

  public CodeChallengeMethod codeChallengeMethod() {
    return codeChallengeMethod;
  }

  public boolean hasCodeChallengeMethod() {
    return codeChallengeMethod.isDefined();
  }

  public AuthorizationDetails authorizationDetails() {
    return authorizationDetails;
  }

  public boolean hasAuthorizationDetails() {
    return authorizationDetails.exists();
  }

  public PresentationDefinition presentationDefinition() {
    return presentationDefinition;
  }

  public boolean hasPresentationDefinition() {
    return presentationDefinition.exists();
  }

  public PresentationDefinitionUri presentationDefinitionUri() {
    return presentationDefinitionUri;
  }

  public boolean hasPresentationDefinitionUri() {
    return presentationDefinitionUri.exists();
  }

  public boolean isInvalidDisplay() {
    return display.isUnknown();
  }

  public boolean isInvalidPrompt() {
    return prompts.hasUnknown();
  }

  public boolean isInvalidMaxAge() {
    return !maxAge.isValid();
  }

  public boolean isOidcProfile() {
    return scopes.contains("openid");
  }

  public boolean exists() {
    return identifier.exists();
  }

  public boolean isPkceRequest() {
    return hasCodeChallenge();
  }

  public boolean isPkceWithS256() {
    return codeChallengeMethod.isS256();
  }

  public boolean isPromptNone() {
    return prompts().hasNone();
  }

  public boolean isPromptLogin() {
    return prompts().hasLogin();
  }

  public boolean isPromptCreate() {
    return prompts().hasCreate();
  }

  public boolean isVerifiableCredentialRequest() {
    return authorizationDetails.hasVerifiableCredential();
  }

  public boolean hasCustomParams() {
    return customParams.exists();
  }

  public OAuthSessionKey sessionKey() {
    return new OAuthSessionKey(tokenIssuer.value(), clientId.value());
  }
}

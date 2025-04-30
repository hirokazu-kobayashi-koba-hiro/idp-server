package org.idp.server.core.oauth.request;

import org.idp.server.core.oauth.AuthorizationProfile;
import org.idp.server.core.oauth.client.Client;
import org.idp.server.core.oauth.identity.RequestedClaimsPayload;
import org.idp.server.core.oauth.rar.AuthorizationDetails;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.oauth.*;
import org.idp.server.core.type.oidc.*;
import org.idp.server.core.type.pkce.CodeChallenge;
import org.idp.server.core.type.pkce.CodeChallengeMethod;

/** AuthorizationRequestBuilder */
public class AuthorizationRequestBuilder {

  AuthorizationRequestIdentifier identifier = new AuthorizationRequestIdentifier();
  TenantIdentifier tenantIdentifier = new TenantIdentifier();
  AuthorizationProfile profile = AuthorizationProfile.UNDEFINED;
  Scopes scopes = new Scopes();
  ResponseType responseType = ResponseType.undefined;
  RequestedClientId requestedClientId = new RequestedClientId();
  Client client = new Client();
  RedirectUri redirectUri = new RedirectUri();
  State state = new State();
  ResponseMode responseMode = ResponseMode.undefined;
  Nonce nonce = new Nonce();
  Display display = Display.undefined;
  Prompts prompts = new Prompts();
  MaxAge maxAge = new MaxAge();
  UiLocales uiLocales = new UiLocales();
  IdTokenHint idTokenHint = new IdTokenHint();
  LoginHint loginHint = new LoginHint();
  AcrValues acrValues = new AcrValues();
  ClaimsValue claimsValue = new ClaimsValue();
  RequestObject requestObject = new RequestObject();
  RequestUri requestUri = new RequestUri();
  RequestedClaimsPayload requestedClaimsPayload = new RequestedClaimsPayload();
  CodeChallenge codeChallenge = new CodeChallenge();
  CodeChallengeMethod codeChallengeMethod = CodeChallengeMethod.undefined;
  AuthorizationDetails authorizationDetails = new AuthorizationDetails();
  CustomParams customParams = new CustomParams();

  public AuthorizationRequestBuilder() {}

  public AuthorizationRequestBuilder add(AuthorizationRequestIdentifier identifier) {
    this.identifier = identifier;
    return this;
  }

  public AuthorizationRequestBuilder add(TenantIdentifier tenantIdentifier) {
    this.tenantIdentifier = tenantIdentifier;
    return this;
  }

  public AuthorizationRequestBuilder add(AuthorizationProfile profile) {
    this.profile = profile;
    return this;
  }

  public AuthorizationRequestBuilder add(AcrValues acrValues) {
    this.acrValues = acrValues;
    return this;
  }

  public AuthorizationRequestBuilder add(ClaimsValue claimsValue) {
    this.claimsValue = claimsValue;
    return this;
  }

  public AuthorizationRequestBuilder add(RequestedClientId requestedClientId) {
    this.requestedClientId = requestedClientId;
    return this;
  }

  public AuthorizationRequestBuilder add(Client client) {
    this.client = client;
    return this;
  }

  public AuthorizationRequestBuilder add(Display display) {
    this.display = display;
    return this;
  }

  public AuthorizationRequestBuilder add(IdTokenHint idTokenHint) {
    this.idTokenHint = idTokenHint;
    return this;
  }

  public AuthorizationRequestBuilder add(LoginHint loginHint) {
    this.loginHint = loginHint;
    return this;
  }

  public AuthorizationRequestBuilder add(MaxAge maxAge) {
    this.maxAge = maxAge;
    return this;
  }

  public AuthorizationRequestBuilder add(Nonce nonce) {
    this.nonce = nonce;
    return this;
  }

  public AuthorizationRequestBuilder add(Prompts prompts) {
    this.prompts = prompts;
    return this;
  }

  public AuthorizationRequestBuilder add(RedirectUri redirectUri) {
    this.redirectUri = redirectUri;
    return this;
  }

  public AuthorizationRequestBuilder add(RequestObject requestObject) {
    this.requestObject = requestObject;
    return this;
  }

  public AuthorizationRequestBuilder add(RequestUri requestUri) {
    this.requestUri = requestUri;
    return this;
  }

  public AuthorizationRequestBuilder add(ResponseMode responseMode) {
    this.responseMode = responseMode;
    return this;
  }

  public AuthorizationRequestBuilder add(ResponseType responseType) {
    this.responseType = responseType;
    return this;
  }

  public AuthorizationRequestBuilder add(Scopes scopes) {
    this.scopes = scopes;
    return this;
  }

  public AuthorizationRequestBuilder add(State state) {
    this.state = state;
    return this;
  }

  public AuthorizationRequestBuilder add(UiLocales uiLocales) {
    this.uiLocales = uiLocales;
    return this;
  }

  public AuthorizationRequestBuilder add(RequestedClaimsPayload requestedClaimsPayload) {
    this.requestedClaimsPayload = requestedClaimsPayload;
    return this;
  }

  public AuthorizationRequestBuilder add(CodeChallenge codeChallenge) {
    this.codeChallenge = codeChallenge;
    return this;
  }

  public AuthorizationRequestBuilder add(CodeChallengeMethod codeChallengeMethod) {
    this.codeChallengeMethod = codeChallengeMethod;
    return this;
  }

  public AuthorizationRequestBuilder add(AuthorizationDetails authorizationDetails) {
    this.authorizationDetails = authorizationDetails;
    return this;
  }

  public AuthorizationRequestBuilder add(CustomParams customParams) {
    this.customParams = customParams;
    return this;
  }

  public AuthorizationRequest build() {
    return new AuthorizationRequest(
        identifier,
        tenantIdentifier,
        profile,
        scopes,
        responseType,
        requestedClientId,
        client,
        redirectUri,
        state,
        responseMode,
        nonce,
        display,
        prompts,
        maxAge,
        uiLocales,
        idTokenHint,
        loginHint,
        acrValues,
        claimsValue,
        requestObject,
        requestUri,
        requestedClaimsPayload,
        codeChallenge,
        codeChallengeMethod,
        authorizationDetails,
        customParams);
  }
}

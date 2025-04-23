package org.idp.server.core.oauth;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.basic.jose.JoseContext;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.grantmangment.AuthorizationGranted;
import org.idp.server.core.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.oauth.grant.GrantIdTokenClaims;
import org.idp.server.core.oauth.grant.GrantUserinfoClaims;
import org.idp.server.core.oauth.grant.consent.ConsentClaim;
import org.idp.server.core.oauth.grant.consent.ConsentClaims;
import org.idp.server.core.oauth.io.OAuthAuthorizeRequest;
import org.idp.server.core.oauth.io.OAuthRequestResponse;
import org.idp.server.core.oauth.io.OAuthRequestStatus;
import org.idp.server.core.oauth.rar.AuthorizationDetails;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.oauth.request.OAuthRequestParameters;
import org.idp.server.core.oauth.response.ResponseModeDecidable;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.OAuthRequestKey;
import org.idp.server.core.type.extension.RegisteredRedirectUris;
import org.idp.server.core.type.extension.ResponseModeValue;
import org.idp.server.core.type.oauth.*;
import org.idp.server.core.type.oidc.ResponseMode;

/** OAuthRequestContext */
public class OAuthRequestContext implements ResponseModeDecidable {
  Tenant tenant;
  OAuthRequestPattern pattern;
  OAuthRequestParameters parameters;
  JoseContext joseContext;
  AuthorizationRequest authorizationRequest;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;
  OAuthSession session;
  AuthorizationGranted authorizationGranted;

  public OAuthRequestContext() {}

  public OAuthRequestContext(
      Tenant tenant,
      OAuthRequestPattern pattern,
      OAuthRequestParameters parameters,
      JoseContext joseContext,
      AuthorizationRequest authorizationRequest,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    this.tenant = tenant;
    this.pattern = pattern;
    this.parameters = parameters;
    this.joseContext = joseContext;
    this.authorizationRequest = authorizationRequest;
    this.serverConfiguration = serverConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  public void setSession(OAuthSession session) {
    this.session = session;
  }

  public void setAuthorizationGranted(AuthorizationGranted authorizationGranted) {
    this.authorizationGranted = authorizationGranted;
  }

  public boolean canAutomaticallyAuthorize() {
    if (!isPromptNone()) {
      return false;
    }

    if (session == null || !session.exists()) {
      throw new OAuthRedirectableBadRequestException(
          "login_required", "invalid session, session is not registered", this);
    }
    if (!session.isValid(authorizationRequest())) {
      throw new OAuthRedirectableBadRequestException(
          "login_required", "invalid session, session is invalid", this);
    }

    if (authorizationGranted == null || !authorizationGranted.exists()) {
      throw new OAuthRedirectableBadRequestException(
          "interaction_required", "authorization granted is nothing", this);
    }
    if (!authorizationGranted.isGrantedScopes(authorizationRequest.scopes())) {
      Scopes unauthorizedScopes =
          authorizationGranted.unauthorizedScopes(authorizationRequest.scopes());
      throw new OAuthRedirectableBadRequestException(
          "interaction_required",
          String.format(
              "authorization request contains unauthorized scopes (%s)",
              unauthorizedScopes.toStringValues()),
          this);
    }

    GrantIdTokenClaims grantIdTokenClaims = createGrantIdTokenClaims();
    if (!authorizationGranted.isGrantedClaims(grantIdTokenClaims)) {
      GrantIdTokenClaims unauthorizedIdTokenClaims =
          authorizationGranted.unauthorizedIdTokenClaims(grantIdTokenClaims);
      throw new OAuthRedirectableBadRequestException(
          "interaction_required",
          String.format(
              "authorization request contains unauthorized id_token claims (%s)",
              unauthorizedIdTokenClaims.toStringValues()),
          this);
    }

    GrantUserinfoClaims grantUserinfoClaims = createGrantUserinfoClaims();
    if (!authorizationGranted.isGrantedClaims(grantUserinfoClaims)) {
      GrantUserinfoClaims unauthorizedIdTokenClaims =
          authorizationGranted.unauthorizedIdTokenClaims(grantUserinfoClaims);
      throw new OAuthRedirectableBadRequestException(
          "interaction_required",
          String.format(
              "authorization request contains unauthorized userinfo claims (%s)",
              unauthorizedIdTokenClaims.toStringValues()),
          this);
    }

    ConsentClaims consentClaims = createConsentClaims();

    if (!authorizationGranted.isConsentedClaims(consentClaims)) {
      throw new OAuthRedirectableBadRequestException(
          "interaction_required", "authorization request contains unauthorized consent", this);
    }

    return true;
  }

  private ConsentClaims createConsentClaims() {
    Map<String, List<ConsentClaim>> contents = new HashMap<>();
    LocalDateTime now = SystemDateTime.now();

    if (clientConfiguration.hasTosUri()) {
      contents.put(
          "terms", List.of(new ConsentClaim("tos_uri", clientConfiguration.tosUri(), now)));
    }

    if (clientConfiguration.hasPolicyUri()) {
      contents.put(
          "privacy", List.of(new ConsentClaim("policy_uri", clientConfiguration.policyUri(), now)));
    }

    return new ConsentClaims(contents);
  }

  private GrantIdTokenClaims createGrantIdTokenClaims() {
    return GrantIdTokenClaims.create(
        authorizationRequest.scopes(),
        authorizationRequest.responseType(),
        serverConfiguration.claimsSupported(),
        authorizationRequest.requestedIdTokenClaims(),
        serverConfiguration.isIdTokenStrictMode());
  }

  private GrantUserinfoClaims createGrantUserinfoClaims() {
    return GrantUserinfoClaims.create(
        authorizationRequest.scopes(),
        serverConfiguration.claimsSupported(),
        authorizationRequest.requestedUserinfoClaims());
  }

  public OAuthAuthorizeRequest createOAuthAuthorizeRequest() {

    return new OAuthAuthorizeRequest(
            tenant,
            authorizationRequestIdentifier().value(),
            session.user(),
            session.authentication())
        .setCustomProperties(session.customProperties());
  }

  public OAuthRequestResponse createResponse() {
    if (isPromptCreate()) {
      return new OAuthRequestResponse(OAuthRequestStatus.OK_ACCOUNT_CREATION, this, session);
    }
    if (Objects.isNull(session) || !session.exists()) {
      return new OAuthRequestResponse(OAuthRequestStatus.OK, this, session);
    }
    if (!session.isValid(authorizationRequest())) {
      return new OAuthRequestResponse(OAuthRequestStatus.OK, this, session);
    }
    return new OAuthRequestResponse(OAuthRequestStatus.OK_SESSION_ENABLE, this, session);
  }

  public Tenant tenant() {
    return tenant;
  }

  public OAuthSession session() {
    return session;
  }

  public AuthorizationRequestIdentifier identifier() {
    return authorizationRequest.identifier();
  }

  public AuthorizationProfile profile() {
    return authorizationRequest.profile();
  }

  public OAuthRequestPattern pattern() {
    return pattern;
  }

  public OAuthRequestParameters parameters() {
    return parameters;
  }

  public JoseContext joseContext() {
    return joseContext;
  }

  public AuthorizationRequest authorizationRequest() {
    return authorizationRequest;
  }

  public AuthorizationRequestIdentifier authorizationRequestIdentifier() {
    return authorizationRequest.identifier();
  }

  public ServerConfiguration serverConfiguration() {
    return serverConfiguration;
  }

  public ClientConfiguration clientConfiguration() {
    return clientConfiguration;
  }

  public boolean isRequestParameterPattern() {
    return pattern.isRequestParameter();
  }

  public boolean isUnsignedRequestObject() {
    return !joseContext.hasJsonWebSignature();
  }

  public boolean isOAuth2Profile() {
    return profile().isOAuth2();
  }

  public boolean isOidcProfile() {
    return profile().isOidc();
  }

  public boolean isFapiBaselineProfile() {
    return profile().isFapiBaseline();
  }

  public boolean isFapiAdvanceProfile() {
    return profile().isFapiAdvance();
  }

  public ResponseType responseType() {
    return authorizationRequest.responseType();
  }

  public ResponseMode responseMode() {
    return authorizationRequest.responseMode();
  }

  public boolean isSupportedResponseTypeWithServer() {
    ResponseType responseType = responseType();
    return serverConfiguration.isSupportedResponseType(responseType);
  }

  public boolean isSupportedResponseTypeWithClient() {
    ResponseType responseType = responseType();
    return clientConfiguration.isSupportedResponseType(responseType);
  }

  public boolean isRegisteredRedirectUri() {
    RedirectUri redirectUri = redirectUri();
    return clientConfiguration.isRegisteredRedirectUri(redirectUri.value());
  }

  public Scopes scopes() {
    return authorizationRequest.scopes();
  }

  public RedirectUri redirectUri() {
    if (authorizationRequest.hasRedirectUri()) {
      return authorizationRequest.redirectUri();
    }
    return clientConfiguration.getFirstRedirectUri();
  }

  public TokenIssuer tokenIssuer() {
    return serverConfiguration.tokenIssuer();
  }

  public State state() {
    return authorizationRequest.state();
  }

  public String getParams(OAuthRequestKey key) {
    return parameters.getValueOrEmpty(key);
  }

  public boolean hasRedirectUriInRequest() {
    return authorizationRequest.hasRedirectUri();
  }

  public boolean isPckeRequest() {
    return authorizationRequest.hasCodeChallenge();
  }

  public RegisteredRedirectUris registeredRedirectUris() {
    return clientConfiguration.registeredRedirectUris();
  }

  public boolean isMultiRegisteredRedirectUri() {
    return clientConfiguration.isMultiRegisteredRedirectUri();
  }

  public ResponseModeValue responseModeValue() {
    return decideResponseModeValue(responseType(), responseMode());
  }

  public boolean isPromptNone() {
    return authorizationRequest.isPromptNone();
  }

  public boolean isPromptCreate() {
    return authorizationRequest.isPromptCreate();
  }

  public boolean isOidcImplicitFlowOrHybridFlow() {
    return responseType().isOidcImplicitFlow() || responseType().isOidcHybridFlow();
  }

  public boolean isOidcImplicitFlow() {
    return responseType().isOidcImplicitFlow();
  }

  public boolean isWebApplication() {
    return clientConfiguration.isWebApplication();
  }

  public boolean hasAuthorizationDetails() {
    return authorizationRequest.hasAuthorizationDetails();
  }

  public AuthorizationDetails authorizationDetails() {
    return authorizationRequest.authorizationDetails();
  }

  public RequestedClientId clientId() {
    return authorizationRequest.retrieveClientId();
  }

  public OAuthSessionKey sessionKey() {
    return authorizationRequest.sessionKey();
  }

  public String sessionKeyValue() {
    return sessionKey().key();
  }

  public boolean isOidcRequest() {
    return scopes().hasOpenidScope();
  }

  public ClientAuthenticationType clientAuthenticationType() {
    return clientConfiguration.clientAuthenticationType();
  }

  public boolean hasOpenidScope() {
    return scopes().hasOpenidScope();
  }

  public boolean isJwtMode() {
    return isJwtMode(authorizationRequest.profile(), responseType(), responseMode());
  }

  public TenantIdentifier tenantIdentifier() {
    return authorizationRequest.tenantIdentifier();
  }
}

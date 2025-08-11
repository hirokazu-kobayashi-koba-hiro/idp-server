/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.openid.oauth;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.openid.grant_management.AuthorizationGranted;
import org.idp.server.core.openid.grant_management.grant.GrantIdTokenClaims;
import org.idp.server.core.openid.grant_management.grant.GrantUserinfoClaims;
import org.idp.server.core.openid.grant_management.grant.consent.ConsentClaim;
import org.idp.server.core.openid.grant_management.grant.consent.ConsentClaims;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.openid.oauth.io.OAuthAuthorizeRequest;
import org.idp.server.core.openid.oauth.io.OAuthRequestResponse;
import org.idp.server.core.openid.oauth.io.OAuthRequestStatus;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetails;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.openid.oauth.request.OAuthRequestParameters;
import org.idp.server.core.openid.oauth.response.ResponseModeDecidable;
import org.idp.server.core.openid.oauth.type.OAuthRequestKey;
import org.idp.server.core.openid.oauth.type.extension.RegisteredRedirectUris;
import org.idp.server.core.openid.oauth.type.extension.ResponseModeValue;
import org.idp.server.core.openid.oauth.type.oauth.*;
import org.idp.server.core.openid.oauth.type.oidc.ResponseMode;
import org.idp.server.core.openid.oauth.view.OAuthViewUrlResolver;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/** OAuthRequestContext */
public class OAuthRequestContext implements ResponseModeDecidable {
  Tenant tenant;
  OAuthRequestPattern pattern;
  OAuthRequestParameters parameters;
  JoseContext joseContext;
  AuthorizationRequest authorizationRequest;
  AuthorizationServerConfiguration authorizationServerConfiguration;
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
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    this.tenant = tenant;
    this.pattern = pattern;
    this.parameters = parameters;
    this.joseContext = joseContext;
    this.authorizationRequest = authorizationRequest;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
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
        authorizationServerConfiguration.claimsSupported(),
        authorizationRequest.requestedIdTokenClaims(),
        authorizationServerConfiguration.isIdTokenStrictMode());
  }

  private GrantUserinfoClaims createGrantUserinfoClaims() {
    return GrantUserinfoClaims.create(
        authorizationRequest.scopes(),
        authorizationServerConfiguration.claimsSupported(),
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
    String frontUrl = OAuthViewUrlResolver.resolve(this);
    if (isPromptCreate()) {

      return new OAuthRequestResponse(
          OAuthRequestStatus.OK_ACCOUNT_CREATION, this, session, frontUrl);
    }

    if (Objects.isNull(session) || !session.exists()) {
      return new OAuthRequestResponse(OAuthRequestStatus.OK, this, session, frontUrl);
    }

    if (!session.isValid(authorizationRequest())) {
      return new OAuthRequestResponse(OAuthRequestStatus.OK, this, session, frontUrl);
    }

    return new OAuthRequestResponse(OAuthRequestStatus.OK_SESSION_ENABLE, this, session, frontUrl);
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

  public AuthorizationServerConfiguration serverConfiguration() {
    return authorizationServerConfiguration;
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
    return authorizationServerConfiguration.isSupportedResponseType(responseType);
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
    return authorizationServerConfiguration.tokenIssuer();
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
    return authorizationRequest.requestedClientId();
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

  public boolean isPushedRequest() {
    return pattern.isPushed();
  }

  public ExpiresIn expiresIn() {
    return authorizationRequest.expiresIn();
  }
}

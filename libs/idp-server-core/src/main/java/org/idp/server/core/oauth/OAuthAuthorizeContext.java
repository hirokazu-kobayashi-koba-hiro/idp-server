package org.idp.server.core.oauth;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.identity.RequestedClaimsPayload;
import org.idp.server.core.oauth.identity.RequestedIdTokenClaims;
import org.idp.server.core.identity.User;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.client.Client;
import org.idp.server.core.oauth.grant.AuthorizationGrant;
import org.idp.server.core.oauth.grant.GrantIdTokenClaims;
import org.idp.server.core.oauth.grant.GrantUserinfoClaims;
import org.idp.server.core.oauth.grant.consent.ConsentClaim;
import org.idp.server.core.oauth.grant.consent.ConsentClaims;
import org.idp.server.core.oauth.rar.AuthorizationDetails;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.response.ResponseModeDecidable;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.extension.CustomProperties;
import org.idp.server.core.type.extension.ExpiredAt;
import org.idp.server.core.type.oauth.*;
import org.idp.server.core.type.oidc.ResponseMode;

/** OAuthAuthorizeContext */
public class OAuthAuthorizeContext implements ResponseModeDecidable {
  AuthorizationRequest authorizationRequest;
  User user;
  Authentication authentication;
  CustomProperties customProperties;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;

  public OAuthAuthorizeContext() {}

  public OAuthAuthorizeContext(
      AuthorizationRequest authorizationRequest,
      User user,
      Authentication authentication,
      CustomProperties customProperties,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    this.authorizationRequest = authorizationRequest;
    this.user = user;
    this.authentication = authentication;
    this.customProperties = customProperties;
    this.clientConfiguration = clientConfiguration;
    this.serverConfiguration = serverConfiguration;
  }

  public AuthorizationRequest authorizationRequest() {
    return authorizationRequest;
  }

  public User user() {
    return user;
  }

  public Authentication authentication() {
    return authentication;
  }

  public Scopes scopes() {
    return authorizationRequest.scopes();
  }

  public RequestedClaimsPayload requestedClaimsPayload() {
    return authorizationRequest.requestedClaimsPayload();
  }

  public AuthorizationGrant authorize() {

    TenantIdentifier tenantIdentifier = authorizationRequest.tenantIdentifier();
    RequestedClientId requestedClientId = authorizationRequest.retrieveClientId();
    Client client = clientConfiguration.client();

    Scopes scopes = authorizationRequest.scopes();
    ResponseType responseType = authorizationRequest.responseType();
    List<String> supportedClaims = serverConfiguration.claimsSupported();
    RequestedClaimsPayload requestedClaimsPayload = authorizationRequest.requestedClaimsPayload();
    boolean idTokenStrictMode = serverConfiguration().isIdTokenStrictMode();

    GrantIdTokenClaims grantIdTokenClaims =
        GrantIdTokenClaims.create(
            scopes,
            responseType,
            supportedClaims,
            requestedClaimsPayload.idToken(),
            idTokenStrictMode);
    GrantUserinfoClaims grantUserinfoClaims =
        GrantUserinfoClaims.create(scopes, supportedClaims, requestedClaimsPayload.userinfo());
    AuthorizationDetails authorizationDetails = authorizationRequest.authorizationDetails();
    ConsentClaims consentClaims = createConsentClaims();

    return new AuthorizationGrant(
        tenantIdentifier,
        user,
        authentication,
        requestedClientId,
        client,
        scopes,
        grantIdTokenClaims,
        grantUserinfoClaims,
        customProperties,
        authorizationDetails,
        consentClaims);
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

  public CustomProperties customProperties() {
    return customProperties;
  }

  public ServerConfiguration serverConfiguration() {
    return serverConfiguration;
  }

  public ClientConfiguration clientConfiguration() {
    return clientConfiguration;
  }

  public TokenIssuer tokenIssuer() {
    return serverConfiguration.tokenIssuer();
  }

  public ResponseType responseType() {
    return authorizationRequest.responseType();
  }

  public ResponseMode responseMode() {
    return authorizationRequest.responseMode();
  }

  public boolean isJwtMode() {
    return isJwtMode(authorizationRequest.profile(), responseType(), responseMode());
  }

  public ExpiredAt authorizationCodeGrantExpiresDateTime() {
    LocalDateTime localDateTime = SystemDateTime.now();
    int duration = serverConfiguration.authorizationCodeValidDuration();
    return new ExpiredAt(localDateTime.plusMinutes(duration));
  }

  public RequestedIdTokenClaims idTokenClaims() {
    return authorizationRequest.requestedClaimsPayload().idToken();
  }

  public boolean hasState() {
    return authorizationRequest.hasState();
  }
}

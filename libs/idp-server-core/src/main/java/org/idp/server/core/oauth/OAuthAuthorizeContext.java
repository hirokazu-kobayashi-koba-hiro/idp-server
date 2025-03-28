package org.idp.server.core.oauth;

import java.time.LocalDateTime;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.client.Client;
import org.idp.server.core.oauth.grant.AuthorizationGrant;
import org.idp.server.core.oauth.identity.ClaimsPayload;
import org.idp.server.core.oauth.identity.IdTokenClaims;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.rar.AuthorizationDetails;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.response.ResponseModeDecidable;
import org.idp.server.core.oauth.vp.request.PresentationDefinition;
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
    return authorizationRequest.scope();
  }

  public ClaimsPayload claimsPayload() {
    return authorizationRequest.claimsPayload();
  }

  public AuthorizationGrant toAuthorizationGranted() {

    ClientId clientId = authorizationRequest.clientId();
    Client client = clientConfiguration.client();
    Scopes scopes = authorizationRequest.scope();
    ClaimsPayload claimsPayload = authorizationRequest.claimsPayload();
    AuthorizationDetails authorizationDetails = authorizationRequest.authorizationDetails();
    PresentationDefinition presentationDefinition = authorizationRequest.presentationDefinition();
    
    return new AuthorizationGrant(
        user,
        authentication,
        clientId,
        client,
        scopes,
        claimsPayload,
        customProperties,
        authorizationDetails,
        presentationDefinition);
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

  public IdTokenClaims idTokenClaims() {
    return authorizationRequest.claimsPayload().idToken();
  }

  public boolean hasState() {
    return authorizationRequest.hasState();
  }
}

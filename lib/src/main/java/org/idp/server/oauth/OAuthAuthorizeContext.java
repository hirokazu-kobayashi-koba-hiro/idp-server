package org.idp.server.oauth;

import java.time.LocalDateTime;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.identity.ClaimsPayload;
import org.idp.server.oauth.identity.IdTokenClaims;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.extension.ExpiredAt;
import org.idp.server.type.oauth.*;

/** OAuthAuthorizeContext */
public class OAuthAuthorizeContext {
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
    ClientId clientId = clientConfiguration.clientId();
    Scopes scopes = authorizationRequest.scope();
    ClaimsPayload claimsPayload = authorizationRequest.claimsPayload();
    return new AuthorizationGrant(
        user, authentication, clientId, scopes, claimsPayload, customProperties);
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

  public ExpiredAt authorizationCodeGrantExpiresDateTime() {
    LocalDateTime localDateTime = SystemDateTime.now();
    int duration = serverConfiguration.authorizationCodeValidDuration();
    return new ExpiredAt(localDateTime.plusMinutes(duration));
  }

  public IdTokenClaims idTokenClaims() {
    return authorizationRequest.claimsPayload().idToken();
  }
}

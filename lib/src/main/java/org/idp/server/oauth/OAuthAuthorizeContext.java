package org.idp.server.oauth;

import java.time.LocalDateTime;
import org.idp.server.basic.date.UtcDateTime;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.grant.AuthorizationGrant;
import org.idp.server.oauth.identity.ClaimsPayload;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.oauth.*;

/** OAuthAuthorizeContext */
public class OAuthAuthorizeContext {
  AuthorizationRequest authorizationRequest;
  User user;
  CustomProperties customProperties;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;

  public OAuthAuthorizeContext() {}

  public OAuthAuthorizeContext(
      AuthorizationRequest authorizationRequest,
      User user,
      CustomProperties customProperties,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    this.authorizationRequest = authorizationRequest;
    this.user = user;
    this.clientConfiguration = clientConfiguration;
    this.serverConfiguration = serverConfiguration;
    this.customProperties = customProperties;
  }

  public AuthorizationRequest authorizationRequest() {
    return authorizationRequest;
  }

  public User user() {
    return user;
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
    return new AuthorizationGrant(user, clientId, scopes, claimsPayload, customProperties);
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
    return serverConfiguration.issuer();
  }

  public ResponseType responseType() {
    return authorizationRequest.responseType();
  }

  public ExpiredAt authorizationCodeGrantExpiresDateTime() {
    LocalDateTime localDateTime = UtcDateTime.now();
    int duration = serverConfiguration.authorizationCodeValidDuration();
    return new ExpiredAt(localDateTime.plusMinutes(duration));
  }
}

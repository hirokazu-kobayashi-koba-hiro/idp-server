package org.idp.server.core.oauth;

import java.time.LocalDateTime;
import org.idp.server.basic.date.UtcDateTime;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.identity.User;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.type.CustomProperties;
import org.idp.server.core.type.ExpiredAt;
import org.idp.server.core.type.ResponseType;
import org.idp.server.core.type.TokenIssuer;

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
    this.clientConfiguration = clientConfiguration;
  }

  public AuthorizationRequest authorizationRequest() {
    return authorizationRequest;
  }

  public User user() {
    return user;
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

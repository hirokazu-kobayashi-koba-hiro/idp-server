package org.idp.server.core.oauth;

import java.time.LocalDateTime;
import org.idp.server.basic.date.UtcDateTime;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.identity.User;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.type.*;

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

  public Subject subject() {
    return new Subject(user.sub());
  }

  public Scopes scopes() {
    return authorizationRequest.scope();
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

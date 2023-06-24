package org.idp.server.clientauthenticator;

import java.util.Objects;
import org.idp.server.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.type.oauth.ClientAuthenticationType;

public class ClientAuthenticationVerifier {
  ClientAuthenticationType clientAuthenticationType;
  ClientAuthenticator clientAuthenticator;
  ServerConfiguration serverConfiguration;

  public ClientAuthenticationVerifier(
      ClientAuthenticationType clientAuthenticationType,
      ClientAuthenticator clientAuthenticator,
      ServerConfiguration serverConfiguration) {
    this.clientAuthenticationType = clientAuthenticationType;
    this.clientAuthenticator = clientAuthenticator;
    this.serverConfiguration = serverConfiguration;
  }

  public void verify() {
    if (Objects.isNull(clientAuthenticator)) {
      throw new ClientUnAuthorizedException(
          String.format(
              "idp does not supported client authentication type (%s)",
              clientAuthenticationType.name()));
    }
    if (!serverConfiguration.isSupportedClientAuthenticationType(clientAuthenticationType.name())) {
      throw new ClientUnAuthorizedException(
          String.format(
              "server does not supported client authentication type (%s)",
              clientAuthenticationType.name()));
    }
  }
}

/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.clientauthenticator;

import java.util.Objects;
import org.idp.server.basic.type.oauth.ClientAuthenticationType;
import org.idp.server.core.oidc.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.oidc.clientauthenticator.plugin.ClientAuthenticator;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;

public class ClientAuthenticationVerifier {
  ClientAuthenticationType clientAuthenticationType;
  ClientAuthenticator clientAuthenticator;
  AuthorizationServerConfiguration authorizationServerConfiguration;

  public ClientAuthenticationVerifier(
      ClientAuthenticationType clientAuthenticationType,
      ClientAuthenticator clientAuthenticator,
      AuthorizationServerConfiguration authorizationServerConfiguration) {
    this.clientAuthenticationType = clientAuthenticationType;
    this.clientAuthenticator = clientAuthenticator;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
  }

  public void verify() {
    if (Objects.isNull(clientAuthenticator)) {
      throw new ClientUnAuthorizedException(
          String.format(
              "idp does not supported client authentication type (%s)",
              clientAuthenticationType.name()));
    }
    if (!authorizationServerConfiguration.isSupportedClientAuthenticationType(
        clientAuthenticationType.name())) {
      throw new ClientUnAuthorizedException(
          String.format(
              "server does not supported client authentication type (%s)",
              clientAuthenticationType.name()));
    }
  }
}

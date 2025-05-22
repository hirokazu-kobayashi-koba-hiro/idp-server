/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.clientauthenticator;

import org.idp.server.core.oidc.clientauthenticator.plugin.ClientAuthenticator;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;

public class ClientAuthenticationHandler {

  ClientAuthenticators authenticators;

  public ClientAuthenticationHandler() {
    this.authenticators = new ClientAuthenticators();
  }

  public ClientCredentials authenticate(BackchannelRequestContext context) {
    ClientAuthenticator clientAuthenticator =
        authenticators.get(context.clientAuthenticationType());

    ClientAuthenticationVerifier verifier =
        new ClientAuthenticationVerifier(
            context.clientAuthenticationType(), clientAuthenticator, context.serverConfiguration());
    verifier.verify();
    return clientAuthenticator.authenticate(context);
  }
}

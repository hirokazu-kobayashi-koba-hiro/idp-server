/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.clientauthenticator;

import org.idp.server.basic.type.oauth.ClientAuthenticationType;
import org.idp.server.core.oidc.clientauthenticator.plugin.ClientAuthenticator;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;

class PublicClientAuthenticator implements ClientAuthenticator {

  @Override
  public ClientAuthenticationType type() {
    return ClientAuthenticationType.none;
  }

  @Override
  public ClientCredentials authenticate(BackchannelRequestContext context) {
    return new ClientCredentials();
  }
}

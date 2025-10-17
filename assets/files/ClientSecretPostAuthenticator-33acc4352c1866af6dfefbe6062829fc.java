/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.openid.oauth.clientauthenticator;

import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientAssertionJwt;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientAuthenticationPublicKey;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertification;
import org.idp.server.core.openid.oauth.clientauthenticator.plugin.ClientAuthenticator;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecret;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * client secret post
 *
 * <p>Alternatively, the authorization server MAY support including the client credentials in the
 * request-body using the following parameters:
 *
 * <p>client_id REQUIRED. The client identifier issued to the client during the registration process
 * described by Section 2.2.
 *
 * <p>client_secret REQUIRED. The client secret. The client MAY omit the parameter if the client
 * secret is an empty string.
 */
class ClientSecretPostAuthenticator implements ClientAuthenticator {

  LoggerWrapper log = LoggerWrapper.getLogger(ClientSecretPostAuthenticator.class);

  @Override
  public ClientAuthenticationType type() {
    return ClientAuthenticationType.client_secret_post;
  }

  @Override
  public ClientCredentials authenticate(BackchannelRequestContext context) {
    RequestedClientId requestedClientId = context.requestedClientId();

    throwExceptionIfNotContainsClientSecretPost(context);
    throwExceptionIfUnMatchClientSecret(context);

    ClientSecret clientSecret = context.parameters().clientSecret();

    log.info(
        "Client authentication succeeded: method={}, client_id={}",
        ClientAuthenticationType.client_secret_post.name(),
        requestedClientId.value());

    return new ClientCredentials(
        requestedClientId,
        ClientAuthenticationType.client_secret_post,
        clientSecret,
        new ClientAuthenticationPublicKey(),
        new ClientAssertionJwt(),
        new ClientCertification());
  }

  void throwExceptionIfUnMatchClientSecret(BackchannelRequestContext context) {
    BackchannelRequestParameters parameters = context.parameters();
    ClientSecret clientSecret = parameters.clientSecret();
    ClientConfiguration clientConfiguration = context.clientConfiguration();
    RequestedClientId clientId = context.requestedClientId();
    if (!clientConfiguration.matchClientSecret(clientSecret.value())) {
      throw new ClientUnAuthorizedException(
          ClientAuthenticationType.client_secret_post.name(),
          clientId,
          "client_secret does not match");
    }
  }

  void throwExceptionIfNotContainsClientSecretPost(BackchannelRequestContext context) {
    BackchannelRequestParameters parameters = context.parameters();
    RequestedClientId clientId = context.requestedClientId();
    if (!parameters.hasClientSecret()) {
      throw new ClientUnAuthorizedException(
          ClientAuthenticationType.client_secret_post.name(),
          clientId,
          "request does not contain client_secret");
    }
  }
}

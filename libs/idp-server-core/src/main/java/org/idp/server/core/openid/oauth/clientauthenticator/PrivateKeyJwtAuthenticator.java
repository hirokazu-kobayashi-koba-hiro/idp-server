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
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.jose.JoseHandler;
import org.idp.server.platform.jose.JoseInvalidException;

class PrivateKeyJwtAuthenticator
    implements ClientAuthenticator, ClientAuthenticationJwtValidatable {

  JoseHandler joseHandler = new JoseHandler();

  @Override
  public ClientAuthenticationType type() {
    return ClientAuthenticationType.private_key_jwt;
  }

  @Override
  public ClientCredentials authenticate(BackchannelRequestContext context) {
    throwExceptionIfNotContainsClientAssertion(context);
    JoseContext joseContext = parseOrThrowExceptionIfUnMatchClientAssertion(context);
    RequestedClientId requestedClientId = context.requestedClientId();
    ClientSecret clientSecret = new ClientSecret();
    ClientAuthenticationPublicKey clientAuthenticationPublicKey =
        new ClientAuthenticationPublicKey(joseContext.jsonWebKey());
    ClientAssertionJwt clientAssertionJwt = new ClientAssertionJwt(joseContext.jsonWebSignature());

    return new ClientCredentials(
        requestedClientId,
        ClientAuthenticationType.private_key_jwt,
        clientSecret,
        clientAuthenticationPublicKey,
        clientAssertionJwt,
        new ClientCertification());
  }

  void throwExceptionIfNotContainsClientAssertion(BackchannelRequestContext context) {
    BackchannelRequestParameters parameters = context.parameters();
    if (!parameters.hasClientAssertion()) {
      throw new ClientUnAuthorizedException(
          "client authentication type is client_secret_jwt, but request does not contains client_assertion");
    }
    if (!parameters.hasClientAssertionType()) {
      throw new ClientUnAuthorizedException(
          "client authentication type is client_secret_jwt, but request does not contains client_assertion_type");
    }
  }

  JoseContext parseOrThrowExceptionIfUnMatchClientAssertion(BackchannelRequestContext context) {
    try {
      BackchannelRequestParameters parameters = context.parameters();
      ClientConfiguration clientConfiguration = context.clientConfiguration();
      JoseContext joseContext =
          joseHandler.handle(
              parameters.clientAssertion().value(),
              clientConfiguration.jwks(),
              clientConfiguration.jwks(),
              clientConfiguration.clientSecretValue());
      joseContext.verifySignature();
      validate(joseContext, context);
      return joseContext;
    } catch (JoseInvalidException e) {
      throw new ClientUnAuthorizedException(e.getMessage());
    }
  }
}

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
import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecret;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.jose.JoseHandler;
import org.idp.server.platform.jose.JoseInvalidException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.x509.X509CertInvalidException;

class PrivateKeyJwtAuthenticator
    implements ClientAuthenticator, ClientAuthenticationJwtValidatable {

  LoggerWrapper log = LoggerWrapper.getLogger(PrivateKeyJwtAuthenticator.class);
  JoseHandler joseHandler = new JoseHandler();

  @Override
  public ClientAuthenticationType type() {
    return ClientAuthenticationType.private_key_jwt;
  }

  @Override
  public ClientCredentials authenticate(BackchannelRequestContext context) {
    RequestedClientId requestedClientId = context.requestedClientId();

    throwExceptionIfNotContainsClientAssertion(context);
    JoseContext joseContext = parseOrThrowExceptionIfUnMatchClientAssertion(context);

    ClientSecret clientSecret = new ClientSecret();
    ClientAuthenticationPublicKey clientAuthenticationPublicKey =
        new ClientAuthenticationPublicKey(joseContext.jsonWebKey());
    ClientAssertionJwt clientAssertionJwt = new ClientAssertionJwt(joseContext.jsonWebSignature());
    ClientCertification clientCertification = parseClientCertification(context);

    log.info(
        "Client authentication succeeded: method={}, client_id={}",
        ClientAuthenticationType.private_key_jwt.name(),
        requestedClientId.value());

    return new ClientCredentials(
        requestedClientId,
        ClientAuthenticationType.private_key_jwt,
        clientSecret,
        clientAuthenticationPublicKey,
        clientAssertionJwt,
        clientCertification);
  }

  private ClientCertification parseClientCertification(BackchannelRequestContext context) {
    ClientCert clientCert = context.clientCert();
    if (clientCert == null || !clientCert.exists()) {
      return new ClientCertification();
    }
    try {
      return ClientCertification.parse(clientCert.plainValue());
    } catch (X509CertInvalidException e) {
      log.warn("Failed to parse client certificate: {}", e.getMessage());
      return new ClientCertification();
    }
  }

  void throwExceptionIfNotContainsClientAssertion(BackchannelRequestContext context) {
    BackchannelRequestParameters parameters = context.parameters();
    RequestedClientId clientId = context.requestedClientId();
    if (!parameters.hasClientAssertion()) {
      throw new ClientUnAuthorizedException(
          ClientAuthenticationType.private_key_jwt.name(),
          clientId,
          "request does not contain client_assertion");
    }
    if (!parameters.hasClientAssertionType()) {
      throw new ClientUnAuthorizedException(
          ClientAuthenticationType.private_key_jwt.name(),
          clientId,
          "request does not contain client_assertion_type");
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
      RequestedClientId clientId = context.requestedClientId();
      throw new ClientUnAuthorizedException(
          ClientAuthenticationType.private_key_jwt.name(),
          clientId,
          "client_assertion validation failed: " + e.getMessage(),
          e);
    }
  }
}

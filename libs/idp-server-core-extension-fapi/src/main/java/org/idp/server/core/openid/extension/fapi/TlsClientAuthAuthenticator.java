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

package org.idp.server.core.openid.extension.fapi;

import org.idp.server.core.openid.oauth.clientauthenticator.BackchannelRequestContext;
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
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.x509.X509CertInvalidException;
import org.idp.server.platform.x509.X509Certification;

public class TlsClientAuthAuthenticator implements ClientAuthenticator {

  LoggerWrapper log = LoggerWrapper.getLogger(TlsClientAuthAuthenticator.class);

  @Override
  public ClientAuthenticationType type() {
    return ClientAuthenticationType.tls_client_auth;
  }

  @Override
  public ClientCredentials authenticate(BackchannelRequestContext context) {
    RequestedClientId requestedClientId = context.requestedClientId();

    throwExceptionIfNotContainsClientCert(context);
    X509Certification x509Certification = parseOrThrowExceptionIfNoneMatch(context);

    ClientSecret clientSecret = new ClientSecret();
    ClientCertification clientCertification = new ClientCertification(x509Certification);

    log.info(
        "Client authentication succeeded: method={}, client_id={}",
        ClientAuthenticationType.tls_client_auth.name(),
        requestedClientId.value());

    return new ClientCredentials(
        requestedClientId,
        ClientAuthenticationType.tls_client_auth,
        clientSecret,
        new ClientAuthenticationPublicKey(),
        new ClientAssertionJwt(),
        clientCertification);
  }

  void throwExceptionIfNotContainsClientCert(BackchannelRequestContext context) {
    ClientCert clientCert = context.clientCert();
    RequestedClientId clientId = context.requestedClientId();
    if (!clientCert.exists()) {
      log.warn(
          "Client authentication failed: method={}, client_id={}, reason={}",
          ClientAuthenticationType.tls_client_auth.name(),
          clientId.value(),
          "request does not contain client_cert");
      throw new ClientUnAuthorizedException(
          "client authentication type is tls_client_auth, but request does not contains client_cert");
    }
  }

  X509Certification parseOrThrowExceptionIfNoneMatch(BackchannelRequestContext context) {
    ClientCert clientCert = context.clientCert();
    try {
      X509Certification x509Certification = X509Certification.parse(clientCert.plainValue());
      ClientConfiguration clientConfiguration = context.clientConfiguration();
      // tls_client_auth_subject_dn
      if (x509Certification.subject().equals(clientConfiguration.tlsClientAuthSubjectDn())) {
        return x509Certification;
      }
      // tls_client_auth_san_dns dNSName
      if (x509Certification.hasDNSName()
          && x509Certification.dNSName().equals(clientConfiguration.tlsClientAuthSanDns())) {
        return x509Certification;
      }
      // tls_client_auth_san_uri uniformResourceIdentifier
      if (x509Certification.hasUniformResourceIdentifier()
          && x509Certification
              .uniformResourceIdentifier()
              .equals(clientConfiguration.tlsClientAuthSanUri())) {
        return x509Certification;
      }
      // tls_client_auth_san_ip iPAddress
      if (x509Certification.hasIPAddress()
          && x509Certification.iPAddress().equals(clientConfiguration.tlsClientAuthSanIp())) {
        return x509Certification;
      }
      // tls_client_auth_san_email rfc822Name
      if (x509Certification.hasRfc822Name()
          && x509Certification.rfc822Name().equals(clientConfiguration.tlsClientAuthSanEmail())) {
        return x509Certification;
      }
      RequestedClientId clientId = context.requestedClientId();
      log.warn(
          "Client authentication failed: method={}, client_id={}, reason={}",
          ClientAuthenticationType.tls_client_auth.name(),
          clientId.value(),
          "client_cert does not match any subject names");
      throw new ClientUnAuthorizedException("client_cert does not match any subject names");
    } catch (X509CertInvalidException e) {
      RequestedClientId clientId = context.requestedClientId();
      log.warn(
          "Client authentication failed: method={}, client_id={}, reason={}",
          ClientAuthenticationType.tls_client_auth.name(),
          clientId.value(),
          "client_cert is malformed: " + e.getMessage());
      throw new ClientUnAuthorizedException("client_cert is malformed");
    }
  }
}

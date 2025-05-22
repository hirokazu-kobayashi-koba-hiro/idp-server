/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.extension.fapi;

import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.basic.type.oauth.ClientAuthenticationType;
import org.idp.server.basic.type.oauth.ClientSecret;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.basic.x509.X509CertInvalidException;
import org.idp.server.basic.x509.X509Certification;
import org.idp.server.core.oidc.clientauthenticator.BackchannelRequestContext;
import org.idp.server.core.oidc.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.oidc.clientauthenticator.plugin.ClientAuthenticator;
import org.idp.server.core.oidc.clientcredentials.ClientAssertionJwt;
import org.idp.server.core.oidc.clientcredentials.ClientAuthenticationPublicKey;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.mtls.ClientCertification;

public class TlsClientAuthAuthenticator implements ClientAuthenticator {

  @Override
  public ClientAuthenticationType type() {
    return ClientAuthenticationType.tls_client_auth;
  }

  @Override
  public ClientCredentials authenticate(BackchannelRequestContext context) {
    throwExceptionIfNotContainsClientCert(context);
    X509Certification x509Certification = parseOrThrowExceptionIfNoneMatch(context);
    RequestedClientId requestedClientId = context.requestedClientId();
    ClientSecret clientSecret = new ClientSecret();
    ClientCertification clientCertification = new ClientCertification(x509Certification);
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
    if (!clientCert.exists()) {
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
      throw new ClientUnAuthorizedException("client_cert does not match any subject names");
    } catch (X509CertInvalidException e) {
      throw new ClientUnAuthorizedException("client_cert is malformed");
    }
  }
}

/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.extension.fapi;

import java.util.List;
import org.idp.server.basic.jose.JsonWebKey;
import org.idp.server.basic.jose.JsonWebKeyInvalidException;
import org.idp.server.basic.jose.JsonWebKeys;
import org.idp.server.basic.jose.JwkParser;
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
import org.idp.server.core.oidc.mtls.ClientCertification;

public class SelfSignedTlsClientAuthAuthenticator implements ClientAuthenticator {

  @Override
  public ClientAuthenticationType type() {
    return ClientAuthenticationType.self_signed_tls_client_auth;
  }

  @Override
  public ClientCredentials authenticate(BackchannelRequestContext context) {
    throwExceptionIfNotContainsClientCert(context);
    ClientCertification clientCertification =
        parseOrThrowExceptionIfUnSpecifiedOrUnMatchKey(context);
    RequestedClientId requestedClientId = context.requestedClientId();
    ClientSecret clientSecret = new ClientSecret();
    return new ClientCredentials(
        requestedClientId,
        ClientAuthenticationType.self_signed_tls_client_auth,
        clientSecret,
        new ClientAuthenticationPublicKey(),
        new ClientAssertionJwt(),
        clientCertification);
  }

  void throwExceptionIfNotContainsClientCert(BackchannelRequestContext context) {
    ClientCert clientCert = context.clientCert();
    if (!clientCert.exists()) {
      throw new ClientUnAuthorizedException(
          "client authentication type is self_signed_tls_client_auth, but request does not contains client_cert");
    }
  }

  ClientCertification parseOrThrowExceptionIfUnSpecifiedOrUnMatchKey(
      BackchannelRequestContext context) {
    try {
      String jwks = context.clientConfiguration().jwks();
      JsonWebKeys jsonWebKeys = JwkParser.parseKeys(jwks);
      JsonWebKeys filterWithX5c = jsonWebKeys.filterWithX5c();
      if (!filterWithX5c.exists()) {
        throw new ClientUnAuthorizedException("unregistered jwk with x5c");
      }
      if (filterWithX5c.isMultiValues()) {
        throw new ClientUnAuthorizedException("multi registered jwk with x5c");
      }
      JsonWebKey jsonWebKey = filterWithX5c.getFirst();
      List<String> x5cList = jsonWebKey.x5c();
      ClientCert clientCert = context.clientCert();
      X509Certification x509Certification = X509Certification.parse(clientCert.plainValue());
      String der = x509Certification.derWithBase64();
      if (!x5cList.contains(der)) {
        throw new ClientUnAuthorizedException("client cert does not match registered jwk");
      }
      return new ClientCertification(x509Certification);
    } catch (JsonWebKeyInvalidException e) {
      throw new ClientUnAuthorizedException("registered jwk is invalid");
    } catch (X509CertInvalidException e) {
      throw new ClientUnAuthorizedException("invalid client cert");
    }
  }
}

package org.idp.server.clientauthenticator;

import org.idp.server.basic.x509.X509CertInvalidException;
import org.idp.server.basic.x509.X509Certification;
import org.idp.server.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.oauth.clientcredentials.ClientAuthenticationPublicKey;
import org.idp.server.oauth.clientcredentials.ClientCredentials;
import org.idp.server.oauth.mtls.ClientCertification;
import org.idp.server.type.mtls.ClientCert;
import org.idp.server.type.oauth.ClientAuthenticationType;
import org.idp.server.type.oauth.ClientId;
import org.idp.server.type.oauth.ClientSecret;

class TlsClientAuthAuthenticator implements ClientAuthenticator {

  @Override
  public ClientCredentials authenticate(BackchannelRequestContext context) {
    throwExceptionIfNotContainsClientCert(context);
    X509Certification x509Certification = parseAndThrowExceptionIfNoneMatch(context);
    ClientId clientId = context.clientConfiguration().clientId();
    ClientSecret clientSecret = new ClientSecret();
    ClientCertification clientCertification = new ClientCertification(x509Certification);
    return new ClientCredentials(
        clientId,
        ClientAuthenticationType.tls_client_auth,
        clientSecret,
        new ClientAuthenticationPublicKey(),
        clientCertification);
  }

  void throwExceptionIfNotContainsClientCert(BackchannelRequestContext context) {
    ClientCert clientCert = context.clientCert();
    if (!clientCert.exists()) {
      throw new ClientUnAuthorizedException(
          "client authentication type is tls_client_auth, but request does not contains client_cert");
    }
  }

  X509Certification parseAndThrowExceptionIfNoneMatch(BackchannelRequestContext context) {
    ClientCert clientCert = context.clientCert();
    try {
      X509Certification x509Certification = X509Certification.parse(clientCert.value());
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

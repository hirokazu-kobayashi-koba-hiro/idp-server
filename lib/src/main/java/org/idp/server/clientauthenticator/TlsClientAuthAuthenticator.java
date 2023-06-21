package org.idp.server.clientauthenticator;

import org.idp.server.basic.x509.X509CertInvalidException;
import org.idp.server.basic.x509.X509Certification;
import org.idp.server.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.type.mtls.ClientCert;

class TlsClientAuthAuthenticator implements ClientAuthenticator {

  @Override
  public void authenticate(BackchannelRequestContext context) {
    throwExceptionIfNotContainsClientCert(context);
    throwExceptionIfNoneMatch(context);
  }

  void throwExceptionIfNotContainsClientCert(BackchannelRequestContext context) {
    ClientCert clientCert = context.clientCert();
    if (!clientCert.exists()) {
      throw new ClientUnAuthorizedException(
          "client authentication type is tls_client_auth, but request does not contains client_cert");
    }
  }

  void throwExceptionIfNoneMatch(BackchannelRequestContext context) {
    ClientCert clientCert = context.clientCert();
    try {
      X509Certification x509Certification = X509Certification.parse(clientCert.value());
      ClientConfiguration clientConfiguration = context.clientConfiguration();
      // tls_client_auth_subject_dn
      if (x509Certification.subject().equals(clientConfiguration.tlsClientAuthSubjectDn())) {
        return;
      }
      // tls_client_auth_san_dns dNSName
      if (x509Certification.hasDNSName()
          && x509Certification.dNSName().equals(clientConfiguration.tlsClientAuthSanDns())) {
        return;
      }
      // tls_client_auth_san_uri uniformResourceIdentifier
      if (x509Certification.hasUniformResourceIdentifier()
          && x509Certification
              .uniformResourceIdentifier()
              .equals(clientConfiguration.tlsClientAuthSanUri())) {
        return;
      }
      // tls_client_auth_san_ip iPAddress
      if (x509Certification.hasIPAddress()
          && x509Certification.iPAddress().equals(clientConfiguration.tlsClientAuthSanIp())) {
        return;
      }
      // tls_client_auth_san_email rfc822Name
      if (x509Certification.hasRfc822Name()
          && x509Certification.rfc822Name().equals(clientConfiguration.tlsClientAuthSanEmail())) {
        return;
      }
      throw new ClientUnAuthorizedException("client_cert does not match any subject names");
    } catch (X509CertInvalidException e) {
      throw new ClientUnAuthorizedException("client_cert is malformed");
    }
  }
}

package org.idp.server.core.verifiablecredential.verifier;

import java.time.LocalDateTime;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.basic.x509.X509CertInvalidException;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.mtls.ClientCertification;
import org.idp.server.core.oauth.mtls.ClientCertificationThumbprint;
import org.idp.server.core.oauth.mtls.ClientCertificationThumbprintCalculator;
import org.idp.server.core.oauth.rar.AuthorizationDetails;
import org.idp.server.core.oauth.token.AccessToken;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.tokenintrospection.exception.TokenInvalidException;
import org.idp.server.core.type.mtls.ClientCert;
import org.idp.server.core.verifiablecredential.exception.VerifiableCredentialTokenInvalidException;

public class VerifiableCredentialOAuthTokenVerifier {
  OAuthToken oAuthToken;
  ClientCert clientCert;
  ServerConfiguration serverConfiguration;

  public VerifiableCredentialOAuthTokenVerifier(
      OAuthToken oAuthToken, ClientCert clientCert, ServerConfiguration serverConfiguration) {
    this.oAuthToken = oAuthToken;
    this.clientCert = clientCert;
    this.serverConfiguration = serverConfiguration;
  }

  public void verify() {
    throwExceptionIfNotFoundToken();
    throwExceptionIfUnMatchClientCert();
    throwExceptionIfNotGranted();
  }

  void throwExceptionIfNotFoundToken() {
    if (!oAuthToken.exists()) {
      throw new TokenInvalidException("not found token");
    }
    LocalDateTime now = SystemDateTime.now();
    if (oAuthToken.isExpire(now)) {
      throw new TokenInvalidException("token is expired");
    }
  }

  void throwExceptionIfUnMatchClientCert() {
    if (!oAuthToken.hasClientCertification()) {
      return;
    }
    if (!clientCert.exists()) {
      throw new VerifiableCredentialTokenInvalidException(
          "access token is sender constrained, but mtls client cert does not exists");
    }
    try {
      ClientCertification clientCertification = ClientCertification.parse(clientCert.plainValue());
      ClientCertificationThumbprintCalculator calculator =
          new ClientCertificationThumbprintCalculator(clientCertification);
      ClientCertificationThumbprint thumbprint = calculator.calculate();
      AccessToken accessToken = oAuthToken.accessToken();
      if (!accessToken.matchThumbprint(thumbprint)) {
        throw new VerifiableCredentialTokenInvalidException(
            "access token and mtls client cert is unmatch");
      }
    } catch (X509CertInvalidException e) {
      throw new VerifiableCredentialTokenInvalidException(
          "access token is sender constrained, but mtls client cert is invalid format", e);
    }
  }

  void throwExceptionIfNotGranted() {
    AccessToken accessToken = oAuthToken.accessToken();
    if (!accessToken.hasAuthorizationDetails()) {
      throw new VerifiableCredentialTokenInvalidException(
          "access token have to contain authorization details at credential endpoint");
    }
    AuthorizationDetails authorizationDetails = accessToken.authorizationDetails();
    if (!authorizationDetails.hasVerifiableCredential()) {
      throw new VerifiableCredentialTokenInvalidException(
          "access token have to contain authorization details at credential endpoint");
    }
  }
}

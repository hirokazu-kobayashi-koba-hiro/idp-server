package org.idp.server.core.extension.verifiable_credentials.verifier;

import java.time.LocalDateTime;
import org.idp.server.basic.type.mtls.ClientCert;
import org.idp.server.basic.x509.X509CertInvalidException;
import org.idp.server.core.extension.verifiable_credentials.exception.VerifiableCredentialTokenInvalidException;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.mtls.ClientCertification;
import org.idp.server.core.oidc.mtls.ClientCertificationThumbprint;
import org.idp.server.core.oidc.mtls.ClientCertificationThumbprintCalculator;
import org.idp.server.core.oidc.rar.AuthorizationDetails;
import org.idp.server.core.oidc.token.AccessToken;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.token.tokenintrospection.exception.TokenInvalidException;
import org.idp.server.platform.date.SystemDateTime;

public class VerifiableCredentialOAuthTokenVerifier {
  OAuthToken oAuthToken;
  ClientCert clientCert;
  AuthorizationServerConfiguration authorizationServerConfiguration;

  public VerifiableCredentialOAuthTokenVerifier(
      OAuthToken oAuthToken,
      ClientCert clientCert,
      AuthorizationServerConfiguration authorizationServerConfiguration) {
    this.oAuthToken = oAuthToken;
    this.clientCert = clientCert;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
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

package org.idp.server.core.userinfo.verifier;

import java.time.LocalDateTime;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.basic.x509.X509CertInvalidException;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.mtls.ClientCertification;
import org.idp.server.core.oauth.mtls.ClientCertificationThumbprint;
import org.idp.server.core.oauth.mtls.ClientCertificationThumbprintCalculator;
import org.idp.server.core.oauth.token.AccessToken;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.tokenintrospection.exception.TokenInvalidException;
import org.idp.server.core.type.mtls.ClientCert;
import org.idp.server.core.verifiablecredential.exception.VerifiableCredentialTokenInvalidException;

public class UserinfoVerifier {

  OAuthToken oAuthToken;
  ClientCert clientCert;
  User user;

  public UserinfoVerifier(OAuthToken oAuthToken, ClientCert clientCert, User user) {
    this.oAuthToken = oAuthToken;
    this.clientCert = clientCert;
    this.user = user;
  }

  public void verify() {
    throwExceptionIfNotFoundToken();
    throwExceptionIfUnMatchClientCert();
    // FIXME
    if (!user.exists()) {
      throw new TokenInvalidException("not found user");
    }
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
}

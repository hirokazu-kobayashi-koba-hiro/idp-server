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

package org.idp.server.core.openid.userinfo.verifier;

import java.time.LocalDateTime;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertification;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertificationThumbprint;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertificationThumbprintCalculator;
import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.token.AccessToken;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.tokenintrospection.exception.TokenInvalidException;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.x509.X509CertInvalidException;

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
    throwExceptionIfNotFoundUser();
    throwExceptionIfInactiveUser();
  }

  void throwExceptionIfNotFoundUser() {
    if (!user.exists()) {
      throw new TokenInvalidException("not found user");
    }
  }

  void throwExceptionIfInactiveUser() {
    if (!user.isActive()) {
      throw new TokenInvalidException(
          String.format(
              "user is not active (id: %s, status: %s)", user.sub(), user.status().name()));
    }
  }

  void throwExceptionIfNotFoundToken() {
    if (!oAuthToken.exists()) {
      throw new TokenInvalidException("not found token");
    }
    LocalDateTime now = SystemDateTime.now();
    if (oAuthToken.isExpiredAccessToken(now)) {
      throw new TokenInvalidException("token is expired");
    }
  }

  void throwExceptionIfUnMatchClientCert() {
    if (!oAuthToken.hasClientCertification()) {
      return;
    }
    if (!clientCert.exists()) {
      throw new TokenInvalidException(
          "access token is sender constrained, but mtls client cert does not exists");
    }
    try {
      ClientCertification clientCertification = ClientCertification.parse(clientCert.plainValue());
      ClientCertificationThumbprintCalculator calculator =
          new ClientCertificationThumbprintCalculator(clientCertification);
      ClientCertificationThumbprint thumbprint = calculator.calculate();
      AccessToken accessToken = oAuthToken.accessToken();
      if (!accessToken.matchThumbprint(thumbprint)) {
        throw new TokenInvalidException("access token and mtls client cert is unmatch");
      }
    } catch (X509CertInvalidException e) {
      throw new TokenInvalidException(
          "access token is sender constrained, but mtls client cert is invalid format", e);
    }
  }
}

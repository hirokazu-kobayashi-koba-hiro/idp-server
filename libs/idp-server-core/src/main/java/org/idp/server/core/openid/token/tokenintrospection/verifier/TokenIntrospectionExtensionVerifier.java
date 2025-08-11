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

package org.idp.server.core.openid.token.tokenintrospection.verifier;

import java.time.LocalDateTime;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertification;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertificationThumbprint;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertificationThumbprintCalculator;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.token.AccessToken;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.tokenintrospection.exception.TokenCertificationBindingInvalidException;
import org.idp.server.core.openid.token.tokenintrospection.exception.TokenInsufficientScopeException;
import org.idp.server.core.openid.token.tokenintrospection.exception.TokenInvalidException;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.x509.X509CertInvalidException;

public class TokenIntrospectionExtensionVerifier {

  ClientCert clientCert;
  Scopes scopes;
  OAuthToken oAuthToken;
  AuthorizationServerConfiguration authorizationServerConfiguration;
  ClientConfiguration clientConfiguration;
  LoggerWrapper log = LoggerWrapper.getLogger(TokenIntrospectionExtensionVerifier.class);

  public TokenIntrospectionExtensionVerifier(
      ClientCert clientCert,
      Scopes scopes,
      OAuthToken oAuthToken,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    this.clientCert = clientCert;
    this.scopes = scopes;
    this.oAuthToken = oAuthToken;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  public void verify() {
    verifyTokenExistence();
    verifyExpiration();
    verifySenderConstrainedIfRequired();
    verifyScope();
  }

  private void verifyTokenExistence() {
    if (!oAuthToken.exists()) {
      throw new TokenInvalidException("Token not found or invalid.");
    }
  }

  private void verifyExpiration() {
    LocalDateTime now = SystemDateTime.now();

    if (oAuthToken.isExpiredAccessToken(now) && oAuthToken.isExpiredRefreshToken(now)) {
      throw new TokenInvalidException("Token has expired (access and refresh tokens).");
    }

    if (oAuthToken.isExpiredAccessToken(now) && !oAuthToken.isExpiredRefreshToken(now)) {
      throw new TokenInvalidException(
          "Access token has expired, but the refresh token is still valid.");
    }
  }

  private void verifySenderConstrainedIfRequired() {
    if (!oAuthToken.hasClientCertification()) {
      return;
    }

    if (!clientCert.exists()) {
      throw new TokenCertificationBindingInvalidException(
          "Sender-constrained access token requires mTLS client certificate, but none was provided.");
    }

    try {
      ClientCertification clientCertification = ClientCertification.parse(clientCert.plainValue());
      ClientCertificationThumbprint thumbprint =
          new ClientCertificationThumbprintCalculator(clientCertification).calculate();

      AccessToken accessToken = oAuthToken.accessToken();
      if (!accessToken.matchThumbprint(thumbprint)) {
        throw new TokenCertificationBindingInvalidException(
            "mTLS client certificate thumbprint does not match the sender-constrained access token.");
      }

    } catch (X509CertInvalidException e) {
      log.warn(e.getMessage(), e);
      throw new TokenCertificationBindingInvalidException(
          String.format(
              "Invalid mTLS client certificate format for sender-constrained access token: %s",
              e.getMessage()));
    }
  }

  private void verifyScope() {
    if (!scopes.exists()) {
      return;
    }

    if (!oAuthToken.isGrantedScopes(scopes)) {
      throw new TokenInsufficientScopeException(
          String.format(
              "Requested scopes are not granted. Requested: %s, Granted: %s",
              scopes.toStringValues(), oAuthToken.scopes().toStringValues()));
    }
  }
}

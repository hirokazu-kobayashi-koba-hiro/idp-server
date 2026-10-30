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

package org.idp.server.account_linking.verifier;

import org.idp.server.account_linking.exception.LinkedExternalAccountTokenException;
import org.idp.server.account_linking.io.LinkedExternalAccountTokenRequest;
import org.idp.server.core.openid.clientinstance.ClientInstanceThumbprint;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientAuthenticationPublicKey;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.dpop.DPoPHeaderValidator;
import org.idp.server.core.openid.oauth.dpop.DPoPProofInvalidException;
import org.idp.server.core.openid.oauth.dpop.DPoPProofVerifier;
import org.idp.server.core.openid.oauth.replay.JwtReplayDetector;
import org.idp.server.core.openid.token.AccessToken;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.tokenintrospection.exception.TokenCertificationBindingInvalidException;
import org.idp.server.core.openid.token.tokenintrospection.verifier.CertificateBindingVerifier;
import org.idp.server.core.openid.token.tokenintrospection.verifier.DPoPBindingVerifier;
import org.idp.server.platform.jose.JsonWebKeyInvalidException;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * The user's token is presented by the sender it is bound to.
 *
 * <p>A sender-constrained token is only worth something if the proof is checked here too; accepted
 * without it, it would be a bearer token. Each binding is checked the way the rest of the server
 * checks it: a DPoP proof for {@code cnf.jkt}, the TLS client certificate for {@code cnf.x5t#S256},
 * and for a token issued to a Client Instance, the instance key that verified the Client
 * Attestation on this very request. A token without a binding passes untouched.
 */
public class LinkedExternalAccountTokenBindingVerifier {

  DPoPBindingVerifier dpopBindingVerifier;
  CertificateBindingVerifier certificateBindingVerifier;

  /** DPoP proofs are checked against the tenant's window and replay cache, as at {@code /me}. */
  public static LinkedExternalAccountTokenBindingVerifier of(
      Tenant tenant,
      AuthorizationServerConfiguration serverConfiguration,
      JwtReplayDetector replayDetector) {
    return new LinkedExternalAccountTokenBindingVerifier(
        new DPoPBindingVerifier(
            new DPoPProofVerifier(
                tenant, serverConfiguration.dpopProofAcceptableWindow(), replayDetector)));
  }

  public LinkedExternalAccountTokenBindingVerifier(DPoPBindingVerifier dpopBindingVerifier) {
    this.dpopBindingVerifier = dpopBindingVerifier;
    this.certificateBindingVerifier = new CertificateBindingVerifier();
  }

  public void verify(
      OAuthToken userToken,
      LinkedExternalAccountTokenRequest request,
      ClientCredentials clientCredentials) {
    verifyDPoPBinding(userToken, request);
    verifyCertificateBinding(userToken, request);
    verifyClientInstanceBinding(userToken.accessToken(), clientCredentials);
  }

  /** RFC 9449: the proof's key is the one in {@code cnf.jkt}, and the proof covers this token. */
  private void verifyDPoPBinding(OAuthToken userToken, LinkedExternalAccountTokenRequest request) {
    try {
      new DPoPHeaderValidator(request.dpopProofHeaders()).validate();
    } catch (DPoPProofInvalidException e) {
      throw LinkedExternalAccountTokenException.invalidRequest(e.getMessage());
    }
    try {
      dpopBindingVerifier.verify(
          request.dpopProof(), request.httpMethod(), request.httpUri(), userToken);
    } catch (DPoPProofInvalidException e) {
      throw LinkedExternalAccountTokenException.invalidToken(e.getMessage());
    }
  }

  /** RFC 8705: the TLS client certificate is the one in {@code cnf.x5t#S256}. */
  private void verifyCertificateBinding(
      OAuthToken userToken, LinkedExternalAccountTokenRequest request) {
    try {
      certificateBindingVerifier.verify(request.clientCert(), userToken);
    } catch (TokenCertificationBindingInvalidException e) {
      throw LinkedExternalAccountTokenException.invalidToken(e.getMessage());
    }
  }

  /**
   * The Client Instance the token was issued to is the one asking.
   *
   * <p>Several installs of one app share a {@code client_id}, so authenticating as the client does
   * not say which install is asking. The key compared is the one the authenticator accepted on this
   * request, not one the caller names. A registered instance has to match as well, so a key
   * registered again does not inherit the tokens of the registration it replaced.
   */
  private void verifyClientInstanceBinding(
      AccessToken accessToken, ClientCredentials clientCredentials) {
    if (!accessToken.hasClientInstanceBinding()) {
      return;
    }

    if (!accessToken.matchClientInstanceThumbprint(presentedThumbprint(clientCredentials))) {
      throw LinkedExternalAccountTokenException.invalidToken(
          "token was issued to a different client instance of this client.");
    }
    if (accessToken.hasClientInstanceIdentifier()
        && !accessToken.matchClientInstanceIdentifier(
            clientCredentials.clientInstance().identifier())) {
      throw LinkedExternalAccountTokenException.invalidToken(
          "token was issued to another registration of this key.");
    }
  }

  private ClientInstanceThumbprint presentedThumbprint(ClientCredentials clientCredentials) {
    ClientAuthenticationPublicKey instanceKey = clientCredentials.clientAuthenticationPublicKey();
    if (!instanceKey.exists()) {
      throw LinkedExternalAccountTokenException.invalidToken(
          "token is bound to a client instance, but the client attestation carried no instance key.");
    }

    try {
      return new ClientInstanceThumbprint(instanceKey.thumbprintSha256());
    } catch (JsonWebKeyInvalidException e) {
      throw LinkedExternalAccountTokenException.invalidToken(
          "failed to read the client instance key: " + e.getMessage());
    }
  }
}

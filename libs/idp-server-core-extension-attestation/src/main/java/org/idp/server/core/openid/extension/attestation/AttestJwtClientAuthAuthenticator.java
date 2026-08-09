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

package org.idp.server.core.openid.extension.attestation;

import org.idp.server.core.openid.clientinstance.ClientAttestationTrustSource;
import org.idp.server.core.openid.oauth.clientattestation.ClientAttestationJwt;
import org.idp.server.core.openid.oauth.clientattestation.ClientAttestationPopJwt;
import org.idp.server.core.openid.oauth.clientauthenticator.BackchannelRequestContext;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientAssertionJwt;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientAuthenticationPublicKey;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertification;
import org.idp.server.core.openid.oauth.clientauthenticator.plugin.ClientAuthenticator;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecret;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.jose.JsonWebKey;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * Attestation-Based Client Authentication ({@code attest_jwt_client_auth}).
 *
 * <p>Authenticates a client with the two JWTs defined by
 * draft-ietf-oauth-attestation-based-client-auth-10:
 *
 * <ul>
 *   <li>{@code OAuth-Client-Attestation} header — Client Attestation JWT, verified with the key
 *       supplied by the resolver of the configured {@link ClientAttestationTrustSource}
 *   <li>{@code OAuth-Client-Attestation-PoP} header — proof of possession of the Client Instance
 *       Key ({@code cnf.jwk} of the Client Attestation JWT), signed by the client instance
 * </ul>
 *
 * @see <a
 *     href="https://www.ietf.org/archive/id/draft-ietf-oauth-attestation-based-client-auth-10.html">OAuth
 *     2.0 Attestation-Based Client Authentication</a>
 */
public class AttestJwtClientAuthAuthenticator implements ClientAuthenticator {

  LoggerWrapper log = LoggerWrapper.getLogger(AttestJwtClientAuthAuthenticator.class);
  ClientAttestationKeyResolvers keyResolvers;

  public AttestJwtClientAuthAuthenticator(ClientAttestationKeyResolvers keyResolvers) {
    this.keyResolvers = keyResolvers;
  }

  @Override
  public ClientAuthenticationType type() {
    return ClientAuthenticationType.attest_jwt_client_auth;
  }

  @Override
  public ClientCredentials authenticate(BackchannelRequestContext context) {
    RequestedClientId requestedClientId = context.requestedClientId();

    throwExceptionIfNotContainsAttestationHeaders(context);

    ClientAttestationTrustSource trustSource =
        context.clientConfiguration().extensionConfiguration().clientAttestationTrustSource();
    if (trustSource.isUndefined()) {
      throw new ClientUnAuthorizedException(
          ClientAuthenticationType.attest_jwt_client_auth.name(),
          requestedClientId,
          "client_attestation_trust_source is not configured or has an unknown value");
    }
    ClientAttestationKeyResolver keyResolver = keyResolvers.get(trustSource);

    JsonWebKey clientInstanceKey =
        new ClientAttestationJwtVerifier(context, keyResolver, trustSource).verify();
    JsonWebSignature popJws =
        new ClientAttestationPopJwtVerifier(context, clientInstanceKey).verify();

    log.debug(
        "Client authentication succeeded: method={}, client_id={}",
        ClientAuthenticationType.attest_jwt_client_auth.name(),
        requestedClientId.value());

    return new ClientCredentials(
        requestedClientId,
        ClientAuthenticationType.attest_jwt_client_auth,
        new ClientSecret(),
        new ClientAuthenticationPublicKey(clientInstanceKey),
        new ClientAssertionJwt(popJws),
        new ClientCertification());
  }

  void throwExceptionIfNotContainsAttestationHeaders(BackchannelRequestContext context) {
    RequestedClientId requestedClientId = context.requestedClientId();
    if (!context.clientAttestationJwt().exists()) {
      throw new ClientUnAuthorizedException(
          ClientAuthenticationType.attest_jwt_client_auth.name(),
          requestedClientId,
          String.format("request does not contain %s header", ClientAttestationJwt.HEADER_NAME));
    }
    if (!context.clientAttestationPopJwt().exists()) {
      throw new ClientUnAuthorizedException(
          ClientAuthenticationType.attest_jwt_client_auth.name(),
          requestedClientId,
          String.format("request does not contain %s header", ClientAttestationPopJwt.HEADER_NAME));
    }
  }
}

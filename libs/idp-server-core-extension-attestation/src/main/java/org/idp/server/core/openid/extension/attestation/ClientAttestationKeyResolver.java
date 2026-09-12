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

import org.idp.server.core.openid.oauth.clientauthenticator.BackchannelRequestContext;
import org.idp.server.platform.jose.JsonWebSignatureHeader;

/**
 * Resolves the trusted keys used to verify the Client Attestation JWT signature.
 *
 * <p>draft-ietf-oauth-attestation-based-client-auth-10 Section 9.8 leaves the trust establishment
 * between the Authorization Server and the Client Attester out of scope. This interface is the
 * extension point for that deployment choice:
 *
 * <ul>
 *   <li>Attester model — a backend Client Attester signs the Client Attestation JWT; the trusted
 *       keys are statically configured per client ({@link StaticJwksClientAttestationKeyResolver})
 *   <li>Self-signed model — the Client Instance signs the Client Attestation JWT with its own
 *       Client Instance Key registered at the Authorization Server beforehand; the resolver looks
 *       up the registered key (e.g. by {@code (tenant, client_id, kid)})
 * </ul>
 *
 * <p>The resolved JWKS provides candidate keys only: key selection (by {@code kid}/{@code alg}) and
 * the trust decision itself happen in the JOSE verification layer — authentication succeeds only
 * when the signature verifies with a resolved key. The {@code kid} of the JOSE header MUST NOT be
 * treated as a trust input by implementations.
 */
public interface ClientAttestationKeyResolver {

  /**
   * Returns the trusted keys for verifying the Client Attestation JWT of the requesting client.
   *
   * @param context the backchannel request context identifying tenant and client
   * @param header the JOSE header of the presented Client Attestation JWT (e.g. {@code kid} for a
   *     registered-key lookup)
   * @return the trusted keys in JWKS format, or {@code null} / empty when no trusted key is
   *     available (the authenticator rejects the client in that case)
   */
  String resolveJwks(BackchannelRequestContext context, JsonWebSignatureHeader header);
}

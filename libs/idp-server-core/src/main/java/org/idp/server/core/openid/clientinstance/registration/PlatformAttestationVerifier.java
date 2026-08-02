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

package org.idp.server.core.openid.clientinstance.registration;

/**
 * Verifies the platform attestation presented at Client Instance registration.
 *
 * <p>The registration endpoint is unauthenticated, so this verification <b>is</b> the
 * authentication of the request. An implementation that accepts evidence it cannot verify makes the
 * whole of {@code attest_jwt_client_auth} meaningless: anyone could register a key for a client and
 * then authenticate as it.
 *
 * <h2>Contract</h2>
 *
 * Implementations MUST establish all three bindings, not only that the evidence is well formed:
 *
 * <ol>
 *   <li><b>Challenge</b> — the evidence was produced for the challenge of this registration (Key
 *       Attestation challenge extension, {@code request_hash}, {@code client_data_hash})
 *   <li><b>Instance key</b> — the evidence covers the Client Instance Key being registered, so a
 *       captured piece of evidence cannot be paired with an attacker's key
 *   <li><b>Application identity</b> — the attested application is the one configured for the client
 *       (Android {@code attestationApplicationId} / Play Integrity package name and signing
 *       certificate digest, iOS App ID)
 * </ol>
 *
 * <p>Certificate chains MUST be validated to a pinned root. A chain is untrusted input: a
 * self-signed chain that merely parses proves nothing.
 */
public interface PlatformAttestationVerifier {

  /** Platform this verifier handles, as sent in {@code platform_evidence.platform}. */
  String platform();

  /**
   * Verifies the evidence, or throws when it does not hold.
   *
   * @throws PlatformAttestationVerificationException when any binding or chain check fails
   */
  void verify(PlatformAttestationVerificationRequest request);
}

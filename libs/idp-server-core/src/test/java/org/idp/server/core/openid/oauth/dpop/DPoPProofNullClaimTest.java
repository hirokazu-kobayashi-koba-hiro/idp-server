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

package org.idp.server.core.openid.oauth.dpop;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Regression for issue #1776: a DPoP proof carrying a JSON {@code null} for a required claim must
 * be rejected as an invalid proof, not crash with an NPE (→ HTTP 500).
 *
 * <p>Both proofs below are self-signed (the verifying public key is embedded in the {@code jwk}
 * header), so signature verification passes and the required-claim checks are reached — the exact
 * unauthenticated path an attacker controls. Their payloads carry the literal {@code "jti":null} /
 * {@code "iat":null}, which Nimbus preserves on parse. Before the fix, {@code hasXxx()} reported
 * the claim present, the guard was passed, and the null getter was dereferenced. The {@code iat}
 * value in the {@code jti:null} proof is arbitrary because the {@code jti} check throws first.
 *
 * <p>The proofs are fixtures generated once with a throwaway ES256 key; regenerate with a JWS built
 * over the raw payload bytes (a {@code JWTClaimsSet} drops null claims on serialization).
 */
class DPoPProofNullClaimTest {

  private static final String HTU = "https://as.example.com/token";

  // payload: {"jti":null,"htm":"POST","htu":"https://as.example.com/token","iat":1700000000}
  private static final String PROOF_NULL_JTI =
      "eyJ0eXAiOiJkcG9wK2p3dCIsImFsZyI6IkVTMjU2IiwiandrIjp7Imt0eSI6IkVDIiwiY3J2IjoiUC0yNTYiLCJraWQiOiJkcG9wLXRlc3QiLCJ4IjoiV0tuZUowNjlrdFVqWmllRkpJWHVlMUlpN2JEZWlUaUdXSEIzb2VCaHFqSSIsInkiOiJ5TGxVVVJIcWVsTHVlVWhWcTItRDBybENBMmFnZGliR2dEa0QtdUgtNlBJIn19.eyJqdGkiOm51bGwsImh0bSI6IlBPU1QiLCJodHUiOiJodHRwczovL2FzLmV4YW1wbGUuY29tL3Rva2VuIiwiaWF0IjoxNzAwMDAwMDAwfQ.28BPA92HaGXs6m1LEqPNEhgjpV1vJcTUPm2p4_d2DgvDFOggz-B67CbUdaStuKpBtG536zQIQz192m0VWZ9v_g";

  // payload: {"jti":"j1","htm":"POST","htu":"https://as.example.com/token","iat":null}
  private static final String PROOF_NULL_IAT =
      "eyJ0eXAiOiJkcG9wK2p3dCIsImFsZyI6IkVTMjU2IiwiandrIjp7Imt0eSI6IkVDIiwiY3J2IjoiUC0yNTYiLCJraWQiOiJkcG9wLXRlc3QiLCJ4IjoiV0tuZUowNjlrdFVqWmllRkpJWHVlMUlpN2JEZWlUaUdXSEIzb2VCaHFqSSIsInkiOiJ5TGxVVVJIcWVsTHVlVWhWcTItRDBybENBMmFnZGliR2dEa0QtdUgtNlBJIn19.eyJqdGkiOiJqMSIsImh0bSI6IlBPU1QiLCJodHUiOiJodHRwczovL2FzLmV4YW1wbGUuY29tL3Rva2VuIiwiaWF0IjpudWxsfQ.ksMkU9WyziUofHfJKbvI5BX4iZzOCVRro-eWfpdCWUQPuTCtk3qOjv8TkBbqsyy-N-O-tzEdZhFo83tyBOIsAQ";

  private final DPoPProofVerifier verifier = new DPoPProofVerifier();

  @Test
  void nullJtiIsRejectedAsInvalidProofNotNpe() {
    DPoPProofInvalidException ex =
        assertThrows(
            DPoPProofInvalidException.class,
            () -> verifier.verify(new DPoPProof(PROOF_NULL_JTI), "POST", HTU, null));
    assertTrue(ex.getMessage().contains("jti"), ex.getMessage());
  }

  @Test
  void nullIatIsRejectedAsInvalidProofNotNpe() {
    DPoPProofInvalidException ex =
        assertThrows(
            DPoPProofInvalidException.class,
            () -> verifier.verify(new DPoPProof(PROOF_NULL_IAT), "POST", HTU, null));
    assertTrue(ex.getMessage().contains("iat"), ex.getMessage());
  }
}

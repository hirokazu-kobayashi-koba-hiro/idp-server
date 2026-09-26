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

package org.idp.server.core.openid.extension.attestation.ios;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.jwk.ECKey;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationEvidence;
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationVerificationException;
import org.idp.server.core.openid.extension.attestation.PlatformChallengeBinding;
import org.idp.server.core.openid.extension.attestation.StubVerificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Verifying an Apple App Attest attestation.
 *
 * <p>Each test removes exactly one property of a valid attestation and asserts that registration
 * stops. A verifier that accepts evidence it cannot tie to this challenge, this key and this
 * application would let anyone register a key and then authenticate as the client.
 */
class IosAppAttestVerifierTest {

  static final String CHALLENGE = "Zm9vYmFyLWNoYWxsZW5nZS0wMQ";

  IosAppAttestFixture fixture;
  IosAppAttestVerifier verifier;
  KeyPair instanceKeyPair;

  @BeforeEach
  void setUp() throws Exception {
    fixture = new IosAppAttestFixture();
    verifier = new IosAppAttestVerifier();
    instanceKeyPair = IosAppAttestFixture.generateKeyPair();
  }

  private byte[] challengeBytes() {
    return Base64.getUrlDecoder().decode(CHALLENGE);
  }

  private Map<String, Object> instanceKeyAsJwk() throws Exception {
    ECKey ecKey =
        new ECKey.Builder(
                com.nimbusds.jose.jwk.Curve.P_256, (ECPublicKey) instanceKeyPair.getPublic())
            .build();
    return ecKey.toPublicJWK().toJSONObject();
  }

  private Map<String, Object> evidence(String attestationObject) {
    return Map.of(
        "platform",
        IosAppAttestVerifier.PLATFORM,
        IosAppAttestObject.EVIDENCE_KEY,
        attestationObject);
  }

  private Map<String, Object> clientPlatformConfig(String rootBase64) {
    return clientPlatformConfig(rootBase64, "production");
  }

  private Map<String, Object> clientPlatformConfig(String rootBase64, String environment) {
    return Map.of(
        "client_instance_platform_config",
        Map.of(
            "ios_app_attest",
            Map.of(
                "app_ids", List.of(IosAppAttestFixture.APP_ID),
                "environment", environment,
                "override_root_certificates", List.of(rootBase64))));
  }

  /**
   * Asserts the attestation is rejected, and rejected by the check the test is about.
   *
   * <p>The reason matters as much as the rejection: every check here removes one property, and a
   * test that passes because an earlier check happened to fire would stop covering its own case
   * without ever going red.
   */
  private void assertRejected(String attestationObject, String reason) {
    PlatformAttestationVerificationException exception =
        assertThrows(
            PlatformAttestationVerificationException.class, () -> verify(attestationObject));

    assertTrue(
        exception.getMessage().contains(reason),
        "rejected, but for another reason: " + exception.getMessage());
  }

  private PlatformAttestationEvidence verify(String attestationObject) throws Exception {
    return verifier.verify(
        StubVerificationRequest.of(
            clientPlatformConfig(fixture.rootBase64()),
            CHALLENGE,
            instanceKeyAsJwk(),
            evidence(attestationObject)));
  }

  @Nested
  class Bindings {

    @Test
    void acceptsAnAttestationThatSatisfiesAllThreeBindings() throws Exception {
      String attestation = fixture.attestation(instanceKeyPair).challenge(challengeBytes()).build();

      assertDoesNotThrow(() -> verify(attestation));
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordsWhatTheVerificationEstablished() throws Exception {
      String attestation = fixture.attestation(instanceKeyPair).challenge(challengeBytes()).build();

      Map<String, Object> stored = verify(attestation).toMap(LocalDateTime.now());

      assertEquals("ios-app-attest", stored.get("platform"));
      Map<String, Object> app = (Map<String, Object>) stored.get("app");
      assertEquals(IosAppAttestFixture.APP_ID, app.get("app_id"));
      assertEquals("production", app.get("environment"));
      List<Map<String, Object>> certificates =
          (List<Map<String, Object>>)
              ((Map<String, Object>) stored.get("chain")).get("certificates");
      assertFalse(certificates.isEmpty());
      certificates.forEach(certificate -> assertNotNull(certificate.get("serial")));
    }

    @Test
    void rejectsAnAttestationProducedForAnotherChallenge() throws Exception {
      String attestation =
          fixture.attestation(instanceKeyPair).challenge("another-challenge".getBytes()).build();

      assertRejected(
          attestation, "the attestation nonce does not match the registration challenge");
    }

    @Test
    void rejectsAnAttestationOfAKeyOtherThanTheOneBeingRegistered() throws Exception {
      // The attestation is valid, but it covers a key the registrant did not present. Accepting it
      // would register a key no hardware ever vouched for.
      KeyPair otherKey = IosAppAttestFixture.generateKeyPair();
      String attestation = fixture.attestation(otherKey).challenge(challengeBytes()).build();

      assertRejected(attestation, "does not certify client_instance_public_key");
    }

    @Test
    void rejectsAnAttestationFromAnotherApplication() throws Exception {
      String attestation =
          fixture
              .attestation(instanceKeyPair)
              .challenge(challengeBytes())
              .appId("ABCDE12345.com.attacker.app")
              .build();

      assertRejected(attestation, "the attested App ID is not configured for this client");
    }

    @Test
    void rejectsAuthenticatorDataThatDescribesAnotherKey() throws Exception {
      // credentialId is the key identifier of the certified key. A mismatch means the authenticator
      // data and the certificate describe different keys.
      String attestation =
          fixture
              .attestation(instanceKeyPair)
              .challenge(challengeBytes())
              .credentialId(IosAppAttestFixture.sha256("not-the-key".getBytes()))
              .build();

      assertRejected(attestation, "credentialId is not the key identifier of the certified key");
    }
  }

  /** What the app embeds as clientDataHash is the client's challenge_binding. */
  @Nested
  class ChallengeBinding {

    private PlatformAttestationEvidence verifyWith(String binding, String attestationObject)
        throws Exception {
      return verifier.verify(
          StubVerificationRequest.of(
              Map.of(
                  "client_instance_platform_config",
                  Map.of(
                      "ios_app_attest",
                      Map.of(
                          "app_ids", List.of(IosAppAttestFixture.APP_ID),
                          "override_root_certificates", List.of(fixture.rootBase64()),
                          "challenge_binding", binding))),
              CHALLENGE,
              instanceKeyAsJwk(),
              evidence(attestationObject)));
    }

    private byte[] clientDataHashFor(PlatformChallengeBinding binding) throws Exception {
      return binding.clientDataHash(CHALLENGE, instanceKeyAsJwk());
    }

    @Test
    void acceptsRequestHashAsClientDataHashWhenConfigured() throws Exception {
      String attestation =
          fixture
              .attestation(instanceKeyPair)
              .clientDataHash(clientDataHashFor(PlatformChallengeBinding.request_hash))
              .build();

      assertDoesNotThrow(() -> verifyWith("request_hash", attestation));
    }

    @Test
    void rejectsRequestHashUnderTheDefault() throws Exception {
      // The app hashed something other than what the client is configured for: a nonce mismatch,
      // which is all the app would ever see.
      String attestation =
          fixture
              .attestation(instanceKeyPair)
              .clientDataHash(clientDataHashFor(PlatformChallengeBinding.request_hash))
              .build();

      assertRejected(attestation, "nonce does not match");
    }

    @Test
    void acceptsTheHashOfTheChallengeTextWhenConfigured() throws Exception {
      String attestation =
          fixture
              .attestation(instanceKeyPair)
              .clientDataHash(clientDataHashFor(PlatformChallengeBinding.challenge_text))
              .build();

      assertDoesNotThrow(() -> verifyWith("challenge_text", attestation));
    }
  }

  @Nested
  class ChainTrust {

    @Test
    void rejectsAChainThatDoesNotLeadToTheConfiguredRoot() throws Exception {
      // Every binding holds here: the attacker wrote the certificate extension themselves. Only the
      // root check separates this from genuine evidence.
      String attestation =
          fixture
              .attestation(instanceKeyPair)
              .challenge(challengeBytes())
              .signedByUntrustedRoot()
              .build();

      assertRejected(attestation, "does not lead to a trusted root");
    }

    @Test
    void rejectsAnAttestationInAnotherFormat() throws Exception {
      String attestation =
          fixture.attestation(instanceKeyPair).challenge(challengeBytes()).format("packed").build();

      assertRejected(attestation, "fmt is not apple-appattest");
    }
  }

  @Nested
  class AuthenticatorData {

    @Test
    void rejectsAKeyThatHasAlreadySignedAnAssertion() throws Exception {
      String attestation =
          fixture.attestation(instanceKeyPair).challenge(challengeBytes()).counter(1).build();

      assertRejected(attestation, "counter is not 0 at attestation");
    }

    @Test
    void rejectsADevelopmentKeyWhenProductionIsConfigured() throws Exception {
      String attestation =
          fixture
              .attestation(instanceKeyPair)
              .challenge(challengeBytes())
              .environment(IosAppAttestEnvironment.development)
              .build();

      assertRejected(attestation, "was not generated in the configured environment");
    }

    @Test
    void acceptsADevelopmentKeyWhenDevelopmentIsConfigured() throws Exception {
      String attestation =
          fixture
              .attestation(instanceKeyPair)
              .challenge(challengeBytes())
              .environment(IosAppAttestEnvironment.development)
              .build();

      assertDoesNotThrow(
          () ->
              verifier.verify(
                  StubVerificationRequest.of(
                      clientPlatformConfig(fixture.rootBase64(), "development"),
                      CHALLENGE,
                      instanceKeyAsJwk(),
                      evidence(attestation))));
    }
  }

  @Nested
  class Configuration {

    @Test
    void rejectsAClientWithNoAppAttestConfiguration() throws Exception {
      String attestation = fixture.attestation(instanceKeyPair).challenge(challengeBytes()).build();

      assertThrows(
          PlatformAttestationVerificationException.class,
          () ->
              verifier.verify(
                  StubVerificationRequest.of(
                      Map.of("client_instance_platform_config", Map.of()),
                      CHALLENGE,
                      instanceKeyAsJwk(),
                      evidence(attestation))));
    }

    @Test
    void rejectsAClientWithNoAppIds() throws Exception {
      // Apple signs an attestation for any app on the device. Without an App ID to check, any
      // App Attest capable app would satisfy the remaining checks.
      String attestation = fixture.attestation(instanceKeyPair).challenge(challengeBytes()).build();

      assertThrows(
          PlatformAttestationVerificationException.class,
          () ->
              verifier.verify(
                  StubVerificationRequest.of(
                      Map.of(
                          "client_instance_platform_config",
                          Map.of("ios_app_attest", Map.of("environment", "production"))),
                      CHALLENGE,
                      instanceKeyAsJwk(),
                      evidence(attestation))));
    }

    @Test
    void rejectsEvidenceWithNoAttestationObject() {
      assertThrows(
          PlatformAttestationVerificationException.class,
          () ->
              verifier.verify(
                  StubVerificationRequest.of(
                      clientPlatformConfig(fixture.rootBase64()),
                      CHALLENGE,
                      instanceKeyAsJwk(),
                      Map.of("platform", IosAppAttestVerifier.PLATFORM))));
    }

    @Test
    void rejectsEvidenceThatIsNotCbor() {
      assertThrows(
          PlatformAttestationVerificationException.class,
          () ->
              verifier.verify(
                  StubVerificationRequest.of(
                      clientPlatformConfig(fixture.rootBase64()),
                      CHALLENGE,
                      instanceKeyAsJwk(),
                      evidence(Base64.getEncoder().encodeToString("not cbor at all".getBytes())))));
    }
  }
}

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

package org.idp.server.core.openid.extension.attestation.android;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.jwk.ECKey;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationVerificationException;
import org.idp.server.core.openid.extension.attestation.StubVerificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Android key attestation at Client Instance registration (#1521).
 *
 * <p>The registration endpoint is unauthenticated, so this verification is the authentication of
 * the request. Each test removes exactly one of the bindings the contract requires and asserts that
 * registration stops — a verifier that accepts evidence it cannot tie to this challenge, this key
 * and this application would let anyone register a key and then authenticate as the client.
 */
class AndroidKeyAttestationVerifierTest {

  static final String CHALLENGE = "Zm9vYmFyLWNoYWxsZW5nZS0wMQ";
  static final byte[] SIGNING_DIGEST = "signing-certificate-digest-0001".getBytes();

  AndroidAttestationFixture fixture;
  AndroidKeyAttestationVerifier verifier;
  KeyPair instanceKeyPair;

  @BeforeEach
  void setUp() throws Exception {
    fixture = new AndroidAttestationFixture();
    verifier = new AndroidKeyAttestationVerifier();
    instanceKeyPair = AndroidAttestationFixture.generateKeyPair();
  }

  private Map<String, Object> instanceKeyAsJwk() throws Exception {
    ECKey ecKey =
        new ECKey.Builder(
                com.nimbusds.jose.jwk.Curve.P_256, (ECPublicKey) instanceKeyPair.getPublic())
            .build();
    return ecKey.toPublicJWK().toJSONObject();
  }

  private Map<String, Object> evidence(List<String> chain) {
    return Map.of("platform", AndroidKeyAttestationVerifier.PLATFORM, "x5c", chain);
  }

  private Map<String, Object> clientPlatformConfig(String rootBase64) {
    return Map.of(
        "client_instance_platform_config",
        Map.of(
            "android_key_attestation",
            Map.of(
                "package_names", List.of(AndroidAttestationFixture.PACKAGE_NAME),
                "signature_digests",
                    List.of(Base64.getUrlEncoder().withoutPadding().encodeToString(SIGNING_DIGEST)),
                "min_security_level", "trusted_environment",
                "trusted_root_certificates", List.of(rootBase64))));
  }

  private List<String> validChain() throws Exception {
    return fixture.chain(
        instanceKeyPair,
        Base64.getUrlDecoder().decode(CHALLENGE),
        AndroidKeyAttestationSecurityLevel.trusted_environment,
        AndroidAttestationFixture.PACKAGE_NAME,
        List.of(SIGNING_DIGEST));
  }

  @Nested
  class Bindings {

    @Test
    void acceptsEvidenceThatSatisfiesAllThreeBindings() throws Exception {
      List<String> chain = validChain();

      assertDoesNotThrow(
          () ->
              verifier.verify(
                  StubVerificationRequest.of(
                      clientPlatformConfig(fixture.rootBase64()),
                      CHALLENGE,
                      instanceKeyAsJwk(),
                      evidence(chain))));
    }

    @Test
    void rejectsEvidenceProducedForAnotherChallenge() throws Exception {
      List<String> chain =
          fixture.chain(
              instanceKeyPair,
              "another-challenge".getBytes(),
              AndroidKeyAttestationSecurityLevel.trusted_environment,
              AndroidAttestationFixture.PACKAGE_NAME,
              List.of(SIGNING_DIGEST));

      PlatformAttestationVerificationException exception =
          assertThrows(
              PlatformAttestationVerificationException.class,
              () ->
                  verifier.verify(
                      StubVerificationRequest.of(
                          clientPlatformConfig(fixture.rootBase64()),
                          CHALLENGE,
                          instanceKeyAsJwk(),
                          evidence(chain))));

      assertTrue(exception.getMessage().contains("attestationChallenge"));
    }

    @Test
    void rejectsEvidenceThatCertifiesAnotherKey() throws Exception {
      // A captured attestation paired with a key the attacker holds.
      KeyPair attackerKey = AndroidAttestationFixture.generateKeyPair();
      List<String> chain =
          fixture.chain(
              attackerKey,
              Base64.getUrlDecoder().decode(CHALLENGE),
              AndroidKeyAttestationSecurityLevel.trusted_environment,
              AndroidAttestationFixture.PACKAGE_NAME,
              List.of(SIGNING_DIGEST));

      PlatformAttestationVerificationException exception =
          assertThrows(
              PlatformAttestationVerificationException.class,
              () ->
                  verifier.verify(
                      StubVerificationRequest.of(
                          clientPlatformConfig(fixture.rootBase64()),
                          CHALLENGE,
                          instanceKeyAsJwk(),
                          evidence(chain))));

      assertTrue(exception.getMessage().contains("client_instance_public_key"));
    }

    @Test
    void rejectsAnotherApplication() throws Exception {
      List<String> chain =
          fixture.chain(
              instanceKeyPair,
              Base64.getUrlDecoder().decode(CHALLENGE),
              AndroidKeyAttestationSecurityLevel.trusted_environment,
              "com.attacker.app",
              List.of(SIGNING_DIGEST));

      PlatformAttestationVerificationException exception =
          assertThrows(
              PlatformAttestationVerificationException.class,
              () ->
                  verifier.verify(
                      StubVerificationRequest.of(
                          clientPlatformConfig(fixture.rootBase64()),
                          CHALLENGE,
                          instanceKeyAsJwk(),
                          evidence(chain))));

      assertTrue(exception.getMessage().contains("package"));
    }

    @Test
    void rejectsAnotherSigningCertificate() throws Exception {
      // Same package name, re-signed by the attacker. The package name is not a secret, so the
      // digests are what separate the real app from a repackaged one.
      List<String> chain =
          fixture.chain(
              instanceKeyPair,
              Base64.getUrlDecoder().decode(CHALLENGE),
              AndroidKeyAttestationSecurityLevel.trusted_environment,
              AndroidAttestationFixture.PACKAGE_NAME,
              List.of("attacker-signing-digest".getBytes()));

      PlatformAttestationVerificationException exception =
          assertThrows(
              PlatformAttestationVerificationException.class,
              () ->
                  verifier.verify(
                      StubVerificationRequest.of(
                          clientPlatformConfig(fixture.rootBase64()),
                          CHALLENGE,
                          instanceKeyAsJwk(),
                          evidence(chain))));

      assertTrue(exception.getMessage().contains("signing certificate digests"));
    }
  }

  @Nested
  class ChainTrust {

    @Test
    void rejectsAChainThatDoesNotLeadToTheConfiguredRoot() throws Exception {
      // The whole point of pinning: this chain is internally consistent and every binding holds,
      // because the attacker wrote the extension themselves.
      AndroidAttestationFixture attackerFixture = new AndroidAttestationFixture();
      List<String> chain =
          attackerFixture.chain(
              instanceKeyPair,
              Base64.getUrlDecoder().decode(CHALLENGE),
              AndroidKeyAttestationSecurityLevel.trusted_environment,
              AndroidAttestationFixture.PACKAGE_NAME,
              List.of(SIGNING_DIGEST));

      PlatformAttestationVerificationException exception =
          assertThrows(
              PlatformAttestationVerificationException.class,
              () ->
                  verifier.verify(
                      StubVerificationRequest.of(
                          clientPlatformConfig(fixture.rootBase64()),
                          CHALLENGE,
                          instanceKeyAsJwk(),
                          evidence(chain))));

      assertTrue(exception.getMessage().contains("trusted root"));
    }

    @Test
    void fallsBackToTheShippedGoogleRootsWhenNoneIsConfigured() throws Exception {
      // Without an override the shipped Google roots apply, and a chain built for this test does
      // not lead to one of them. The registration is rejected for that reason rather than for the
      // absence of configuration.
      Map<String, Object> withoutRoot =
          Map.of(
              "client_instance_platform_config",
              Map.of(
                  "android_key_attestation",
                  Map.of(
                      "package_names", List.of(AndroidAttestationFixture.PACKAGE_NAME),
                      "signature_digests",
                          List.of(
                              Base64.getUrlEncoder()
                                  .withoutPadding()
                                  .encodeToString(SIGNING_DIGEST)))));

      PlatformAttestationVerificationException exception =
          assertThrows(
              PlatformAttestationVerificationException.class,
              () ->
                  verifier.verify(
                      StubVerificationRequest.of(
                          withoutRoot, CHALLENGE, instanceKeyAsJwk(), evidence(validChain()))));

      assertTrue(exception.getMessage().contains("trusted root"));
    }
  }

  @Nested
  class SecurityLevel {

    @Test
    void rejectsASoftwareBackedKey() throws Exception {
      // A key the OS holds can be exported from a compromised device, so accepting it gains
      // nothing over having no attestation.
      List<String> chain =
          fixture.chain(
              instanceKeyPair,
              Base64.getUrlDecoder().decode(CHALLENGE),
              AndroidKeyAttestationSecurityLevel.software,
              AndroidAttestationFixture.PACKAGE_NAME,
              List.of(SIGNING_DIGEST));

      PlatformAttestationVerificationException exception =
          assertThrows(
              PlatformAttestationVerificationException.class,
              () ->
                  verifier.verify(
                      StubVerificationRequest.of(
                          clientPlatformConfig(fixture.rootBase64()),
                          CHALLENGE,
                          instanceKeyAsJwk(),
                          evidence(chain))));

      assertTrue(exception.getMessage().contains("security level"));
    }

    @Test
    void acceptsStrongBoxWhenTrustedEnvironmentIsTheMinimum() throws Exception {
      List<String> chain =
          fixture.chain(
              instanceKeyPair,
              Base64.getUrlDecoder().decode(CHALLENGE),
              AndroidKeyAttestationSecurityLevel.strong_box,
              AndroidAttestationFixture.PACKAGE_NAME,
              List.of(SIGNING_DIGEST));

      assertDoesNotThrow(
          () ->
              verifier.verify(
                  StubVerificationRequest.of(
                      clientPlatformConfig(fixture.rootBase64()),
                      CHALLENGE,
                      instanceKeyAsJwk(),
                      evidence(chain))));
    }
  }

  /**
   * The AOSP schema defines SecurityLevel as ENUMERATED, which has a different DER tag from
   * INTEGER. Reading it as INTEGER passes every test built the same way and then fails on the first
   * chain from a device, so the encoding is pinned rather than accommodated.
   */
  @Nested
  class SecurityLevelEncoding {

    @Test
    void readsTheEnumeratedEncodingADeviceProduces() throws Exception {
      List<String> chain =
          fixture.chain(
              instanceKeyPair,
              Base64.getUrlDecoder().decode(CHALLENGE),
              AndroidKeyAttestationSecurityLevel.trusted_environment,
              AndroidAttestationFixture.PACKAGE_NAME,
              List.of(SIGNING_DIGEST),
              false);

      assertDoesNotThrow(
          () ->
              verifier.verify(
                  StubVerificationRequest.of(
                      clientPlatformConfig(fixture.rootBase64()),
                      CHALLENGE,
                      instanceKeyAsJwk(),
                      evidence(chain))));
    }

    @Test
    void rejectsTheIntegerEncodingTheSchemaDoesNotDefine() throws Exception {
      // Being lenient here would gain nothing — a producer that reaches this verifier had to be
      // signed by the pinned root — and would let a fixture with the wrong tag pass unnoticed.
      List<String> chain =
          fixture.chain(
              instanceKeyPair,
              Base64.getUrlDecoder().decode(CHALLENGE),
              AndroidKeyAttestationSecurityLevel.trusted_environment,
              AndroidAttestationFixture.PACKAGE_NAME,
              List.of(SIGNING_DIGEST),
              true);

      PlatformAttestationVerificationException exception =
          assertThrows(
              PlatformAttestationVerificationException.class,
              () ->
                  verifier.verify(
                      StubVerificationRequest.of(
                          clientPlatformConfig(fixture.rootBase64()),
                          CHALLENGE,
                          instanceKeyAsJwk(),
                          evidence(chain))));

      assertTrue(exception.getMessage().contains("enumerated"));
    }
  }

  @Nested
  class Configuration {

    @Test
    void rejectsAClientWithoutPlatformConfiguration() throws Exception {
      assertThrows(
          RuntimeException.class,
          () ->
              verifier.verify(
                  StubVerificationRequest.of(
                      Map.of(), CHALLENGE, instanceKeyAsJwk(), evidence(validChain()))));
    }

    @Test
    void rejectsAConfigurationWithoutSignatureDigests() throws Exception {
      Map<String, Object> withoutDigests =
          Map.of(
              "client_instance_platform_config",
              Map.of(
                  "android_key_attestation",
                  Map.of("package_names", List.of(AndroidAttestationFixture.PACKAGE_NAME))));

      assertThrows(
          RuntimeException.class,
          () ->
              verifier.verify(
                  StubVerificationRequest.of(
                      withoutDigests, CHALLENGE, instanceKeyAsJwk(), evidence(validChain()))));
    }
  }

  static String digestOf(byte[] value) throws Exception {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(value));
  }
}

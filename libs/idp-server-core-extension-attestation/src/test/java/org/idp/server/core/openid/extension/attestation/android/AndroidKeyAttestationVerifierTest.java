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
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationEvidence;
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationVerificationException;
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationVerificationRequest;
import org.idp.server.core.openid.extension.attestation.PlatformChallengeBinding;
import org.idp.server.core.openid.extension.attestation.StubVerificationRequest;
import org.idp.server.platform.x509.X509CertificateChain;
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
                "override_root_certificates", List.of(rootBase64))));
  }

  private List<String> validChain() throws Exception {
    return fixture.chain(
        instanceKeyPair,
        Base64.getUrlDecoder().decode(CHALLENGE),
        AndroidKeyAttestationSecurityLevel.trusted_environment,
        AndroidAttestationFixture.PACKAGE_NAME,
        List.of(SIGNING_DIGEST));
  }

  /** {@link #validChain()} with the hardware list saying something else about the key. */
  private List<String> chainWith(AndroidAttestationFixture.KeyProperties properties)
      throws Exception {
    return fixture.chain(
        instanceKeyPair,
        Base64.getUrlDecoder().decode(CHALLENGE),
        AndroidKeyAttestationSecurityLevel.trusted_environment,
        AndroidAttestationFixture.PACKAGE_NAME,
        List.of(SIGNING_DIGEST),
        properties);
  }

  private PlatformAttestationVerificationRequest requestFor(List<String> chain) throws Exception {
    return StubVerificationRequest.of(
        clientPlatformConfig(fixture.rootBase64()), CHALLENGE, instanceKeyAsJwk(), evidence(chain));
  }

  /** {@link #requestFor} with more settings in the client's android_key_attestation. */
  @SuppressWarnings("unchecked")
  private PlatformAttestationVerificationRequest requestFor(
      List<String> chain, Map<String, Object> additionalSettings) throws Exception {
    Map<String, Object> android =
        new java.util.HashMap<>(
            (Map<String, Object>)
                ((Map<String, Object>)
                        clientPlatformConfig(fixture.rootBase64())
                            .get("client_instance_platform_config"))
                    .get("android_key_attestation"));
    android.putAll(additionalSettings);
    return StubVerificationRequest.of(
        Map.of("client_instance_platform_config", Map.of("android_key_attestation", android)),
        CHALLENGE,
        instanceKeyAsJwk(),
        evidence(chain));
  }

  private AndroidAttestationFixture.KeyProperties deviceProperties() {
    return AndroidAttestationFixture.KeyProperties.ofDevice(
        AndroidKeyAttestationSecurityLevel.trusted_environment);
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
    @SuppressWarnings("unchecked")
    void recordsWhatTheVerificationEstablished() throws Exception {
      List<String> chain = validChain();

      PlatformAttestationEvidence evidence =
          verifier.verify(
              StubVerificationRequest.of(
                  clientPlatformConfig(fixture.rootBase64()),
                  CHALLENGE,
                  instanceKeyAsJwk(),
                  evidence(chain)));
      Map<String, Object> stored = evidence.toMap(LocalDateTime.now());

      assertEquals("android-key-attestation", stored.get("platform"));
      Map<String, Object> key = (Map<String, Object>) stored.get("key");
      assertEquals("trusted_environment", key.get("attestation_security_level"));
      assertEquals("trusted_environment", key.get("keymint_security_level"));
      assertEquals("generated", key.get("origin"));
      Map<String, Object> app = (Map<String, Object>) stored.get("app");
      assertEquals(List.of(AndroidAttestationFixture.PACKAGE_NAME), app.get("package_names"));
      Map<String, Object> device = (Map<String, Object>) stored.get("device");
      assertEquals("verified", device.get("verified_boot_state"));
      assertEquals(true, device.get("device_locked"));
      assertEquals(202409, device.get("os_patch_level"));

      // Every certificate of the presented chain, by the serial a revocation list is keyed on.
      List<Map<String, Object>> certificates =
          (List<Map<String, Object>>)
              ((Map<String, Object>) stored.get("chain")).get("certificates");
      List<java.security.cert.X509Certificate> presented =
          X509CertificateChain.parse(chain).certificates();
      assertEquals(presented.size(), certificates.size());
      for (int i = 0; i < presented.size(); i++) {
        assertEquals(
            presented.get(i).getSerialNumber().toString(16), certificates.get(i).get("serial"));
        assertNotNull(certificates.get(i).get("not_after"));
        assertNotNull(certificates.get(i).get("sha256"));
      }
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

  /** What the app embeds as attestationChallenge is the client's challenge_binding. */
  @Nested
  class ChallengeBinding {

    private List<String> chainEmbedding(PlatformChallengeBinding binding) throws Exception {
      return fixture.chain(
          instanceKeyPair,
          binding.challengeBytes(CHALLENGE, instanceKeyAsJwk()),
          AndroidKeyAttestationSecurityLevel.trusted_environment,
          AndroidAttestationFixture.PACKAGE_NAME,
          List.of(SIGNING_DIGEST));
    }

    @Test
    void acceptsRequestHashWhenConfigured() throws Exception {
      PlatformAttestationVerificationRequest request =
          requestFor(
              chainEmbedding(PlatformChallengeBinding.request_hash),
              Map.of("challenge_binding", "request_hash"));

      assertDoesNotThrow(() -> verifier.verify(request));
    }

    @Test
    void acceptsTheChallengeTextWhenConfigured() throws Exception {
      PlatformAttestationVerificationRequest request =
          requestFor(
              chainEmbedding(PlatformChallengeBinding.challenge_text),
              Map.of("challenge_binding", "challenge_text"));

      assertDoesNotThrow(() -> verifier.verify(request));
    }

    @Test
    void rejectsTheChallengeTextUnderTheDefault() throws Exception {
      PlatformAttestationVerificationRequest request =
          requestFor(chainEmbedding(PlatformChallengeBinding.challenge_text));

      PlatformAttestationVerificationException exception =
          assertThrows(
              PlatformAttestationVerificationException.class, () -> verifier.verify(request));
      assertTrue(exception.getMessage().contains("attestationChallenge does not match"));
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
   * What the hardware list says about the key itself (#1521 review).
   *
   * <p>These are not bindings: the evidence here is about this challenge, this key and this
   * application, and is still refused. Each case is one where the chain verifies and the premise
   * registering a Client Instance rests on does not hold.
   *
   * <p>Every value is read from {@code hardwareEnforced}. The same tags may appear in {@code
   * softwareEnforced}, where they are the platform's word about a property only KeyMint can know,
   * so the last test states that reading them there decides nothing.
   */
  @Nested
  class KeyProperties {

    @Test
    void rejectsAnImportedKey() throws Exception {
      // The key lives in the TEE, certifies the registered public key and names the right app —
      // and a copy of the private key exists wherever it was generated, so possession says nothing
      // about which device is calling.
      PlatformAttestationVerificationRequest request =
          requestFor(chainWith(deviceProperties().withOrigin(AndroidKeyOrigin.imported)));

      PlatformAttestationVerificationException exception =
          assertThrows(
              PlatformAttestationVerificationException.class, () -> verifier.verify(request));

      assertTrue(exception.getMessage().contains("not generated in secure hardware"));
    }

    @Test
    void rejectsASecurelyImportedKey() throws Exception {
      // Secure import means the plaintext never appeared on this device. The system that wrapped
      // it held the plaintext by definition, so the premise still does not hold.
      PlatformAttestationVerificationRequest request =
          requestFor(chainWith(deviceProperties().withOrigin(AndroidKeyOrigin.securely_imported)));

      PlatformAttestationVerificationException exception =
          assertThrows(
              PlatformAttestationVerificationException.class, () -> verifier.verify(request));

      assertTrue(exception.getMessage().contains("not generated in secure hardware"));
    }

    @Test
    void rejectsAKeyWhoseOriginTheDeviceDidNotReport() throws Exception {
      // The field is what the check reads. An absent one is not evidence that the key was
      // generated in place.
      PlatformAttestationVerificationRequest request =
          requestFor(chainWith(deviceProperties().withOrigin(AndroidKeyOrigin.undefined)));

      PlatformAttestationVerificationException exception =
          assertThrows(
              PlatformAttestationVerificationException.class, () -> verifier.verify(request));

      assertTrue(exception.getMessage().contains("undefined"));
    }

    @Test
    void rejectsAKeyKeyMintWillNotSignWith() throws Exception {
      // A Client Instance key exists to sign PoP JWTs. Registering one that cannot would fail at
      // first use instead, on an endpoint that cannot say why.
      PlatformAttestationVerificationRequest request =
          requestFor(
              chainWith(
                  deviceProperties()
                      .withPurposes(
                          List.of(AndroidKeyPurpose.encrypt, AndroidKeyPurpose.decrypt))));

      PlatformAttestationVerificationException exception =
          assertThrows(
              PlatformAttestationVerificationException.class, () -> verifier.verify(request));

      assertTrue(exception.getMessage().contains("not authorized to sign"));
    }

    @Test
    void rejectsAKeyHeldInSoftwareWhoseAttestationWasProducedInHardware() throws Exception {
      // attestationSecurityLevel and keyMintSecurityLevel describe different subjects. Reading
      // only the first accepts a software key whose attestation the TEE happened to sign — and
      // the hardware list the checks above read would mean nothing on such a key.
      PlatformAttestationVerificationRequest request =
          requestFor(
              chainWith(
                  deviceProperties()
                      .withKeyMintSecurityLevel(AndroidKeyAttestationSecurityLevel.software)));

      PlatformAttestationVerificationException exception =
          assertThrows(
              PlatformAttestationVerificationException.class, () -> verifier.verify(request));

      assertTrue(exception.getMessage().contains("keyMint security level"));
    }

    @Test
    void doesNotAcceptTheSoftwareListAsTheKeysOwnProperties() throws Exception {
      // Everything a device would report, moved to the list the platform writes. The platform is
      // not in a position to know either value, so the chain is refused exactly as one that
      // reported them nowhere.
      PlatformAttestationVerificationRequest request =
          requestFor(chainWith(deviceProperties().movedToSoftwareList()));

      PlatformAttestationVerificationException exception =
          assertThrows(
              PlatformAttestationVerificationException.class, () -> verifier.verify(request));

      assertTrue(exception.getMessage().contains("not generated in secure hardware"));
    }

    @Test
    void acceptsWhatADeviceGeneratingASigningKeyReports() throws Exception {
      PlatformAttestationVerificationRequest request = requestFor(chainWith(deviceProperties()));

      assertDoesNotThrow(() -> verifier.verify(request));
    }
  }

  /**
   * The AOSP schema defines SecurityLevel as ENUMERATED, which has a different DER tag from
   * INTEGER. Reading it as INTEGER passes every test built the same way and then fails on the first
   * chain from a device, so the encoding is pinned rather than accommodated.
   */
  /**
   * The boot state decides whether the platform's statements, the application identity among them,
   * can be believed: a modified OS names any application it likes.
   */
  @Nested
  class Boot {

    private void assertRejected(PlatformAttestationVerificationRequest request, String reason) {
      PlatformAttestationVerificationException exception =
          assertThrows(
              PlatformAttestationVerificationException.class, () -> verifier.verify(request));
      assertTrue(exception.getMessage().contains(reason), exception.getMessage());
    }

    @Test
    void rejectsADeviceWhoseBootloaderIsUnlocked() throws Exception {
      // What an unlocked device reports: nothing is verified, and the bootloader loads any OS.
      assertRejected(
          requestFor(
              chainWith(deviceProperties().withBoot(AndroidVerifiedBootState.unverified, false))),
          "verified boot state unverified is not accepted");
    }

    @Test
    void rejectsAVerifiedBootWithTheBootloaderUnlocked() throws Exception {
      assertRejected(
          requestFor(
              chainWith(deviceProperties().withBoot(AndroidVerifiedBootState.verified, false))),
          "bootloader is unlocked");
    }

    @Test
    void rejectsAnOsVerifiedAgainstAnOwnerInstalledKeyByDefault() throws Exception {
      assertRejected(
          requestFor(
              chainWith(deviceProperties().withBoot(AndroidVerifiedBootState.self_signed, true))),
          "verified boot state self_signed is not accepted");
    }

    @Test
    void acceptsAnOsVerifiedAgainstAnOwnerInstalledKeyWhenConfigured() throws Exception {
      PlatformAttestationVerificationRequest request =
          requestFor(
              chainWith(deviceProperties().withBoot(AndroidVerifiedBootState.self_signed, true)),
              Map.of("verified_boot_states", List.of("verified", "self_signed")));

      assertDoesNotThrow(() -> verifier.verify(request));
    }

    @Test
    void acceptsAnUnlockedDeviceOnlyWhenBothSettingsAreRelaxed() throws Exception {
      // A development device: the operator relaxes both the state and the lock, deliberately.
      PlatformAttestationVerificationRequest request =
          requestFor(
              chainWith(deviceProperties().withBoot(AndroidVerifiedBootState.unverified, false)),
              Map.of(
                  "verified_boot_states",
                  List.of("verified", "unverified"),
                  "require_device_locked",
                  false));

      assertDoesNotThrow(() -> verifier.verify(request));
    }

    @Test
    void rejectsADeviceThatDoesNotReportItsRootOfTrust() throws Exception {
      assertRejected(
          requestFor(
              chainWith(deviceProperties().withBoot(AndroidVerifiedBootState.undefined, false))),
          "does not report the device's root of trust");
    }

    @Test
    void refusesAConfigurationThatAcceptsAFailedBoot() throws Exception {
      PlatformAttestationVerificationRequest request =
          requestFor(validChain(), Map.of("verified_boot_states", List.of("verified", "failed")));

      assertThrows(RuntimeException.class, () -> verifier.verify(request));
    }
  }

  @Nested
  class OsPatchLevel {

    @Test
    void setsNoMinimumByDefault() throws Exception {
      PlatformAttestationVerificationRequest request =
          requestFor(chainWith(deviceProperties().withOsPatchLevel(0)));

      assertDoesNotThrow(() -> verifier.verify(request));
    }

    @Test
    void rejectsAPatchLevelOlderThanTheMinimum() throws Exception {
      PlatformAttestationVerificationRequest request =
          requestFor(
              chainWith(deviceProperties().withOsPatchLevel(202401)),
              Map.of("min_os_patch_level", 202406));

      PlatformAttestationVerificationException exception =
          assertThrows(
              PlatformAttestationVerificationException.class, () -> verifier.verify(request));
      assertTrue(exception.getMessage().contains("older than the configured minimum"));
    }

    @Test
    void acceptsThePatchLevelTheMinimumNames() throws Exception {
      PlatformAttestationVerificationRequest request =
          requestFor(
              chainWith(deviceProperties().withOsPatchLevel(202406)),
              Map.of("min_os_patch_level", 202406));

      assertDoesNotThrow(() -> verifier.verify(request));
    }

    @Test
    void rejectsADeviceThatDoesNotReportThePatchLevelWhenAMinimumIsSet() throws Exception {
      PlatformAttestationVerificationRequest request =
          requestFor(
              chainWith(deviceProperties().withOsPatchLevel(0)),
              Map.of("min_os_patch_level", 202406));

      PlatformAttestationVerificationException exception =
          assertThrows(
              PlatformAttestationVerificationException.class, () -> verifier.verify(request));
      assertTrue(exception.getMessage().contains("does not report the OS patch level"));
    }
  }

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

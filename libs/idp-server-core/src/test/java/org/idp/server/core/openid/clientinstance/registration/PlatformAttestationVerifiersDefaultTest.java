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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.idp.server.core.openid.plugin.clientinstance.PlatformAttestationVerifierPluginLoader;
import org.junit.jupiter.api.Test;

/**
 * What registration accepts when no platform verifier is installed.
 *
 * <p>The registration endpoint is authenticated by the platform verification, so the default has to
 * be the closed one: a deployment that ships without a verifier module, or whose service file is
 * broken, rejects every registration rather than accepting keys it cannot vouch for. The core
 * module carries no verifier of its own, which makes it that deployment.
 */
class PlatformAttestationVerifiersDefaultTest {

  @Test
  void loadsNothingWithoutAVerifierModuleOrTheDevelopmentVariable() {
    assertNull(
        System.getenv("IDP_SERVER_CLIENT_INSTANCE_DEVELOPMENT_VERIFIER"),
        "the test environment sets the bypass variable, so this test cannot check the default");

    List<PlatformAttestationVerifier> loaded =
        PlatformAttestationVerifierPluginLoader.load(
            new ClientInstanceRegistrationDependencyContainer());

    assertTrue(loaded.isEmpty(), "expected no verifier in core: " + loaded);
  }

  @Test
  void rejectsEveryPlatformWhenNoVerifierIsLoaded() {
    PlatformAttestationVerifiers verifiers = new PlatformAttestationVerifiers(List.of());

    for (String platform :
        List.of("android-key-attestation", "ios-app-attest", RequestHashBindingVerifier.PLATFORM)) {
      assertThrows(
          PlatformAttestationVerificationException.class,
          () -> verifiers.get(platform),
          "a registration naming " + platform + " was not rejected");
    }
  }

  @Test
  void rejectsTheDevelopmentPlatformUnlessItsVerifierWasAdded() {
    // A request can name the development platform whatever the server runs; naming it must not be
    // enough, only the verifier the environment variable adds.
    PlatformAttestationVerifiers withoutBypass =
        new PlatformAttestationVerifiers(List.of(stub("android-key-attestation")));

    assertThrows(
        PlatformAttestationVerificationException.class,
        () -> withoutBypass.get(RequestHashBindingVerifier.PLATFORM));
  }

  private static PlatformAttestationVerifier stub(String platform) {
    return new PlatformAttestationVerifier() {
      @Override
      public String platform() {
        return platform;
      }

      @Override
      public PlatformAttestationEvidence verify(PlatformAttestationVerificationRequest request) {
        throw new UnsupportedOperationException();
      }
    };
  }
}

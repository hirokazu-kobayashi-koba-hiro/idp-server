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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationDependencyContainer;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationDependencyMissingException;
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationVerifier;
import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationVerifierFactory;
import org.idp.server.core.openid.extension.attestation.android.AndroidKeyAttestationVerifier;
import org.idp.server.core.openid.extension.attestation.ios.IosAppAttestVerifier;
import org.idp.server.core.openid.plugin.clientinstance.PlatformAttestationVerifierPluginLoader;
import org.junit.jupiter.api.Test;

/**
 * The SPI wiring of the platform verifiers (#1521).
 *
 * <p>This is a configuration file and a service registration rather than logic, and nothing else
 * fails when it is wrong: an entry naming a class that no longer exists, or naming the verifier
 * where the factory is now expected, leaves {@code ServiceLoader} returning nothing. Registration
 * then rejects every request — the safe direction, and a silent one. The verifier tests all
 * construct their subject directly, so none of them would notice.
 */
class PlatformAttestationVerifierRegistrationTest {

  @Test
  void loadsBothPlatformsThroughTheFactoryServiceFile() {
    List<PlatformAttestationVerifier> verifiers =
        PlatformAttestationVerifierPluginLoader.load(
            new ClientInstanceRegistrationDependencyContainer());

    List<String> platforms = verifiers.stream().map(PlatformAttestationVerifier::platform).toList();

    assertTrue(
        platforms.contains(AndroidKeyAttestationVerifier.PLATFORM),
        "android verifier was not loaded: " + platforms);
    assertTrue(
        platforms.contains(IosAppAttestVerifier.PLATFORM),
        "ios verifier was not loaded: " + platforms);
  }

  /** Only the platform verifiers are registered: there is no bypass to pull in. */
  @Test
  void registersOnlyThePlatformVerifiers() {
    List<PlatformAttestationVerifier> verifiers =
        PlatformAttestationVerifierPluginLoader.load(
            new ClientInstanceRegistrationDependencyContainer());

    assertEquals(
        2,
        verifiers.size(),
        "expected only the two platform verifiers: "
            + verifiers.stream().map(PlatformAttestationVerifier::platform).toList());
  }

  /**
   * A missing dependency surfaces where the application assembles its verifiers, not as a rejected
   * registration. The empty container above works only because neither factory asks for anything
   * yet; this pins what happens once one does.
   */
  @Test
  void reportsAMissingDependencyAtAssemblyTime() {
    PlatformAttestationVerifierFactory needsSomething =
        container -> {
          container.resolve(String.class);
          return null;
        };

    assertThrows(
        ClientInstanceRegistrationDependencyMissingException.class,
        () -> needsSomething.create(new ClientInstanceRegistrationDependencyContainer()));
  }
}

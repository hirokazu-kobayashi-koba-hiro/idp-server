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

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Apple App Attestation root shipped with this module.
 *
 * <p>The digest is repeated here rather than read from the resource, so that a change to the
 * shipped certificate fails a test instead of silently widening what the server trusts. Replacing
 * the root is a trust decision, and this test is where that decision has to be restated.
 *
 * <p>Verify independently with:
 *
 * <pre>
 *   curl -sfL https://www.apple.com/certificateauthority/Apple_App_Attestation_Root_CA.pem \
 *     | openssl x509 -outform DER | openssl dgst -sha256
 * </pre>
 */
class AppleAttestationRootsTest {

  /** CN=Apple App Attestation Root CA, serialNumber=0BF3BE0EF1CDD2E0FB8C6E721F621798. */
  static final String ROOT_APPLE_APP_ATTESTATION = "HLmCO6KLpq0tM6AGlB3irk9RPvHU6DG59-D6e2JCyTI";

  @Test
  void shipsTheAppleRoot() {
    List<String> digests = AppleAttestationRoots.digests();

    assertEquals(1, digests.size(), "a root was added or removed without updating this test");
    assertTrue(digests.contains(ROOT_APPLE_APP_ATTESTATION));
  }

  @Test
  void loadsItWithoutConfiguration() {
    // The resource is read from the classpath, so a packaging change that drops it shows up here
    // rather than as every App Attest registration failing at runtime.
    assertFalse(AppleAttestationRoots.certificates().isEmpty());
  }

  @Test
  void shipsARootThatIsStillValid() throws Exception {
    // Apple's root expires in 2045. A shipped certificate that has expired would reject every
    // registration, and verifyToRoot checks the root's own validity window.
    assertDoesNotThrow(() -> AppleAttestationRoots.certificates().get(0).checkValidity());
  }
}

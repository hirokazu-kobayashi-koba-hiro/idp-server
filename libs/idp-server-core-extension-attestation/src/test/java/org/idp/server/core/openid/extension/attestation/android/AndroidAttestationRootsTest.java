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

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The Google attestation roots shipped with this module.
 *
 * <p>The digests are repeated here rather than read from the resources, so that a change to a
 * shipped certificate fails a test instead of silently widening what the server trusts. Replacing a
 * root is a trust decision, and this test is where that decision has to be restated.
 *
 * <p>Verify independently with:
 *
 * <pre>
 *   curl -s https://android.googleapis.com/attestation/root | jq -r '.[]' \
 *     | openssl x509 -outform DER | openssl dgst -sha256
 * </pre>
 */
class AndroidAttestationRootsTest {

  /** Google hardware attestation root, serialNumber=f92009e853b6b045, valid until 2042-03-15. */
  static final String ROOT_F92009E853B6B045 = "ztscttyJauXseXNIvOkoZ1PCs47nHOD740qaEkiADfw";

  /** CN=Key Attestation CA1, O=Google LLC, valid until 2035-07-15. */
  static final String ROOT_KEY_ATTESTATION_CA1 = "bZ20zmxcCykxZtCJhuBXdKh3bOtSXZ5DKVIN4SukvMA";

  @Test
  void shipsTheGoogleRoots() {
    List<String> digests = AndroidAttestationRoots.digests();

    assertEquals(2, digests.size(), "a root was added or removed without updating this test");
    assertTrue(digests.contains(ROOT_F92009E853B6B045));
    assertTrue(digests.contains(ROOT_KEY_ATTESTATION_CA1));
  }

  @Test
  void loadsThemWithoutConfiguration() {
    // The resources are read from the classpath, so a packaging change that drops them shows up
    // here rather than as every Android registration failing at runtime.
    assertFalse(AndroidAttestationRoots.digests().isEmpty());
  }
}

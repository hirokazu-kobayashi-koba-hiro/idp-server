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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of {@link PlatformAttestationVerifier} implementations, keyed by platform.
 *
 * <p>An unknown platform is rejected rather than skipped: the verification is what authenticates
 * the unauthenticated registration endpoint, so falling through would let anyone register a key by
 * naming a platform the server does not implement.
 */
public class PlatformAttestationVerifiers {

  Map<String, PlatformAttestationVerifier> verifiers;

  public PlatformAttestationVerifiers(List<PlatformAttestationVerifier> loaded) {
    this.verifiers = new HashMap<>();
    loaded.forEach(verifier -> verifiers.put(verifier.platform(), verifier));
  }

  public PlatformAttestationVerifier get(String platform) {
    PlatformAttestationVerifier verifier = verifiers.get(platform);

    if (verifier == null) {
      throw new PlatformAttestationVerificationException(
          "no platform attestation verifier is available for platform: " + platform);
    }

    return verifier;
  }
}

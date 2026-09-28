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

/**
 * How the attested key came to exist, as recorded in {@code origin} (tag 702).
 *
 * <p>The security level says where the key <em>is</em>; this says where it <em>came from</em>. They
 * answer different questions, and only both together support the premise Attestation-Based Client
 * Authentication rests on: that the private key has never existed anywhere but this device. A key
 * reported at {@code trusted_environment} may still have been generated elsewhere and imported, in
 * which case a copy exists wherever it was generated and proving possession proves nothing about
 * which device is calling.
 *
 * <p>{@link #securely_imported} is deliberately not treated as equivalent to {@link #generated}.
 * Secure import means the key material never appeared in plaintext <em>on this device</em>; it says
 * nothing about the system that wrapped it, which held the plaintext by definition.
 *
 * @see <a href="https://source.android.com/docs/security/features/keystore/attestation">Key
 *     attestation</a>
 */
public enum AndroidKeyOrigin {
  generated(0),
  derived(1),
  imported(2),
  unknown(3),
  securely_imported(4),
  undefined(-1);

  int value;

  AndroidKeyOrigin(int value) {
    this.value = value;
  }

  public static AndroidKeyOrigin of(int value) {
    for (AndroidKeyOrigin origin : values()) {
      if (origin.value == value) {
        return origin;
      }
    }
    return undefined;
  }

  /** True only for a key the secure hardware created itself. */
  public boolean isGeneratedInSecureHardware() {
    return this == generated;
  }
}

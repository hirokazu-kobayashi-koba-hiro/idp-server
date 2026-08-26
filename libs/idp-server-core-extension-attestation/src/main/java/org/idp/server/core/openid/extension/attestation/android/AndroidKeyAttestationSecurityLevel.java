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
 * Where the attested key lives, as recorded in {@code attestationSecurityLevel}.
 *
 * <p>{@link #software} means the key is held by the OS rather than by secure hardware, so a
 * compromised device can export it. A registration that accepts it gains nothing over having no
 * attestation at all, which is why the verifier requires a hardware level by default.
 */
public enum AndroidKeyAttestationSecurityLevel {
  software(0),
  trusted_environment(1),
  strong_box(2),
  undefined(-1);

  int value;

  AndroidKeyAttestationSecurityLevel(int value) {
    this.value = value;
  }

  public static AndroidKeyAttestationSecurityLevel of(int value) {
    for (AndroidKeyAttestationSecurityLevel level : values()) {
      if (level.value == value) {
        return level;
      }
    }
    return undefined;
  }

  public boolean isBackedByHardware() {
    return this == trusted_environment || this == strong_box;
  }
}

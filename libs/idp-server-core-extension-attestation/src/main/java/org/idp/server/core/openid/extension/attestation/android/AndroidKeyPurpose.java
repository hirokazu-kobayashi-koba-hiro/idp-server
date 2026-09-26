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
 * What the attested key is allowed to do, as recorded in {@code purpose} (tag 1).
 *
 * <p>A Client Instance key exists to sign Client Attestation PoP JWTs, so a key the secure hardware
 * will not use for signing cannot serve as one. Such a key is rejected at registration rather than
 * at first use: the failure would otherwise surface as a signature that does not verify, long after
 * the registration that should have refused it, and on an endpoint that cannot say why.
 */
public enum AndroidKeyPurpose {
  encrypt(0),
  decrypt(1),
  sign(2),
  verify(3),
  derive_key(4),
  wrap_key(5),
  agree_key(6),
  attest_key(7),
  undefined(-1);

  int value;

  AndroidKeyPurpose(int value) {
    this.value = value;
  }

  public static AndroidKeyPurpose of(int value) {
    for (AndroidKeyPurpose purpose : values()) {
      if (purpose.value == value) {
        return purpose;
      }
    }
    return undefined;
  }
}

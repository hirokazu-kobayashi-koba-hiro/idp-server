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
 * How the device booted, as recorded in {@code RootOfTrust.verifiedBootState} (tag 704).
 *
 * <p>This is what makes the rest of the extension trustworthy. {@code attestationApplicationId} is
 * reported by the Android platform, not by KeyMint, so on a device running a modified OS the
 * platform can name any application it likes. KeyMint writes the boot state itself, from the
 * bootloader: {@link #verified} means the OS was checked against the key the device shipped with.
 *
 * <p>{@link #self_signed} is a verified boot against a key the owner installed (a custom OS such as
 * GrapheneOS, with the bootloader relocked). It is refused by default: the OS is verified, but not
 * against a key the operator of this server has any reason to trust.
 *
 * @see <a href="https://source.android.com/docs/security/features/keystore/attestation">Key
 *     attestation</a>
 */
public enum AndroidVerifiedBootState {
  verified(0),
  self_signed(1),
  unverified(2),
  failed(3),
  undefined(-1);

  int value;

  AndroidVerifiedBootState(int value) {
    this.value = value;
  }

  public static AndroidVerifiedBootState of(int value) {
    for (AndroidVerifiedBootState state : values()) {
      if (state.value == value) {
        return state;
      }
    }
    return undefined;
  }

  /** Looks a state up by its configuration name, or {@code undefined} when there is none. */
  public static AndroidVerifiedBootState of(String name) {
    for (AndroidVerifiedBootState state : values()) {
      if (state != undefined && state.name().equals(name)) {
        return state;
      }
    }
    return undefined;
  }
}

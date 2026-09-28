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
 * The boot state KeyMint recorded, from {@code rootOfTrust} (tag 704) of the hardware list.
 *
 * <pre>
 * RootOfTrust ::= SEQUENCE {
 *   verifiedBootKey    OCTET_STRING,
 *   deviceLocked       BOOLEAN,
 *   verifiedBootState  VerifiedBootState,
 *   verifiedBootHash   OCTET_STRING,   -- version 3 and later
 * }
 * </pre>
 *
 * <p>{@code verifiedBootKey} and {@code verifiedBootHash} are not kept: they identify the OS build
 * and signing key rather than decide anything this verifier checks.
 */
public class AndroidRootOfTrust {

  boolean reported;
  boolean deviceLocked;
  AndroidVerifiedBootState verifiedBootState;

  AndroidRootOfTrust(
      boolean reported, boolean deviceLocked, AndroidVerifiedBootState verifiedBootState) {
    this.reported = reported;
    this.deviceLocked = deviceLocked;
    this.verifiedBootState = verifiedBootState;
  }

  static AndroidRootOfTrust of(boolean deviceLocked, AndroidVerifiedBootState verifiedBootState) {
    return new AndroidRootOfTrust(true, deviceLocked, verifiedBootState);
  }

  /** A device that did not report the root of trust. */
  static AndroidRootOfTrust notReported() {
    return new AndroidRootOfTrust(false, false, AndroidVerifiedBootState.undefined);
  }

  public boolean isReported() {
    return reported;
  }

  public boolean isDeviceLocked() {
    return deviceLocked;
  }

  public AndroidVerifiedBootState verifiedBootState() {
    return verifiedBootState;
  }
}

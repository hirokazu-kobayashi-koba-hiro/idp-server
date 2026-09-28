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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * The App Attest environment a key was generated in, as carried by the authenticator data's {@code
 * aaguid}.
 *
 * <p>Apple states the value is "appattestdevelop" during development, and "appattest" followed by
 * seven 0x00 bytes after distribution. Keys from the two environments are not interchangeable, and
 * a development key says nothing about a device the operator does not control, which is why the
 * configuration defaults to {@link #production}.
 *
 * @see <a
 *     href="https://developer.apple.com/documentation/devicecheck/validating-apps-that-connect-to-your-server">Validating
 *     apps that connect to your server</a>
 */
public enum IosAppAttestEnvironment {
  production(aaguid("appattest")),
  development(aaguid("appattestdevelop"));

  static final int AAGUID_LENGTH = 16;

  byte[] aaguid;

  IosAppAttestEnvironment(byte[] aaguid) {
    this.aaguid = aaguid;
  }

  /** The name padded to 16 bytes with 0x00, which is how Apple defines both values. */
  private static byte[] aaguid(String name) {
    return Arrays.copyOf(name.getBytes(StandardCharsets.US_ASCII), AAGUID_LENGTH);
  }

  public byte[] aaguid() {
    return aaguid.clone();
  }

  public boolean matches(byte[] presented) {
    return java.security.MessageDigest.isEqual(aaguid, presented);
  }
}

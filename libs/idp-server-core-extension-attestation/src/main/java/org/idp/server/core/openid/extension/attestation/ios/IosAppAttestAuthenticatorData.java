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

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * The authenticator data carried in an App Attest attestation object.
 *
 * <p>Apple produces it "according to the [WebAuthn] specification", so the layout is WebAuthn's
 * authenticator data rather than a format of Apple's own:
 *
 * <pre>
 *   offset  size  field
 *   0       32    rpIdHash          SHA-256 of the App ID
 *   32      1     flags
 *   33      4     counter           signCount, 0 at attestation
 *   37      16    aaguid            the App Attest environment
 *   53      2     credentialIdLength
 *   55      L     credentialId      SHA-256 of the attested public key
 *   55+L    77    the COSE encoded public key
 *   ...           extensions
 * </pre>
 *
 * <p>The bytes are kept as received: the nonce that ties this data to the certificate is computed
 * over them, so re-serializing a parsed form would compute the hash of something the device never
 * signed.
 */
public class IosAppAttestAuthenticatorData {

  static final int RP_ID_HASH_OFFSET = 0;
  static final int RP_ID_HASH_LENGTH = 32;
  static final int COUNTER_OFFSET = 33;
  static final int AAGUID_OFFSET = 37;
  static final int CREDENTIAL_ID_LENGTH_OFFSET = 53;
  static final int CREDENTIAL_ID_OFFSET = 55;

  byte[] raw;
  byte[] rpIdHash;
  long counter;
  byte[] aaguid;
  byte[] credentialId;

  IosAppAttestAuthenticatorData(
      byte[] raw, byte[] rpIdHash, long counter, byte[] aaguid, byte[] credentialId) {
    this.raw = raw;
    this.rpIdHash = rpIdHash;
    this.counter = counter;
    this.aaguid = aaguid;
    this.credentialId = credentialId;
  }

  public static IosAppAttestAuthenticatorData parse(byte[] raw) {
    if (raw.length < CREDENTIAL_ID_OFFSET) {
      throw new IosAppAttestException(
          "authData is shorter than the attested credential data it must contain: " + raw.length);
    }

    int credentialIdLength =
        ByteBuffer.wrap(raw, CREDENTIAL_ID_LENGTH_OFFSET, 2).getShort() & 0xffff;
    if (raw.length < CREDENTIAL_ID_OFFSET + credentialIdLength) {
      throw new IosAppAttestException(
          "authData declares a credentialId of "
              + credentialIdLength
              + " bytes but holds "
              + (raw.length - CREDENTIAL_ID_OFFSET));
    }

    return new IosAppAttestAuthenticatorData(
        raw,
        Arrays.copyOfRange(raw, RP_ID_HASH_OFFSET, RP_ID_HASH_OFFSET + RP_ID_HASH_LENGTH),
        ByteBuffer.wrap(raw, COUNTER_OFFSET, 4).getInt() & 0xffffffffL,
        Arrays.copyOfRange(
            raw, AAGUID_OFFSET, AAGUID_OFFSET + IosAppAttestEnvironment.AAGUID_LENGTH),
        Arrays.copyOfRange(raw, CREDENTIAL_ID_OFFSET, CREDENTIAL_ID_OFFSET + credentialIdLength));
  }

  public byte[] raw() {
    return raw;
  }

  public byte[] rpIdHash() {
    return rpIdHash;
  }

  public long counter() {
    return counter;
  }

  public byte[] aaguid() {
    return aaguid;
  }

  public byte[] credentialId() {
    return credentialId;
  }
}

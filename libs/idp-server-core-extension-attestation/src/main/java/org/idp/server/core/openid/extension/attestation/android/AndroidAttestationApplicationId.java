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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;
import org.idp.server.platform.asn1.Asn1InvalidException;
import org.idp.server.platform.asn1.Asn1Node;

/**
 * The application the attested key was created for, as recorded by the Android platform.
 *
 * <pre>
 * AttestationApplicationId ::= SEQUENCE {
 *   package_infos               SET OF AttestationPackageInfo,
 *   signature_digests           SET OF OCTET_STRING,
 * }
 * AttestationPackageInfo ::= SEQUENCE {
 *   package_name                OCTET_STRING,
 *   version                     INTEGER,
 * }
 * </pre>
 *
 * <p>This is the only part of the attestation that says <b>which</b> application asked for the key.
 * Without checking it, a key attested by any application on any device would be accepted — the
 * evidence would prove that secure hardware exists, not that this client produced the key.
 *
 * <p>The digests are of the signing certificates, so they change when the app is re-signed. An
 * upload key rotation therefore has to be rolled out as a configuration change alongside the app.
 */
public class AndroidAttestationApplicationId {

  Set<String> packageNames;
  Set<String> signatureDigests;

  AndroidAttestationApplicationId(Set<String> packageNames, Set<String> signatureDigests) {
    this.packageNames = packageNames;
    this.signatureDigests = signatureDigests;
  }

  static AndroidAttestationApplicationId parse(byte[] encoded) {
    try {
      Asn1Node applicationId = Asn1Node.parse(encoded);

      Set<String> packageNames = new LinkedHashSet<>();
      for (Asn1Node packageInfo : applicationId.at(0).elements()) {
        packageNames.add(new String(packageInfo.at(0).octets(), StandardCharsets.UTF_8));
      }

      // Compared as base64url without padding, the same shape the client configuration holds.
      Set<String> signatureDigests = new LinkedHashSet<>();
      for (Asn1Node digest : applicationId.at(1).elements()) {
        signatureDigests.add(
            Base64.getUrlEncoder().withoutPadding().encodeToString(digest.octets()));
      }

      return new AndroidAttestationApplicationId(packageNames, signatureDigests);
    } catch (Asn1InvalidException e) {
      throw new AndroidKeyAttestationException(
          "failed to parse attestationApplicationId: " + e.getMessage(), e);
    }
  }

  Set<String> packageNames() {
    return packageNames;
  }

  Set<String> signatureDigests() {
    return signatureDigests;
  }
}

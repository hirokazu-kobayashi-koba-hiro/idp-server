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
package org.idp.server.platform.jose.sdjwt;

import com.authlete.sd.Disclosure;
import com.authlete.sd.SDJWT;
import com.authlete.sd.SDObjectBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.jose.JoseInvalidException;
import org.idp.server.platform.jose.JsonWebKey;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JsonWebSignatureFactory;

/**
 * Issues SD-JWTs (RFC 9901).
 *
 * <p>The only place idp-server touches the SD-JWT library: Disclosures, their digests and the
 * {@code _sd} array come from it, while signing stays with {@link JsonWebSignatureFactory} so the
 * header (typ, x5c) is under this code's control. Replacing the library means replacing this class.
 *
 * <p>Selective disclosure is for top-level claims. Each Disclosure gets a fresh salt, and the
 * {@code _sd} digests are sorted, which Section 4.2.4.2 names as a way of hiding their order.
 */
public class SdJwtIssuer {

  static final String HASH_ALGORITHM = "sha-256";

  JsonWebSignatureFactory jsonWebSignatureFactory = new JsonWebSignatureFactory();

  /**
   * @param plainClaims claims every holder presentation reveals (iss, iat, vct, cnf, ...)
   * @param disclosableClaims top-level claims the holder decides to reveal
   * @param type the {@code typ} header, e.g. {@code dc+sd-jwt}
   * @param signingKey the Issuer's private key
   * @param includeX5c whether to carry the key's certificate chain in the header
   */
  public SdJwt issue(
      Map<String, Object> plainClaims,
      Map<String, Object> disclosableClaims,
      String type,
      JsonWebKey signingKey,
      boolean includeX5c)
      throws JoseInvalidException {

    SDObjectBuilder builder = new SDObjectBuilder(HASH_ALGORITHM);
    plainClaims.forEach(builder::putClaim);

    List<Disclosure> disclosures = new ArrayList<>();
    disclosableClaims.forEach((name, value) -> disclosures.add(builder.putSDClaim(name, value)));

    Map<String, Object> payload = builder.build(true);

    JsonWebSignature jws =
        jsonWebSignatureFactory.createTypedWithAsymmetricKey(payload, type, signingKey, includeX5c);
    String issuerSignedJwt = jws.serialize();

    SDJWT sdJwt = new SDJWT(issuerSignedJwt, disclosures);
    return new SdJwt(sdJwt.toString(), issuerSignedJwt, disclosures.size());
  }
}

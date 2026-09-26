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
package org.idp.server.core.extension.oid4vci.issuance;

import java.util.LinkedHashMap;
import java.util.Map;
import org.idp.server.core.openid.oauth.configuration.vci.CredentialClaimMapping;
import org.idp.server.core.openid.oauth.configuration.vci.CredentialConfiguration;
import org.idp.server.core.openid.oauth.configuration.vci.CredentialIssuanceDefinition;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.jose.JoseInvalidException;
import org.idp.server.platform.jose.JsonWebKey;
import org.idp.server.platform.jose.sdjwt.SdJwtIssuer;

/**
 * Builds an SD-JWT VC ({@code dc+sd-jwt}, draft-ietf-oauth-sd-jwt-vc) for one holder key.
 *
 * <p>Always disclosed: {@code iss}, {@code iat}, {@code exp}, {@code vct} and {@code cnf}, which a
 * Verifier needs before it can trust anything else. {@code iat} is the start of the day of issuance
 * and {@code exp} counts from it, so the time claims do not link credentials. The configured claims
 * follow their {@code selectively_disclosable} setting; a claim the user has no value for is left
 * out rather than issued empty.
 *
 * <p>The signing key's certificate chain goes into the {@code x5c} header when the key has one,
 * which HAIP requires of a Credential Issuer.
 */
public class SdJwtVcCredentialCreator {

  static final String TYPE = "dc+sd-jwt";

  SdJwtIssuer sdJwtIssuer = new SdJwtIssuer();

  public String create(
      String credentialIssuer,
      CredentialConfiguration configuration,
      CredentialIssuanceDefinition definition,
      CredentialSubjectSource subject,
      JsonWebKey holderKey,
      JsonWebKey signingKey)
      throws JoseInvalidException {

    long issuedAt = startOfDay(SystemDateTime.currentEpochMilliSecond() / 1000);

    Map<String, Object> plain = new LinkedHashMap<>();
    plain.put("iss", credentialIssuer);
    plain.put("iat", issuedAt);
    plain.put("exp", issuedAt + definition.expiresIn());
    plain.put("vct", configuration.vct());
    plain.put("cnf", Map.of("jwk", holderKey.toPublicMap()));

    Map<String, Object> disclosable = new LinkedHashMap<>();
    for (CredentialClaimMapping claim : definition.claims()) {
      Object value = subject.read(claim.from());
      if (value == null) {
        continue;
      }
      if (claim.selectivelyDisclosable()) {
        disclosable.put(claim.name(), value);
      } else {
        plain.put(claim.name(), value);
      }
    }

    return sdJwtIssuer.issue(plain, disclosable, TYPE, signingKey, signingKey.hasX5c()).value();
  }

  /**
   * RFC 9901 Section 10.1: time claims "MUST either be randomized within a time period considered
   * appropriate ... or rounded (e.g., rounded down to the beginning of the day)", or two
   * credentials of the same holder can be linked by their exact issuance time. Rounded down to the
   * start of the UTC day.
   */
  static long startOfDay(long epochSecond) {
    return epochSecond - Math.floorMod(epochSecond, 86_400L);
  }
}

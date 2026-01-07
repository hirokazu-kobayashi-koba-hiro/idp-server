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

package org.idp.server.core.openid.oauth.type.oauth;

import java.util.Objects;
import org.idp.server.platform.jose.JoseInvalidException;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JsonWebTokenClaims;

public class JwtBearerAssertion {

  private static final String DEVICE_ISSUER_PREFIX = "device:";

  String value;
  JsonWebSignature jsonWebSignature;

  public JwtBearerAssertion() {}

  public JwtBearerAssertion(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return value != null && !value.isEmpty();
  }

  public JsonWebSignature parse() throws JoseInvalidException {
    if (jsonWebSignature == null) {
      jsonWebSignature = JsonWebSignature.parse(value);
    }
    return jsonWebSignature;
  }

  public JsonWebTokenClaims claims() throws JoseInvalidException {
    return parse().claims();
  }

  public String issuer() throws JoseInvalidException {
    return claims().getIss();
  }

  public boolean hasIssuer() throws JoseInvalidException {
    return claims().hasIss();
  }

  public String subject() throws JoseInvalidException {
    return claims().getSub();
  }

  public boolean hasSubject() throws JoseInvalidException {
    return claims().hasSub();
  }

  public boolean isDeviceIssuer() throws JoseInvalidException {
    String iss = issuer();
    return iss != null && iss.startsWith(DEVICE_ISSUER_PREFIX);
  }

  public String extractDeviceId() throws JoseInvalidException {
    if (!isDeviceIssuer()) {
      throw new JoseInvalidException("Issuer is not a device issuer", null);
    }
    return issuer().substring(DEVICE_ISSUER_PREFIX.length());
  }

  public String algorithm() throws JoseInvalidException {
    return parse().algorithm();
  }

  public String keyId() throws JoseInvalidException {
    return parse().keyId();
  }

  public boolean hasKeyId() throws JoseInvalidException {
    return parse().hasKeyId();
  }

  public boolean isSymmetricType() throws JoseInvalidException {
    return parse().isSymmetricType();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    JwtBearerAssertion that = (JwtBearerAssertion) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}

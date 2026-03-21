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

/**
 * SubjectToken
 *
 * <p>RFC 8693 Section 2.1 - subject_token parameter.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-2.1">RFC 8693 Section 2.1</a>
 */
public class SubjectToken {

  String value;
  JsonWebSignature jsonWebSignature;

  public SubjectToken() {}

  public SubjectToken(String value) {
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

  public String subject() throws JoseInvalidException {
    return claims().getSub();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    SubjectToken that = (SubjectToken) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}

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


package org.idp.server.basic.jose;

import com.nimbusds.jwt.JWTClaimNames;
import com.nimbusds.jwt.JWTClaimsSet;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** JsonWebTokenClaims */
public class JsonWebTokenClaims {
  JWTClaimsSet value;

  public JsonWebTokenClaims() {}

  public JsonWebTokenClaims(JWTClaimsSet value) {
    this.value = value;
  }

  public String getIss() {
    return value.getIssuer();
  }

  public boolean hasIss() {
    return contains(JWTClaimNames.ISSUER);
  }

  public String getSub() {
    return value.getSubject();
  }

  public boolean hasSub() {
    return contains(JWTClaimNames.SUBJECT);
  }

  public List<String> getAud() {
    return value.getAudience();
  }

  public boolean hasAud() {
    return contains(JWTClaimNames.AUDIENCE);
  }

  public Date getNbf() {
    return value.getNotBeforeTime();
  }

  public boolean hasNbf() {
    return contains(JWTClaimNames.NOT_BEFORE);
  }

  public Date getIat() {
    return value.getIssueTime();
  }

  public boolean hasIat() {
    return contains(JWTClaimNames.ISSUED_AT);
  }

  public String getJti() {
    return value.getJWTID();
  }

  public boolean hasJti() {
    return contains(JWTClaimNames.JWT_ID);
  }

  public Date getExp() {
    return value.getExpirationTime();
  }

  public boolean hasExp() {
    return contains(JWTClaimNames.EXPIRATION_TIME);
  }

  public Map<String, Object> payload() {
    return value.getClaims();
  }

  public String getValue(String key) {
    if (!contains(key)) {
      return "";
    }
    return (String) payload().get(key);
  }

  public boolean contains(String key) {
    if (Objects.isNull(value)) {
      return false;
    }
    return value.getClaims().containsKey(key);
  }

  public boolean exists() {
    if (Objects.isNull(value) || Objects.isNull(value.getClaims())) {
      return false;
    }
    return !value.getClaims().isEmpty();
  }

  public Map<String, Object> toMap() {
    return value.getClaims();
  }
}

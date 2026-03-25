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

package org.idp.server.core.openid.token.service;

import java.util.Map;

/**
 * ExternalIntrospectionResult
 *
 * <p>Encapsulates the response from an external IdP's token introspection endpoint (RFC 7662).
 */
public class ExternalIntrospectionResult {
  Map<String, Object> claims;

  public ExternalIntrospectionResult(Map<String, Object> claims) {
    this.claims = claims;
  }

  public boolean isActive() {
    Object active = claims.get("active");
    return Boolean.TRUE.equals(active);
  }

  public String subject() {
    Object sub = claims.get("sub");
    return sub != null ? sub.toString() : null;
  }

  public String issuer() {
    Object iss = claims.get("iss");
    return iss != null ? iss.toString() : null;
  }

  public boolean hasSubject() {
    return subject() != null && !subject().isEmpty();
  }

  public Map<String, Object> claims() {
    return claims;
  }
}

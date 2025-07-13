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

package org.idp.server.core.oidc.token.tokenintrospection;

import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.type.ArrayValueMap;
import org.idp.server.core.oidc.type.OAuthRequestKey;
import org.idp.server.core.oidc.type.oauth.*;

/** TokenRequestParameters */
public class TokenIntrospectionRequestParameters {
  ArrayValueMap values;

  public TokenIntrospectionRequestParameters() {
    this.values = new ArrayValueMap();
  }

  public TokenIntrospectionRequestParameters(ArrayValueMap values) {
    this.values = values;
  }

  public TokenIntrospectionRequestParameters(Map<String, String[]> values) {
    this.values = new ArrayValueMap(values);
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public AccessTokenEntity accessToken() {
    return new AccessTokenEntity(getValueOrEmpty(OAuthRequestKey.token));
  }

  public RefreshTokenEntity refreshToken() {
    return new RefreshTokenEntity(getValueOrEmpty(OAuthRequestKey.token));
  }

  public boolean hasToken() {
    return contains(OAuthRequestKey.token);
  }

  public String getValueOrEmpty(OAuthRequestKey key) {
    return values.getFirstOrEmpty(key.name());
  }

  boolean contains(OAuthRequestKey key) {
    return values.contains(key.name());
  }

  public List<String> multiValueKeys() {
    return values.multiValueKeys();
  }
}

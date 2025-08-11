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

package org.idp.server.core.openid.token.tokenrevocation;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.clientauthenticator.BackchannelRequestParameters;
import org.idp.server.core.openid.oauth.type.ArrayValueMap;
import org.idp.server.core.openid.oauth.type.OAuthRequestKey;
import org.idp.server.core.openid.oauth.type.oauth.*;

/** TokenRevocationRequestParameters */
public class TokenRevocationRequestParameters implements BackchannelRequestParameters {
  ArrayValueMap values;

  public TokenRevocationRequestParameters() {
    this.values = new ArrayValueMap();
  }

  public TokenRevocationRequestParameters(ArrayValueMap values) {
    this.values = values;
  }

  public TokenRevocationRequestParameters(Map<String, String[]> values) {
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

  @Override
  public RequestedClientId clientId() {
    return new RequestedClientId(getValueOrEmpty(OAuthRequestKey.client_id));
  }

  @Override
  public boolean hasClientId() {
    return contains(OAuthRequestKey.client_id);
  }

  @Override
  public ClientSecret clientSecret() {
    return new ClientSecret(getValueOrEmpty(OAuthRequestKey.client_secret));
  }

  @Override
  public boolean hasClientSecret() {
    return contains(OAuthRequestKey.client_secret);
  }

  @Override
  public ClientAssertion clientAssertion() {
    return new ClientAssertion(getValueOrEmpty(OAuthRequestKey.client_assertion));
  }

  @Override
  public boolean hasClientAssertion() {
    return contains(OAuthRequestKey.client_assertion);
  }

  @Override
  public ClientAssertionType clientAssertionType() {
    return ClientAssertionType.of(getValueOrEmpty(OAuthRequestKey.client_assertion_type));
  }

  @Override
  public boolean hasClientAssertionType() {
    return contains(OAuthRequestKey.client_assertion_type);
  }
}

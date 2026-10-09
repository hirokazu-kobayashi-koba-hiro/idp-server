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

package org.idp.server.account_linking.io;

import java.util.Map;
import org.idp.server.core.openid.oauth.clientauthenticator.BackchannelRequestParameters;
import org.idp.server.core.openid.oauth.type.ArrayValueMap;
import org.idp.server.core.openid.oauth.type.OAuthRequestKey;
import org.idp.server.core.openid.oauth.type.oauth.AccessTokenEntity;
import org.idp.server.core.openid.oauth.type.oauth.ClientAssertion;
import org.idp.server.core.openid.oauth.type.oauth.ClientAssertionType;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecret;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;

/**
 * Form parameters of a stored token retrieval.
 *
 * <p>The client authenticates with the same parameters as at the token endpoint, and the user it
 * acts for is named by {@code token}: the user's access token, carried in the body as at
 * introspection. It cannot travel as a Bearer header, which {@code client_secret_basic} already
 * occupies.
 */
public class LinkedExternalAccountTokenParameters implements BackchannelRequestParameters {

  ArrayValueMap values;

  public LinkedExternalAccountTokenParameters(Map<String, String[]> values) {
    this.values = new ArrayValueMap(values);
  }

  /** The access token of the user whose linked account is asked for. */
  public AccessTokenEntity userAccessToken() {
    return new AccessTokenEntity(getValueOrEmpty(OAuthRequestKey.token));
  }

  public boolean hasUserAccessToken() {
    return contains(OAuthRequestKey.token) && !getValueOrEmpty(OAuthRequestKey.token).isEmpty();
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

  private String getValueOrEmpty(OAuthRequestKey key) {
    return values.getFirstOrEmpty(key.name());
  }

  private boolean contains(OAuthRequestKey key) {
    return values.contains(key.name());
  }
}

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

package org.idp.server.core.oidc.token;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.type.ArrayValueMap;
import org.idp.server.basic.type.OAuthRequestKey;
import org.idp.server.basic.type.ciba.AuthReqId;
import org.idp.server.basic.type.oauth.*;
import org.idp.server.basic.type.pkce.CodeVerifier;
import org.idp.server.core.oidc.clientauthenticator.BackchannelRequestParameters;

/** TokenRequestParameters */
public class TokenRequestParameters implements BackchannelRequestParameters {
  ArrayValueMap values;

  public TokenRequestParameters() {
    this.values = new ArrayValueMap();
  }

  public TokenRequestParameters(ArrayValueMap values) {
    this.values = values;
  }

  public TokenRequestParameters(Map<String, String[]> values) {
    this.values = new ArrayValueMap(values);
  }

  public boolean isEmpty() {
    return values.isEmpty();
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

  public RedirectUri redirectUri() {
    return new RedirectUri(getValueOrEmpty(OAuthRequestKey.redirect_uri));
  }

  public boolean hasRedirectUri() {
    return contains(OAuthRequestKey.redirect_uri);
  }

  public AuthorizationCode code() {
    return new AuthorizationCode(getValueOrEmpty(OAuthRequestKey.code));
  }

  public boolean hasCode() {
    return contains(OAuthRequestKey.code);
  }

  public GrantType grantType() {
    return GrantType.of(getValueOrEmpty(OAuthRequestKey.grant_type));
  }

  public boolean hasGrantType() {
    return contains(OAuthRequestKey.grant_type);
  }

  public RefreshTokenEntity refreshToken() {
    return new RefreshTokenEntity(getValueOrEmpty(OAuthRequestKey.refresh_token));
  }

  public boolean hasRefreshToken() {
    return contains(OAuthRequestKey.refresh_token);
  }

  public Scopes scopes() {
    return new Scopes(getValueOrEmpty(OAuthRequestKey.scope));
  }

  public Username username() {
    return new Username(getValueOrEmpty(OAuthRequestKey.username));
  }

  public boolean hasUsername() {
    return contains(OAuthRequestKey.username);
  }

  public Password password() {
    return new Password(getValueOrEmpty(OAuthRequestKey.password));
  }

  public boolean hasPassword() {
    return contains(OAuthRequestKey.password);
  }

  public CodeVerifier codeVerifier() {
    return new CodeVerifier(getValueOrEmpty(OAuthRequestKey.code_verifier));
  }

  public boolean hasCodeVerifier() {
    return contains(OAuthRequestKey.code_verifier);
  }

  public AuthReqId authReqId() {
    return new AuthReqId(getValueOrEmpty(OAuthRequestKey.auth_req_id));
  }

  public boolean hasAuthReqId() {
    return contains(OAuthRequestKey.auth_req_id);
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

  public boolean isRefreshTokenGrant() {
    return grantType() == GrantType.refresh_token;
  }
}

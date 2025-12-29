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

package org.idp.server.core.openid.oauth.logout;

import org.idp.server.core.openid.oauth.OAuthSession;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.request.OAuthLogoutParameters;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.State;
import org.idp.server.core.openid.oauth.type.oidc.IdTokenHint;
import org.idp.server.core.openid.oauth.type.oidc.logout.PostLogoutRedirectUri;
import org.idp.server.platform.jose.JsonWebTokenClaims;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * OAuthLogoutContext
 *
 * <p>Context object for RP-Initiated Logout request processing. Contains validated id_token_hint
 * claims, client configuration, and session information.
 *
 * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html">RP-Initiated
 *     Logout</a>
 */
public class OAuthLogoutContext {

  Tenant tenant;
  OAuthLogoutParameters parameters;
  AuthorizationServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;
  JsonWebTokenClaims idTokenClaims;
  OAuthSession session;

  public OAuthLogoutContext() {}

  public OAuthLogoutContext(
      Tenant tenant,
      OAuthLogoutParameters parameters,
      AuthorizationServerConfiguration serverConfiguration,
      JsonWebTokenClaims idTokenClaims) {
    this.tenant = tenant;
    this.parameters = parameters;
    this.serverConfiguration = serverConfiguration;
    this.idTokenClaims = idTokenClaims;
  }

  public void setClientConfiguration(ClientConfiguration clientConfiguration) {
    this.clientConfiguration = clientConfiguration;
  }

  public void setSession(OAuthSession session) {
    this.session = session;
  }

  public Tenant tenant() {
    return tenant;
  }

  public OAuthLogoutParameters parameters() {
    return parameters;
  }

  public AuthorizationServerConfiguration serverConfiguration() {
    return serverConfiguration;
  }

  public ClientConfiguration clientConfiguration() {
    return clientConfiguration;
  }

  public JsonWebTokenClaims idTokenClaims() {
    return idTokenClaims;
  }

  public boolean hasIdTokenClaims() {
    return idTokenClaims != null;
  }

  public OAuthSession session() {
    return session;
  }

  public boolean hasSession() {
    return session != null && session.exists();
  }

  public IdTokenHint idTokenHint() {
    return parameters.idTokenHint();
  }

  public boolean hasIdTokenHint() {
    return parameters.hasIdTokenHint();
  }

  public PostLogoutRedirectUri postLogoutRedirectUri() {
    return parameters.postLogoutRedirectUri();
  }

  public boolean hasPostLogoutRedirectUri() {
    return parameters.hasPostLogoutRedirectUri();
  }

  public State state() {
    return parameters.state();
  }

  public boolean hasState() {
    return parameters.hasState();
  }

  /**
   * Returns the client id.
   *
   * <p>Priority: 1. client_id from parameters 2. aud from id_token_hint
   *
   * @return client id
   */
  public RequestedClientId clientId() {
    if (parameters.hasClientId()) {
      return parameters.clientId();
    }
    if (hasIdTokenClaims() && idTokenClaims.hasAud() && !idTokenClaims.getAud().isEmpty()) {
      return new RequestedClientId(idTokenClaims.getAud().get(0));
    }
    return new RequestedClientId("");
  }

  public boolean hasClientConfiguration() {
    return clientConfiguration != null && clientConfiguration.exists();
  }

  /**
   * Gets the subject from id_token_hint.
   *
   * @return subject claim value or null
   */
  public String subject() {
    if (hasIdTokenClaims()) {
      return idTokenClaims.getSub();
    }
    return null;
  }

  /**
   * Gets the session ID (sid) from id_token_hint.
   *
   * @return sid claim value or null
   */
  public String sessionId() {
    if (hasIdTokenClaims() && idTokenClaims.contains("sid")) {
      return idTokenClaims.getValue("sid");
    }
    return null;
  }

  public boolean hasSessionId() {
    return sessionId() != null && !sessionId().isEmpty();
  }
}

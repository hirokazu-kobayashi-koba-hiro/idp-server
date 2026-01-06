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

package org.idp.server.core.openid.oauth.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.session.OPSession;

public class OAuthViewDataCreator {

  AuthorizationRequest authorizationRequest;
  AuthorizationServerConfiguration authorizationServerConfiguration;
  ClientConfiguration clientConfiguration;
  OPSession opSession;
  Map<String, Object> additionalViewData;

  public OAuthViewDataCreator(
      AuthorizationRequest authorizationRequest,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      OPSession opSession,
      Map<String, Object> additionalViewData) {
    this.authorizationRequest = authorizationRequest;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
    this.clientConfiguration = clientConfiguration;
    this.opSession = opSession;
    this.additionalViewData = additionalViewData;
  }

  public OAuthViewData create() {
    String clientId = authorizationRequest.requestedClientId().value();
    String clientName = clientConfiguration.clientNameValue();
    String clientUri = clientConfiguration.clientUri();
    String logoUri = clientConfiguration.logoUri();
    List<String> contacts = clientConfiguration.contacts();
    String tosUri = clientConfiguration.tosUri();
    String policyUri = clientConfiguration.policyUri();
    Map<String, String> customParams = authorizationRequest.customParams().values();
    List<String> scopes = authorizationRequest.scopes().toStringList();
    boolean sessionEnabled = isSessionEnabled();
    List<Map<String, Object>> availableFederationsAsMapList =
        clientConfiguration.availableFederationsAsMapList();

    return new OAuthViewData(
        clientId,
        clientName,
        clientUri,
        logoUri,
        contacts,
        tosUri,
        policyUri,
        scopes,
        sessionEnabled,
        availableFederationsAsMapList,
        customParams,
        additionalViewData);
  }

  /**
   * Determines if session-based authorization is enabled.
   *
   * <p>Session is enabled when:
   *
   * <ul>
   *   <li>OPSession exists and is active
   *   <li>prompt=login is not specified (prompt=login forces re-authentication)
   *   <li>If max_age is specified, auth_time must be within the max_age window
   *   <li>If acr_values is specified, session's acr must be in the requested acr_values
   * </ul>
   *
   * @return true if session-based authorization can be used
   */
  private boolean isSessionEnabled() {
    // No session available
    if (opSession == null || !opSession.exists() || !opSession.isActive()) {
      return false;
    }

    // prompt=login forces re-authentication
    if (authorizationRequest.isPromptLogin()) {
      return false;
    }

    // Check max_age constraint
    if (authorizationRequest.hasMaxAge()) {
      long maxAgeSeconds = authorizationRequest.maxAge().toLongValue();
      Instant maxAuthTime = opSession.authTime().plusSeconds(maxAgeSeconds);
      if (Instant.now().isAfter(maxAuthTime)) {
        return false;
      }
    }

    // Check acr_values constraint - prevent ACR downgrade attacks
    if (authorizationRequest.hasAcrValues()) {
      String sessionAcr = opSession.acr();
      if (sessionAcr == null || sessionAcr.isEmpty()) {
        return false;
      }
      if (!authorizationRequest.acrValues().contains(sessionAcr)) {
        return false;
      }
    }

    return true;
  }
}

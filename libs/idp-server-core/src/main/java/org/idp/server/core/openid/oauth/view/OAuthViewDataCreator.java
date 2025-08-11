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

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.OAuthSession;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;

public class OAuthViewDataCreator {

  AuthorizationRequest authorizationRequest;
  AuthorizationServerConfiguration authorizationServerConfiguration;
  ClientConfiguration clientConfiguration;
  OAuthSession session;
  Map<String, Object> additionalViewData;

  public OAuthViewDataCreator(
      AuthorizationRequest authorizationRequest,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      OAuthSession session,
      Map<String, Object> additionalViewData) {
    this.authorizationRequest = authorizationRequest;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
    this.clientConfiguration = clientConfiguration;
    this.session = session;
    this.additionalViewData = additionalViewData;
  }

  public OAuthViewData create() {
    String clientId = authorizationRequest.requestedClientId().value();
    String clientName = clientConfiguration.clientNameValue();
    String clientUri = clientConfiguration.clientUri();
    String logoUri = clientConfiguration.logoUri();
    String contacts = clientConfiguration.contacts();
    String tosUri = clientConfiguration.tosUri();
    String policyUri = clientConfiguration.policyUri();
    Map<String, String> customParams = authorizationRequest.customParams().values();
    List<String> scopes = authorizationRequest.scopes().toStringList();
    boolean sessionEnabled = session != null && session.isValid(authorizationRequest);
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
}

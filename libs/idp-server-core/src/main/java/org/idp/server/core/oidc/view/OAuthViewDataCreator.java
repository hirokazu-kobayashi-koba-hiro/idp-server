/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.view;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.OAuthSession;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.request.AuthorizationRequest;

public class OAuthViewDataCreator {

  AuthorizationRequest authorizationRequest;
  AuthorizationServerConfiguration authorizationServerConfiguration;
  ClientConfiguration clientConfiguration;
  OAuthSession session;

  public OAuthViewDataCreator(
      AuthorizationRequest authorizationRequest,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      OAuthSession session) {
    this.authorizationRequest = authorizationRequest;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
    this.clientConfiguration = clientConfiguration;
    this.session = session;
  }

  public OAuthViewData create() {
    String clientId = authorizationRequest.retrieveClientId().value();
    String clientName = clientConfiguration.clientNameValue();
    String clientUri = clientConfiguration.clientUri();
    String logoUri = clientConfiguration.logoUri();
    String contacts = clientConfiguration.contacts();
    String tosUri = clientConfiguration.tosUri();
    String policyUri = clientConfiguration.policyUri();
    Map<String, String> customParams = authorizationRequest.customParams().values();
    List<String> scopes = authorizationRequest.scopes().toStringList();
    Map<String, Object> contents = new HashMap<>();
    contents.put("client_id", clientId);
    contents.put("client_name", clientName);
    contents.put("client_uri", clientUri);
    contents.put("logo_uri", logoUri);
    contents.put("contacts", contacts);
    contents.put("tos_uri", tosUri);
    contents.put("policy_uri", policyUri);
    contents.put("scopes", scopes);
    if (session == null) {
      contents.put("session_enabled", false);
    } else {
      contents.put("session_enabled", session.isValid(authorizationRequest));
    }
    contents.put("custom_params", customParams);
    return new OAuthViewData(
        clientId,
        clientName,
        clientUri,
        logoUri,
        contacts,
        tosUri,
        policyUri,
        scopes,
        customParams,
        contents);
  }
}

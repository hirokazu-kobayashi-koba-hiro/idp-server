package org.idp.server.oauth.view;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.request.AuthorizationRequest;

public class OAuthViewDataCreator {

  AuthorizationRequest authorizationRequest;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;

  public OAuthViewDataCreator(
      AuthorizationRequest authorizationRequest,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    this.authorizationRequest = authorizationRequest;
    this.serverConfiguration = serverConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  public OAuthViewData create() {
    String clientId = authorizationRequest.clientId().value();
    String clientName = clientConfiguration.clientName();
    String clientUri = clientConfiguration.clientUri();
    String logoUri = clientConfiguration.logoUri();
    String contacts = clientConfiguration.contacts();
    String tosUri = clientConfiguration.tosUri();
    String policyUri = clientConfiguration.policyUri();
    List<String> scopes = authorizationRequest.scope().toStringList();
    Map<String, Object> contents = new HashMap<>();
    contents.put("client_id", clientId);
    contents.put("client_name", clientName);
    contents.put("client_uri", clientUri);
    contents.put("logo_uri", logoUri);
    contents.put("contacts", contacts);
    contents.put("tos_uri", tosUri);
    contents.put("policy_uri", policyUri);
    contents.put("scopes", scopes);
    return new OAuthViewData(
        clientId, clientName, clientUri, logoUri, contacts, tosUri, policyUri, scopes, contents);
  }
}

package org.idp.server.core.oauth.view;

import java.util.List;
import java.util.Map;

public class OAuthViewData {
  String clientId;
  String clientName;
  String clientUri;
  String logoUri;
  String contacts;
  String tosUri;
  String policyUri;
  List<String> scopes;
  Map<String, String> customParams;
  Map<String, Object> contents;

  public OAuthViewData(
      String clientId,
      String clientName,
      String clientUri,
      String logoUri,
      String contacts,
      String tosUri,
      String policyUri,
      List<String> scopes,
      Map<String, String> customParams,
      Map<String, Object> contents) {
    this.clientId = clientId;
    this.clientName = clientName;
    this.clientUri = clientUri;
    this.logoUri = logoUri;
    this.contacts = contacts;
    this.tosUri = tosUri;
    this.policyUri = policyUri;
    this.scopes = scopes;
    this.customParams = customParams;
    this.contents = contents;
  }

  public String clientId() {
    return clientId;
  }

  public String clientName() {
    return clientName;
  }

  public String clientUri() {
    return clientUri;
  }

  public String logoUri() {
    return logoUri;
  }

  public String contacts() {
    return contacts;
  }

  public String tosUri() {
    return tosUri;
  }

  public String policyUri() {
    return policyUri;
  }

  public List<String> scopes() {
    return scopes;
  }

  public Map<String, String> customParams() {
    return customParams;
  }

  public Map<String, Object> contents() {
    return contents;
  }
}

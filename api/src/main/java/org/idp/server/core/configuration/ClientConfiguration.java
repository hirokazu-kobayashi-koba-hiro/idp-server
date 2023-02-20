package org.idp.server.core.configuration;

import java.util.List;
import org.idp.server.basic.json.JsonReadable;

/** ClientConfiguration */
public class ClientConfiguration implements JsonReadable {
  String clientId;
  String redirectUris;
  String tokenEndpointAuthMethod;
  List<String> grantTypes;
  List<String> responseTypes;
  String clientName;
  String clientUri;
  String logoUri;
  String scope;
  String contacts;
  String tosUri;
  String policyUri;
  String jwksUri;
  String jwks;
  String softwareId;
  String softwareVersion;

  public ClientConfiguration() {}

  public String clientId() {
    return clientId;
  }

  public String redirectUris() {
    return redirectUris;
  }

  public String tokenEndpointAuthMethod() {
    return tokenEndpointAuthMethod;
  }

  public List<String> grantTypes() {
    return grantTypes;
  }

  public List<String> responseTypes() {
    return responseTypes;
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

  public String scope() {
    return scope;
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

  public String jwksUri() {
    return jwksUri;
  }

  public String jwks() {
    return jwks;
  }

  public String softwareId() {
    return softwareId;
  }

  public String softwareVersion() {
    return softwareVersion;
  }
}

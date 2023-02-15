package org.idp.server.core.configuration;

import java.util.List;

/** ClientConfiguration */
public class ClientConfiguration {
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

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getRedirectUris() {
    return redirectUris;
  }

  public void setRedirectUris(String redirectUris) {
    this.redirectUris = redirectUris;
  }

  public String getTokenEndpointAuthMethod() {
    return tokenEndpointAuthMethod;
  }

  public void setTokenEndpointAuthMethod(String tokenEndpointAuthMethod) {
    this.tokenEndpointAuthMethod = tokenEndpointAuthMethod;
  }

  public List<String> getGrantTypes() {
    return grantTypes;
  }

  public void setGrantTypes(List<String> grantTypes) {
    this.grantTypes = grantTypes;
  }

  public List<String> getResponseTypes() {
    return responseTypes;
  }

  public void setResponseTypes(List<String> responseTypes) {
    this.responseTypes = responseTypes;
  }

  public String getClientName() {
    return clientName;
  }

  public void setClientName(String clientName) {
    this.clientName = clientName;
  }

  public String getClientUri() {
    return clientUri;
  }

  public void setClientUri(String clientUri) {
    this.clientUri = clientUri;
  }

  public String getLogoUri() {
    return logoUri;
  }

  public void setLogoUri(String logoUri) {
    this.logoUri = logoUri;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  public String getContacts() {
    return contacts;
  }

  public void setContacts(String contacts) {
    this.contacts = contacts;
  }

  public String getTosUri() {
    return tosUri;
  }

  public void setTosUri(String tosUri) {
    this.tosUri = tosUri;
  }

  public String getPolicyUri() {
    return policyUri;
  }

  public void setPolicyUri(String policyUri) {
    this.policyUri = policyUri;
  }

  public String getJwksUri() {
    return jwksUri;
  }

  public void setJwksUri(String jwksUri) {
    this.jwksUri = jwksUri;
  }

  public String getJwks() {
    return jwks;
  }

  public void setJwks(String jwks) {
    this.jwks = jwks;
  }

  public String getSoftwareId() {
    return softwareId;
  }

  public void setSoftwareId(String softwareId) {
    this.softwareId = softwareId;
  }

  public String getSoftwareVersion() {
    return softwareVersion;
  }

  public void setSoftwareVersion(String softwareVersion) {
    this.softwareVersion = softwareVersion;
  }
}

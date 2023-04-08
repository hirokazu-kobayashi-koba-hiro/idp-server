package org.idp.server.core.configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.core.type.oauth.ClientId;
import org.idp.server.core.type.oauth.TokenIssuer;

/** ClientConfiguration */
public class ClientConfiguration implements JsonReadable {
  String clientId;
  String clientSecret;
  List<String> redirectUris;
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
  List<String> requestUris;

  // extension
  boolean supportedJar;
  String issuer;

  public ClientConfiguration() {}

  public ClientId clientId() {
    return new ClientId(clientId);
  }

  public String clientSecret() {
    return clientSecret;
  }

  public List<String> redirectUris() {
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

  public List<String> scopes() {
    if (Objects.isNull(scope)) {
      return List.of();
    }
    return Arrays.stream(scope.split(" ")).toList();
  }

  public List<String> filteredScope(String spacedScopes) {
    if (Objects.isNull(spacedScopes) || spacedScopes.isEmpty()) {
      return List.of();
    }
    List<String> scopes = Arrays.stream(spacedScopes.split(" ")).toList();
    return scopes.stream().filter(scope -> scopes().contains(scope)).toList();
  }

  public List<String> filteredScope(List<String> scopes) {
    return scopes.stream().filter(scope -> scopes().contains(scope)).toList();
  }

  public boolean isSupportedJar() {
    return supportedJar;
  }

  public boolean isRegisteredRequestUri(String requestUri) {
    return requestUris.contains(requestUri);
  }

  public TokenIssuer tokenIssuer() {
    return new TokenIssuer(issuer);
  }
}

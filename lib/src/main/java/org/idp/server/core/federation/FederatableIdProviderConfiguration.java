package org.idp.server.core.federation;

import org.idp.server.core.basic.json.JsonReadable;

import java.util.List;
import java.util.Objects;

public class FederatableIdProviderConfiguration implements JsonReadable {

  String identifier;
  String name;
  String description;
  String issuer;
  String clientId;
  String clientSecret;
  String redirectUri;
  List<String> scopesSupported;
  String authorizationEndpoint;
  String tokenEndpoint;
  String userinfoEndpoint;
  String jwksUri;

  public FederatableIdProviderConfiguration() {}

  public FederatableIdProviderConfiguration(
      String identifier,
      String name,
      String description,
      String issuer,
      String clientId,
      String clientSecret,
      String redirectUri,
      List<String> scopesSupported,
      String authorizationEndpoint,
      String tokenEndpoint,
      String userinfoEndpoint,
      String jwksUri) {
    this.identifier = identifier;
    this.name = name;
    this.description = description;
    this.issuer = issuer;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.redirectUri = redirectUri;
    this.scopesSupported = scopesSupported;
    this.authorizationEndpoint = authorizationEndpoint;
    this.tokenEndpoint = tokenEndpoint;
    this.userinfoEndpoint = userinfoEndpoint;
    this.jwksUri = jwksUri;
  }

  public String identifier() {
    return identifier;
  }

  public String name() {
    return name;
  }

  public String description() {
    return description;
  }

  public String issuer() {
    return issuer;
  }

  public String clientId() {
    return clientId;
  }

  public String clientSecret() {
    return clientSecret;
  }

  public String redirectUri() {
    return redirectUri;
  }

  public List<String> scopesSupported() {
    return scopesSupported;
  }

  public String scopeAsString() {
    return String.join(" ", scopesSupported);
  }

  public String authorizationEndpoint() {
    return authorizationEndpoint;
  }

  public String tokenEndpoint() {
    return tokenEndpoint;
  }

  public String userinfoEndpoint() {
    return userinfoEndpoint;
  }

  public String jwksUri() {
    return jwksUri;
  }

  public boolean exists() {
    return Objects.nonNull(identifier) && !identifier.isEmpty();
  }
}

package org.idp.server.core.federation.sso.oidc;

import java.util.List;
import java.util.Objects;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.core.federation.sso.SsoProvider;

public class OidcSsoConfiguration implements JsonReadable {

  String type;
  String provider;
  String issuer;
  String issuerName;
  String description;
  String clientId;
  String clientSecret;
  String clientAuthenticationType;
  String redirectUri;
  List<String> scopesSupported;
  String authorizationEndpoint;
  String tokenEndpoint;
  String userinfoEndpoint;
  String jwksUri;
  String paramsDelimiter;

  public OidcSsoConfiguration() {}

  public OidcSsoConfiguration(
      String type,
      String issuer,
      String issuerName,
      String description,
      String clientId,
      String clientSecret,
      String clientAuthenticationType,
      String redirectUri,
      List<String> scopesSupported,
      String authorizationEndpoint,
      String tokenEndpoint,
      String userinfoEndpoint,
      String jwksUri,
      String paramsDelimiter) {
    this.type = type;
    this.issuer = issuer;
    this.issuerName = issuerName;
    this.description = description;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.clientAuthenticationType = clientAuthenticationType;
    this.redirectUri = redirectUri;
    this.scopesSupported = scopesSupported;
    this.authorizationEndpoint = authorizationEndpoint;
    this.tokenEndpoint = tokenEndpoint;
    this.userinfoEndpoint = userinfoEndpoint;
    this.jwksUri = jwksUri;
    this.paramsDelimiter = paramsDelimiter;
  }

  public String type() {
    return type;
  }

  public String issuer() {
    return issuer;
  }

  public String issuerName() {
    return issuerName;
  }

  public String description() {
    return description;
  }

  public String clientId() {
    return clientId;
  }

  public String clientSecret() {
    return clientSecret;
  }

  public String clientAuthenticationType() {
    return clientAuthenticationType;
  }

  public String redirectUri() {
    return redirectUri;
  }

  public List<String> scopesSupported() {
    return scopesSupported;
  }

  public String scopeAsString() {

    if (Objects.nonNull(paramsDelimiter) && Objects.equals(paramsDelimiter, ",")) {
      return String.join(",", scopesSupported);
    }

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

  public SsoProvider ssoProvider() {
    return new SsoProvider(provider);
  }
}

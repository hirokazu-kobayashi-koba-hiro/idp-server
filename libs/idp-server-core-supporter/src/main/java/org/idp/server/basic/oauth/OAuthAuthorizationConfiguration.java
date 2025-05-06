package org.idp.server.basic.oauth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.JsonReadable;

public class OAuthAuthorizationConfiguration implements JsonReadable {

  String type;
  String tokenEndpoint;
  String clientAuthenticationType;
  String clientId;
  String clientSecret;
  String scope;
  String username;
  String password;

  public OAuthAuthorizationConfiguration() {}

  public OAuthAuthorizationConfiguration(String type, String tokenEndpoint, String clientAuthenticationType, String clientId, String clientSecret, String scope, String username, String password) {
    this.type = type;
    this.tokenEndpoint = tokenEndpoint;
    this.clientAuthenticationType = clientAuthenticationType;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.scope = scope;
    this.username = username;
    this.password = password;
  }

  public OAuthAuthorizationType type() {
    return OAuthAuthorizationType.of(type);
  }

  public String tokenEndpoint() {
    return tokenEndpoint;
  }

  public String clientAuthenticationType() {
    return clientAuthenticationType;
  }

  public String clientId() {
    return clientId;
  }

  public String clientSecret() {
    return clientSecret;
  }

  public String scope() {
    return scope;
  }

  public String username() {
    return username;
  }

  public String password() {
    return password;
  }

  public boolean exists() {
    return type != null && !type.isEmpty();
  }

  public boolean isClientSecretBasic() {
    return clientAuthenticationType != null && clientAuthenticationType.equals("client_secret_basic");
  }

  public boolean isClientSecretPost() {
    return clientAuthenticationType != null && clientAuthenticationType.equals("client_secret_post");
  }

  public boolean isClientCredentials() {
    return type() == OAuthAuthorizationType.CLIENT_CREDENTIALS;
  }

  public boolean isResourceOwnerPassword() {
    return type() == OAuthAuthorizationType.RESOURCE_OWNER_PASSWORD_CREDENTIALS;
  }

  public String basicAuthenticationValue() {
    String auth = clientId + ":" + clientSecret;
    String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    return "Basic " + encodedAuth;
  }

  public Map<String, String> toMap() {
    Map<String, String> map = new HashMap<>();

    map.put("client_id", clientId);

    if (isResourceOwnerPassword()) {
      map.put("username", username);
      map.put("password", password);
    }

    if (isClientSecretPost()) {
      map.put("client_secret", clientSecret);
    }

    map.put("scope", scope);
    map.put("grant_type", type);
    return map;
  }
}

package org.idp.server.configuration;

import java.util.*;
import java.util.stream.Collectors;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.type.ciba.BackchannelTokenDeliveryMode;
import org.idp.server.type.extension.RegisteredRedirectUris;
import org.idp.server.type.oauth.*;

/** ClientConfiguration */
public class ClientConfiguration implements JsonReadable {
  String clientId;
  String clientSecret;
  List<String> redirectUris = new ArrayList<>();
  String tokenEndpointAuthMethod;
  List<String> grantTypes = new ArrayList<>();
  List<String> responseTypes = new ArrayList<>();
  String clientName = "";
  String clientUri = "";
  String logoUri = "";
  String scope;
  String contacts = "";
  String tosUri = "";
  String policyUri = "";
  String jwksUri;
  String jwks;
  String softwareId = "";
  String softwareVersion = "";
  List<String> requestUris = new ArrayList<>();
  String backchannelTokenDeliveryMode = "";
  String backchannelClientNotificationEndpoint = "";
  String backchannelAuthenticationRequestSigningAlg = "";
  Boolean backchannelUserCodeParameter;
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

  public RegisteredRedirectUris registeredRedirectUris() {
    return new RegisteredRedirectUris(redirectUris);
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

  public Set<String> filteredScope(String spacedScopes) {
    if (Objects.isNull(spacedScopes) || spacedScopes.isEmpty()) {
      return Set.of();
    }
    List<String> scopes = Arrays.stream(spacedScopes.split(" ")).toList();
    return scopes.stream().filter(scope -> scopes().contains(scope)).collect(Collectors.toSet());
  }

  public Set<String> filteredScope(List<String> scopes) {
    return scopes.stream().filter(scope -> scopes().contains(scope)).collect(Collectors.toSet());
  }

  public boolean isSupportedJar() {
    return supportedJar;
  }

  public boolean isRegisteredRequestUri(String requestUri) {
    return requestUris.contains(requestUri);
  }

  public boolean isRegisteredRedirectUri(String redirectUri) {
    return redirectUris.contains(redirectUri);
  }

  public TokenIssuer tokenIssuer() {
    return new TokenIssuer(issuer);
  }

  public boolean isSupportedResponseType(ResponseType responseType) {
    return responseTypes.contains(responseType.value());
  }

  public boolean matchClientSecret(String that) {
    return clientSecret.equals(that);
  }

  public ClientAuthenticationType clientAuthenticationType() {
    return ClientAuthenticationType.valueOf(tokenEndpointAuthMethod);
  }

  public BackchannelTokenDeliveryMode backchannelTokenDeliveryMode() {
    return BackchannelTokenDeliveryMode.of(backchannelTokenDeliveryMode);
  }

  public boolean hasBackchannelTokenDeliveryMode() {
    return backchannelTokenDeliveryMode().isDefined();
  }

  public String backchannelClientNotificationEndpoint() {
    return backchannelClientNotificationEndpoint;
  }

  public boolean hasBackchannelClientNotificationEndpoint() {
    return !backchannelClientNotificationEndpoint.isEmpty();
  }

  public String backchannelAuthenticationRequestSigningAlg() {
    return backchannelAuthenticationRequestSigningAlg;
  }

  public boolean hasBackchannelAuthenticationRequestSigningAlg() {
    return !backchannelAuthenticationRequestSigningAlg.isEmpty();
  }

  public Boolean backchannelUserCodeParameter() {
    return backchannelUserCodeParameter;
  }

  public boolean hasBackchannelUserCodeParameter() {
    return Objects.nonNull(backchannelUserCodeParameter);
  }

  public boolean isSupportedGrantType(GrantType grantType) {
    return grantTypes.contains(grantType.value());
  }
}

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
  String applicationType = "web";
  String idTokenEncryptedResponseAlg;
  String idTokenEncryptedResponseEnc;
  List<String> authorizationDetailsTypes = new ArrayList<>();
  String tlsClientAuthSubjectDn;
  String tlsClientAuthSanDns;
  String tlsClientAuthSanUri;
  String tlsClientAuthSanIp;
  String tlsClientAuthSanEmail;
  boolean tlsClientCertificateBoundAccessTokens = false;
  String authorizationSignedResponseAlg;
  String authorizationEncryptedResponseAlg;
  String authorizationEncryptedResponseEnc;
  // extension
  boolean supportedJar;
  String issuer;

  public ClientConfiguration() {}

  public ClientId clientId() {
    return new ClientId(clientId);
  }

  public String clientIdValue() {
    return clientId;
  }

  public String clientSecretValue() {
    return clientSecret;
  }

  public ClientSecret clientSecret() {
    return new ClientSecret(clientSecret);
  }

  public List<String> redirectUris() {
    return redirectUris;
  }

  public boolean hasRedirectUri() {
    return !redirectUris.isEmpty();
  }

  public boolean isMultiRegisteredRedirectUri() {
    return redirectUris.size() >= 2;
  }

  public RedirectUri getFirstRedirectUri() {
    return new RedirectUri(redirectUris.get(0));
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

  public boolean isWebApplication() {
    return applicationType.equals("web");
  }

  public String idTokenEncryptedResponseAlg() {
    return idTokenEncryptedResponseAlg;
  }

  public String idTokenEncryptedResponseEnc() {
    return idTokenEncryptedResponseEnc;
  }

  public boolean hasEncryptedIdTokenMeta() {
    return Objects.nonNull(idTokenEncryptedResponseAlg)
        && Objects.nonNull(idTokenEncryptedResponseEnc);
  }

  public List<String> authorizationDetailsTypes() {
    return authorizationDetailsTypes;
  }

  public boolean isAuthorizedAuthorizationDetailsType(String type) {
    return authorizationDetailsTypes.contains(type);
  }

  public String tlsClientAuthSubjectDn() {
    return tlsClientAuthSubjectDn;
  }

  public String tlsClientAuthSanDns() {
    return tlsClientAuthSanDns;
  }

  public String tlsClientAuthSanUri() {
    return tlsClientAuthSanUri;
  }

  public String tlsClientAuthSanIp() {
    return tlsClientAuthSanIp;
  }

  public String tlsClientAuthSanEmail() {
    return tlsClientAuthSanEmail;
  }

  public boolean isTlsClientCertificateBoundAccessTokens() {
    return tlsClientCertificateBoundAccessTokens;
  }

  public String authorizationSignedResponseAlg() {
    return authorizationSignedResponseAlg;
  }

  public boolean hasAuthorizationSignedResponseAlg() {
    return Objects.nonNull(authorizationSignedResponseAlg);
  }

  public String authorizationEncryptedResponseAlg() {
    return authorizationEncryptedResponseAlg;
  }

  public String authorizationEncryptedResponseEnc() {
    return authorizationEncryptedResponseEnc;
  }

  public boolean hasEncryptedAuthorizationResponseMeta() {
    return Objects.nonNull(idTokenEncryptedResponseAlg)
        && Objects.nonNull(idTokenEncryptedResponseEnc);
  }
}

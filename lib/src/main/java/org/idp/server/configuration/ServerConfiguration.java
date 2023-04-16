package org.idp.server.configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.type.oauth.ResponseType;
import org.idp.server.type.oauth.TokenIssuer;

/** ServerConfiguration */
public class ServerConfiguration implements JsonReadable {
  String issuer = "";
  String authorizationEndpoint = "";
  String tokenEndpoint = "";
  String userinfoEndpoint = "";
  String jwks = "";
  String jwksUri = "";
  List<String> scopesSupported = new ArrayList<>();
  List<String> responseTypesSupported = new ArrayList<>();
  List<String> responseModesSupported = new ArrayList<>();
  List<String> grantTypesSupported = new ArrayList<>();
  List<String> acrValuesSupported = new ArrayList<>();
  List<String> subjectTypesSupported = new ArrayList<>();
  List<String> idTokenSigningAlgValuesSupported = new ArrayList<>();
  List<String> idTokenEncryptionAlgValuesSupported = new ArrayList<>();
  List<String> idTokenEncryptionEncValuesSupported = new ArrayList<>();
  List<String> userinfoSigningAlgValuesSupported = new ArrayList<>();
  List<String> userinfoEncryptionAlgValuesSupported = new ArrayList<>();
  List<String> userinfoEncryptionEncValuesSupported = new ArrayList<>();
  List<String> requestObjectSigningAlgValuesSupported = new ArrayList<>();
  List<String> requestObjectEncryptionAlgValuesSupported = new ArrayList<>();
  List<String> requestObjectEncryptionEncValuesSupported = new ArrayList<>();
  List<String> authorizationSigningAlgValuesSupported = new ArrayList<>();
  List<String> authorizationEncryptionAlgValuesSupported = new ArrayList<>();
  List<String> authorizationEncryptionEncValuesSupported = new ArrayList<>();
  List<String> tokenEndpointAuthMethodsSupported = new ArrayList<>();
  List<String> tokenEndpointAuthSigningAlgValuesSupported = new ArrayList<>();
  List<String> displayValuesSupported = new ArrayList<>();
  List<String> claimTypesSupported = new ArrayList<>();
  List<String> claimsSupported = new ArrayList<>();
  boolean claimsParameterSupported = true;
  boolean requestParameterSupported = true;
  boolean requestUriParameterSupported = true;
  boolean requireRequestUriRegistration = true;
  String revocationEndpoint = "";
  List<String> revocationEndpointAuthMethodsSupported = new ArrayList<>();
  List<String> revocationEndpointAuthSigningAlgValuesSupported = new ArrayList<>();
  String introspectionEndpoint = "";
  List<String> introspectionEndpointAuthMethodsSupported = new ArrayList<>();
  List<String> introspectionEndpointAuthSigningAlgValuesSupported = new ArrayList<>();
  List<String> codeChallengeMethodsSupported = new ArrayList<>();
  boolean tlsClientCertificateBoundAccessTokens = false;
  boolean requireSignedRequestObject = false;
  boolean authorizationResponseIssParameterSupported = false;

  // extension
  List<String> fapiBaselineScopes = new ArrayList<>();
  List<String> fapiAdvanceScopes = new ArrayList<>();
  int authorizationCodeValidDuration = 600;
  String tokenSignedKeyId = "";
  String idTokenSignedKeyId = "";
  long accessTokenDuration = 3600;

  public ServerConfiguration() {}

  public TokenIssuer issuer() {
    return new TokenIssuer(issuer);
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

  public String jwks() {
    return jwks;
  }

  public String jwksUri() {
    return jwksUri;
  }

  public List<String> scopesSupported() {
    return scopesSupported;
  }

  public List<String> responseTypesSupported() {
    return responseTypesSupported;
  }

  public List<String> responseModesSupported() {
    return responseModesSupported;
  }

  public List<String> grantTypesSupported() {
    return grantTypesSupported;
  }

  public List<String> acrValuesSupported() {
    return acrValuesSupported;
  }

  public List<String> subjectTypesSupported() {
    return subjectTypesSupported;
  }

  public List<String> idTokenSigningAlgValuesSupported() {
    return idTokenSigningAlgValuesSupported;
  }

  public List<String> idTokenEncryptionAlgValuesSupported() {
    return idTokenEncryptionAlgValuesSupported;
  }

  public List<String> idTokenEncryptionEncValuesSupported() {
    return idTokenEncryptionEncValuesSupported;
  }

  public List<String> userinfoSigningAlgValuesSupported() {
    return userinfoSigningAlgValuesSupported;
  }

  public List<String> userinfoEncryptionAlgValuesSupported() {
    return userinfoEncryptionAlgValuesSupported;
  }

  public List<String> userinfoEncryptionEncValuesSupported() {
    return userinfoEncryptionEncValuesSupported;
  }

  public List<String> requestObjectSigningAlgValuesSupported() {
    return requestObjectSigningAlgValuesSupported;
  }

  public List<String> requestObjectEncryptionAlgValuesSupported() {
    return requestObjectEncryptionAlgValuesSupported;
  }

  public List<String> requestObjectEncryptionEncValuesSupported() {
    return requestObjectEncryptionEncValuesSupported;
  }

  public List<String> authorizationSigningAlgValuesSupported() {
    return authorizationSigningAlgValuesSupported;
  }

  public List<String> authorizationEncryptionAlgValuesSupported() {
    return authorizationEncryptionAlgValuesSupported;
  }

  public List<String> authorizationEncryptionEncValuesSupported() {
    return authorizationEncryptionEncValuesSupported;
  }

  public List<String> tokenEndpointAuthMethodsSupported() {
    return tokenEndpointAuthMethodsSupported;
  }

  public List<String> tokenEndpointAuthSigningAlgValuesSupported() {
    return tokenEndpointAuthSigningAlgValuesSupported;
  }

  public List<String> displayValuesSupported() {
    return displayValuesSupported;
  }

  public List<String> claimTypesSupported() {
    return claimTypesSupported;
  }

  public List<String> claimsSupported() {
    return claimsSupported;
  }

  public boolean claimsParameterSupported() {
    return claimsParameterSupported;
  }

  public boolean requestParameterSupported() {
    return requestParameterSupported;
  }

  public boolean requestUriParameterSupported() {
    return requestUriParameterSupported;
  }

  public boolean requireRequestUriRegistration() {
    return requireRequestUriRegistration;
  }

  public String revocationEndpoint() {
    return revocationEndpoint;
  }

  public List<String> revocationEndpointAuthMethodsSupported() {
    return revocationEndpointAuthMethodsSupported;
  }

  public List<String> revocationEndpointAuthSigningAlgValuesSupported() {
    return revocationEndpointAuthSigningAlgValuesSupported;
  }

  public String introspectionEndpoint() {
    return introspectionEndpoint;
  }

  public List<String> introspectionEndpointAuthMethodsSupported() {
    return introspectionEndpointAuthMethodsSupported;
  }

  public List<String> introspectionEndpointAuthSigningAlgValuesSupported() {
    return introspectionEndpointAuthSigningAlgValuesSupported;
  }

  public List<String> codeChallengeMethodsSupported() {
    return codeChallengeMethodsSupported;
  }

  public boolean tlsClientCertificateBoundAccessTokens() {
    return tlsClientCertificateBoundAccessTokens;
  }

  public boolean requireSignedRequestObject() {
    return requireSignedRequestObject;
  }

  public boolean authorizationResponseIssParameterSupported() {
    return authorizationResponseIssParameterSupported;
  }

  public List<String> filteredScope(String spacedScopes) {
    List<String> scopes = Arrays.stream(spacedScopes.split(" ")).toList();
    return scopes.stream().filter(scope -> scopesSupported.contains(scope)).toList();
  }

  public List<String> filteredScope(List<String> scopes) {
    return scopes.stream().filter(scope -> scopesSupported.contains(scope)).toList();
  }

  public boolean hasFapiBaselineScope(Set<String> scopes) {
    return scopes.stream().anyMatch(scope -> fapiBaselineScopes.contains(scope));
  }

  public boolean hasFapiAdvanceScope(Set<String> scopes) {
    return scopes.stream().anyMatch(scope -> fapiAdvanceScopes.contains(scope));
  }

  public int authorizationCodeValidDuration() {
    return authorizationCodeValidDuration;
  }

  public String tokenSignedKeyId() {
    return tokenSignedKeyId;
  }

  public String idTokenSignedKeyId() {
    return idTokenSignedKeyId;
  }

  public long accessTokenDuration() {
    return accessTokenDuration;
  }

  public boolean isSupportedResponseType(ResponseType responseType) {
    return responseTypesSupported.contains(responseType.name());
  }
}

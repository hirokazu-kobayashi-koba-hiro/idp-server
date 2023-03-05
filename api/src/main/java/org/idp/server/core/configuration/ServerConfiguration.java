package org.idp.server.core.configuration;

import java.util.Arrays;
import java.util.List;
import org.idp.server.basic.json.JsonReadable;

/** ServerConfiguration */
public class ServerConfiguration implements JsonReadable {
  String issuer;
  String authorizationEndpoint;
  String tokenEndpoint;
  String userinfoEndpoint;
  String jwks;
  String jwksUri;
  List<String> scopesSupported;
  List<String> responseTypesSupported;
  List<String> responseModesSupported;
  List<String> grantTypesSupported;
  List<String> acrValuesSupported;
  List<String> subjectTypesSupported;
  List<String> idTokenSigningAlgValuesSupported;
  List<String> idTokenEncryptionAlgValuesSupported;
  List<String> idTokenEncryptionEncValuesSupported;
  List<String> userinfoSigningAlgValuesSupported;
  List<String> userinfoEncryptionAlgValuesSupported;
  List<String> userinfoEncryptionEncValuesSupported;
  List<String> requestObjectSigningAlgValuesSupported;
  List<String> requestObjectEncryptionAlgValuesSupported;
  List<String> requestObjectEncryptionEncValuesSupported;
  List<String> authorizationSigningAlgValuesSupported;
  List<String> authorizationEncryptionAlgValuesSupported;
  List<String> authorizationEncryptionEncValuesSupported;
  List<String> tokenEndpointAuthMethodsSupported;
  List<String> tokenEndpointAuthSigningAlgValuesSupported;
  List<String> displayValuesSupported;
  List<String> claimTypesSupported;
  List<String> claimsSupported;
  boolean claimsParameterSupported;
  boolean requestParameterSupported;
  boolean requestUriParameterSupported;
  boolean requireRequestUriRegistration;
  String revocationEndpoint;
  List<String> revocationEndpointAuthMethodsSupported;
  List<String> revocationEndpointAuthSigningAlgValuesSupported;
  String introspectionEndpoint;
  List<String> introspectionEndpointAuthMethodsSupported;
  List<String> introspectionEndpointAuthSigningAlgValuesSupported;
  List<String> codeChallengeMethodsSupported;
  boolean tlsClientCertificateBoundAccessTokens;
  boolean requireSignedRequestObject;
  boolean authorizationResponseIssParameterSupported;

  // extension
  List<String> fapiBaselineScopes;
  List<String> fapiAdvanceScopes;

  public ServerConfiguration() {}

  public String issuer() {
    return issuer;
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

  public boolean hasFapiBaselineScope(List<String> scopes) {
    return scopes.stream().anyMatch(scope -> fapiBaselineScopes.contains(scope));
  }

  public boolean hasFapiAdvanceScope(List<String> scopes) {
    return scopes.stream().anyMatch(scope -> fapiAdvanceScopes.contains(scope));
  }
}

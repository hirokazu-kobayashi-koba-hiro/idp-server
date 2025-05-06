package org.idp.server.core.oidc.configuration;

import java.util.*;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.basic.type.oauth.GrantType;
import org.idp.server.basic.type.oauth.ResponseType;
import org.idp.server.basic.type.oauth.TokenIssuer;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

/** ServerConfiguration */
public class ServerConfiguration implements JsonReadable {
  String tenantId;
  String issuer;
  String authorizationEndpoint;
  String tokenEndpoint = "";
  String userinfoEndpoint = "";
  String jwks;
  String jwksUri;
  String registrationEndpoint = "";
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
  List<String> backchannelTokenDeliveryModesSupported = new ArrayList<>();
  String backchannelAuthenticationEndpoint = "";
  List<String> backchannelAuthenticationRequestSigningAlgValuesSupported = new ArrayList<>();
  Boolean backchannelUserCodeParameterSupported;
  List<String> authorizationDetailsTypesSupported = new ArrayList<>();
  VerifiableCredentialConfiguration credentialIssuerMetadata = new VerifiableCredentialConfiguration();
  // extension
  List<String> fapiBaselineScopes = new ArrayList<>();
  List<String> fapiAdvanceScopes = new ArrayList<>();
  int authorizationCodeValidDuration = 600;
  String tokenSignedKeyId = "";
  String idTokenSignedKeyId = "";
  long accessTokenDuration = 1800;
  long refreshTokenDuration = 3600;
  long idTokenDuration = 3600;
  boolean idTokenStrictMode = false;
  long defaultMaxAge = 86400;
  long authorizationResponseDuration = 60;
  int backchannelAuthRequestExpiresIn = 300;
  int backchannelAuthPollingInterval = 5;
  int oauthAuthorizationRequestExpiresIn = 1800;
  List<String> availableAuthenticationMethods = new ArrayList<>(List.of("password"));

  public ServerConfiguration() {}

  public String tenantId() {
    return tenantId;
  }

  // TODO
  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public TenantIdentifier tenantIdentifier() {
    return new TenantIdentifier(tenantId);
  }

  public TokenIssuer tokenIssuer() {
    return new TokenIssuer(issuer);
  }

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

  public String registrationEndpoint() {
    return registrationEndpoint;
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

  public boolean isSupportedClientAuthenticationType(String value) {
    return tokenEndpointAuthMethodsSupported.contains(value);
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

  public boolean isTlsClientCertificateBoundAccessTokens() {
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

  public long refreshTokenDuration() {
    return refreshTokenDuration;
  }

  public long idTokenDuration() {
    return idTokenDuration;
  }

  public boolean isIdTokenStrictMode() {
    return idTokenStrictMode;
  }

  public boolean hasTokenEndpoint() {
    return Objects.nonNull(tokenEndpoint) && !tokenEndpoint.isEmpty();
  }

  public boolean hasUserinfoEndpoint() {
    return Objects.nonNull(userinfoEndpoint) && !userinfoEndpoint.isEmpty();
  }

  public boolean hasRegistrationEndpoint() {
    return Objects.nonNull(revocationEndpoint) && !registrationEndpoint.isEmpty();
  }

  public boolean isSupportedResponseType(ResponseType responseType) {
    return responseTypesSupported.contains(responseType.value());
  }

  public boolean hasScopesSupported() {
    return !scopesSupported.isEmpty();
  }

  public boolean hasResponseTypesSupported() {
    return !responseTypesSupported.isEmpty();
  }

  public boolean hasResponseModesSupported() {
    return !responseModesSupported.isEmpty();
  }

  public boolean hasGrantTypesSupported() {
    return !grantTypesSupported.isEmpty();
  }

  public boolean hasAcrValuesSupported() {
    return !acrValuesSupported.isEmpty();
  }

  public boolean hasSubjectTypesSupported() {
    return !subjectTypesSupported.isEmpty();
  }

  public boolean hasIdTokenSigningAlgValuesSupported() {
    return !idTokenSigningAlgValuesSupported.isEmpty();
  }

  public boolean hasIdTokenEncryptionAlgValuesSupported() {
    return !idTokenEncryptionAlgValuesSupported.isEmpty();
  }

  public boolean hasIdTokenEncryptionEncValuesSupported() {
    return !idTokenEncryptionEncValuesSupported.isEmpty();
  }

  public boolean hasUserinfoSigningAlgValuesSupported() {
    return !userinfoSigningAlgValuesSupported.isEmpty();
  }

  public boolean hasUserinfoEncryptionAlgValuesSupported() {
    return !userinfoEncryptionAlgValuesSupported.isEmpty();
  }

  public boolean hasUserinfoEncryptionEncValuesSupported() {
    return !userinfoEncryptionEncValuesSupported.isEmpty();
  }

  public boolean hasRequestObjectSigningAlgValuesSupported() {
    return !requestObjectSigningAlgValuesSupported.isEmpty();
  }

  public boolean hasRequestObjectEncryptionAlgValuesSupported() {
    return !requestObjectEncryptionAlgValuesSupported.isEmpty();
  }

  public boolean hasRequestObjectEncryptionEncValuesSupported() {
    return !requestObjectEncryptionEncValuesSupported.isEmpty();
  }

  public boolean hasAuthorizationSigningAlgValuesSupported() {
    return !authorizationSigningAlgValuesSupported.isEmpty();
  }

  public boolean hasAuthorizationEncryptionAlgValuesSupported() {
    return !authorizationEncryptionAlgValuesSupported.isEmpty();
  }

  public boolean hasAuthorizationEncryptionEncValuesSupported() {
    return !authorizationEncryptionEncValuesSupported.isEmpty();
  }

  public boolean hasTokenEndpointAuthMethodsSupported() {
    return !tokenEndpointAuthMethodsSupported.isEmpty();
  }

  public boolean hasTokenEndpointAuthSigningAlgValuesSupported() {
    return !tokenEndpointAuthSigningAlgValuesSupported.isEmpty();
  }

  public boolean hasDisplayValuesSupported() {
    return !displayValuesSupported.isEmpty();
  }

  public boolean hasClaimTypesSupported() {
    return !claimTypesSupported.isEmpty();
  }

  public boolean hasClaimsSupported() {
    return !claimsSupported.isEmpty();
  }

  public boolean hasRevocationEndpoint() {
    return Objects.nonNull(revocationEndpoint) && !revocationEndpoint.isEmpty();
  }

  public boolean hasRevocationEndpointAuthMethodsSupported() {
    return !revocationEndpointAuthMethodsSupported.isEmpty();
  }

  public boolean hasRevocationEndpointAuthSigningAlgValuesSupported() {
    return !revocationEndpointAuthSigningAlgValuesSupported.isEmpty();
  }

  public boolean hasIntrospectionEndpoint() {
    return Objects.nonNull(introspectionEndpoint) && !introspectionEndpoint.isEmpty();
  }

  public boolean hasIntrospectionEndpointAuthMethodsSupported() {
    return !introspectionEndpointAuthMethodsSupported.isEmpty();
  }

  public boolean hasIntrospectionEndpointAuthSigningAlgValuesSupported() {
    return !introspectionEndpointAuthSigningAlgValuesSupported.isEmpty();
  }

  public boolean hasCodeChallengeMethodsSupported() {
    return !codeChallengeMethodsSupported.isEmpty();
  }

  public List<String> backchannelTokenDeliveryModesSupported() {
    return backchannelTokenDeliveryModesSupported;
  }

  public boolean hasBackchannelTokenDeliveryModesSupported() {
    return !backchannelTokenDeliveryModesSupported.isEmpty();
  }

  public String backchannelAuthenticationEndpoint() {
    return backchannelAuthenticationEndpoint;
  }

  public boolean hasBackchannelAuthenticationEndpoint() {
    return Objects.nonNull(backchannelAuthenticationEndpoint) && !backchannelAuthenticationEndpoint.isEmpty();
  }

  public List<String> backchannelAuthenticationRequestSigningAlgValuesSupported() {
    return backchannelAuthenticationRequestSigningAlgValuesSupported;
  }

  public boolean hasBackchannelAuthenticationRequestSigningAlgValuesSupported() {
    return !backchannelAuthenticationRequestSigningAlgValuesSupported.isEmpty();
  }

  public boolean backchannelUserCodeParameterSupported() {
    return backchannelUserCodeParameterSupported;
  }

  public boolean hasBackchannelUserCodeParameterSupported() {
    return Objects.nonNull(backchannelUserCodeParameterSupported);
  }

  public boolean isSupportedGrantType(GrantType grantType) {
    return grantTypesSupported.contains(grantType.value());
  }

  public long defaultMaxAge() {
    return defaultMaxAge;
  }

  public List<String> authorizationDetailsTypesSupported() {
    return authorizationDetailsTypesSupported;
  }

  public boolean isSupportedAuthorizationDetailsType(String type) {
    return authorizationDetailsTypesSupported.contains(type);
  }

  public long authorizationResponseDuration() {
    return authorizationResponseDuration;
  }

  public boolean hasKey(String algorithm) {
    return jwks.contains(algorithm);
  }

  public VerifiableCredentialConfiguration credentialIssuerMetadata() {
    return credentialIssuerMetadata;
  }

  public boolean hasCredentialIssuerMetadata() {
    return credentialIssuerMetadata.exists();
  }

  public int backchannelAuthRequestExpiresIn() {
    return backchannelAuthRequestExpiresIn;
  }

  public int backchannelAuthPollingInterval() {
    return backchannelAuthPollingInterval;
  }

  public int oauthAuthorizationRequestExpiresIn() {
    return oauthAuthorizationRequestExpiresIn;
  }

  public List<String> availableAuthenticationMethods() {
    return availableAuthenticationMethods;
  }
}

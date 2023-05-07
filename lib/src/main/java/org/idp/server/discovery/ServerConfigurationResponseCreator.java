package org.idp.server.discovery;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.configuration.ServerConfiguration;

public class ServerConfigurationResponseCreator {
  ServerConfiguration serverConfiguration;

  public ServerConfigurationResponseCreator(ServerConfiguration serverConfiguration) {
    this.serverConfiguration = serverConfiguration;
  }

  public Map<String, Object> create() {
    Map<String, Object> map = new HashMap<>();
    map.put("issuer", serverConfiguration.issuer());
    map.put("authorization_endpoint", serverConfiguration.authorizationEndpoint());
    if (serverConfiguration.hasTokenEndpoint()) {
      map.put("token_endpoint", serverConfiguration.tokenEndpoint());
    }
    if (serverConfiguration.hasUserinfoEndpoint()) {
      map.put("userinfo_endpoint", serverConfiguration.userinfoEndpoint());
    }
    if (serverConfiguration.hasRegistrationEndpoint()) {
      map.put("registration_endpoint", serverConfiguration.registrationEndpoint());
    }
    map.put("jwks_uri", serverConfiguration.jwksUri());
    if (serverConfiguration.hasScopesSupported()) {
      map.put("scopes_supported", serverConfiguration.scopesSupported());
    }
    map.put("response_types_supported", serverConfiguration.responseTypesSupported());
    if (serverConfiguration.hasResponseModesSupported()) {
      map.put("response_modes_supported", serverConfiguration.responseModesSupported());
    }
    if (serverConfiguration.hasGrantTypesSupported()) {
      map.put("grant_types_supported", serverConfiguration.grantTypesSupported());
    }
    if (serverConfiguration.hasAcrValuesSupported()) {
      map.put("acr_values_supported", serverConfiguration.acrValuesSupported());
    }
    map.put("subject_types_supported", serverConfiguration.subjectTypesSupported());
    map.put(
        "id_token_signing_alg_values_supported",
        serverConfiguration.idTokenSigningAlgValuesSupported());
    if (serverConfiguration.hasIdTokenEncryptionEncValuesSupported()) {
      map.put(
          "id_token_encryption_alg_values_supported",
          serverConfiguration.idTokenEncryptionEncValuesSupported());
    }
    if (serverConfiguration.hasIdTokenEncryptionEncValuesSupported()) {
      map.put(
          "id_token_encryption_enc_values_supported",
          serverConfiguration.idTokenEncryptionEncValuesSupported());
    }
    if (serverConfiguration.hasUserinfoSigningAlgValuesSupported()) {
      map.put(
          "userinfo_signing_alg_values_supported",
          serverConfiguration.userinfoSigningAlgValuesSupported());
    }
    if (serverConfiguration.hasUserinfoEncryptionAlgValuesSupported()) {
      map.put(
          "userinfo_encryption_alg_values_supported",
          serverConfiguration.userinfoEncryptionAlgValuesSupported());
    }
    if (serverConfiguration.hasUserinfoEncryptionEncValuesSupported()) {
      map.put(
          "userinfo_encryption_enc_values_supported",
          serverConfiguration.userinfoEncryptionEncValuesSupported());
    }
    if (serverConfiguration.hasRequestObjectSigningAlgValuesSupported()) {
      map.put(
          "request_object_signing_alg_values_supported",
          serverConfiguration.requestObjectSigningAlgValuesSupported());
    }
    if (serverConfiguration.hasRequestObjectEncryptionAlgValuesSupported()) {
      map.put(
          "request_object_encryption_alg_values_supported",
          serverConfiguration.requestObjectEncryptionAlgValuesSupported());
    }
    if (serverConfiguration.hasRequestObjectEncryptionEncValuesSupported()) {
      map.put("", serverConfiguration.requestObjectEncryptionEncValuesSupported());
    }
    if (serverConfiguration.hasTokenEndpointAuthMethodsSupported()) {
      map.put(
          "token_endpoint_auth_methods_supported",
          serverConfiguration.tokenEndpointAuthMethodsSupported());
    }
    if (serverConfiguration.hasTokenEndpointAuthSigningAlgValuesSupported()) {
      map.put(
          "token_endpoint_auth_signing_alg_values_supported",
          serverConfiguration.tokenEndpointAuthMethodsSupported());
    }
    if (serverConfiguration.hasDisplayValuesSupported()) {
      map.put("display_values_supported", serverConfiguration.displayValuesSupported());
    }
    if (serverConfiguration.hasClaimTypesSupported()) {
      map.put("claim_types_supported", serverConfiguration.claimTypesSupported());
    }
    if (serverConfiguration.hasClaimsSupported()) {
      map.put("claims_supported", serverConfiguration.claimsSupported());
    }
    map.put("claims_parameter_supported", serverConfiguration.claimsParameterSupported());
    map.put("request_parameter_supported", serverConfiguration.requestParameterSupported());
    map.put("request_uri_parameter_supported", serverConfiguration.requestUriParameterSupported());
    map.put(
        "require_request_uri_registration", serverConfiguration.requireRequestUriRegistration());

    if (serverConfiguration.hasBackchannelTokenDeliveryModesSupported()) {
      map.put("backchannel_token_delivery_modes_supported", serverConfiguration.backchannelTokenDeliveryModesSupported());
    }
    if (serverConfiguration.hasBackchannelAuthenticationEndpoint()) {
      map.put("backchannel_authentication_endpoint", serverConfiguration.hasBackchannelAuthenticationEndpoint());
    }
    if (serverConfiguration.hasBackchannelAuthenticationRequestSigningAlgValuesSupported()) {
      map.put("backchannel_authentication_request_signing_alg_values_supported", serverConfiguration.backchannelAuthenticationRequestSigningAlgValuesSupported());
    }
    if (serverConfiguration.hasBackchannelUserCodeParameterSupported()) {
      map.put("backchannel_user_code_parameter_supported", serverConfiguration.backchannelUserCodeParameterSupported());
    }
    return map;
  }
}

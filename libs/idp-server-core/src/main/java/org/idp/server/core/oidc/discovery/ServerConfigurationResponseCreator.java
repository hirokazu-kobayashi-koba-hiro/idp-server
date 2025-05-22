/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.discovery;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;

public class ServerConfigurationResponseCreator {
  AuthorizationServerConfiguration authorizationServerConfiguration;

  public ServerConfigurationResponseCreator(
      AuthorizationServerConfiguration authorizationServerConfiguration) {
    this.authorizationServerConfiguration = authorizationServerConfiguration;
  }

  public Map<String, Object> create() {
    Map<String, Object> map = new HashMap<>();
    map.put("issuer", authorizationServerConfiguration.issuer());
    map.put("authorization_endpoint", authorizationServerConfiguration.authorizationEndpoint());
    if (authorizationServerConfiguration.hasTokenEndpoint()) {
      map.put("token_endpoint", authorizationServerConfiguration.tokenEndpoint());
    }
    if (authorizationServerConfiguration.hasUserinfoEndpoint()) {
      map.put("userinfo_endpoint", authorizationServerConfiguration.userinfoEndpoint());
    }
    if (authorizationServerConfiguration.hasRegistrationEndpoint()) {
      map.put("registration_endpoint", authorizationServerConfiguration.registrationEndpoint());
    }
    map.put("jwks_uri", authorizationServerConfiguration.jwksUri());
    if (authorizationServerConfiguration.hasScopesSupported()) {
      map.put("scopes_supported", authorizationServerConfiguration.scopesSupported());
    }
    map.put("response_types_supported", authorizationServerConfiguration.responseTypesSupported());
    if (authorizationServerConfiguration.hasResponseModesSupported()) {
      map.put(
          "response_modes_supported", authorizationServerConfiguration.responseModesSupported());
    }
    if (authorizationServerConfiguration.hasGrantTypesSupported()) {
      map.put("grant_types_supported", authorizationServerConfiguration.grantTypesSupported());
    }
    if (authorizationServerConfiguration.hasAcrValuesSupported()) {
      map.put("acr_values_supported", authorizationServerConfiguration.acrValuesSupported());
    }
    map.put("subject_types_supported", authorizationServerConfiguration.subjectTypesSupported());
    map.put(
        "id_token_signing_alg_values_supported",
        authorizationServerConfiguration.idTokenSigningAlgValuesSupported());
    if (authorizationServerConfiguration.hasIdTokenEncryptionEncValuesSupported()) {
      map.put(
          "id_token_encryption_alg_values_supported",
          authorizationServerConfiguration.idTokenEncryptionEncValuesSupported());
    }
    if (authorizationServerConfiguration.hasIdTokenEncryptionEncValuesSupported()) {
      map.put(
          "id_token_encryption_enc_values_supported",
          authorizationServerConfiguration.idTokenEncryptionEncValuesSupported());
    }
    if (authorizationServerConfiguration.hasUserinfoSigningAlgValuesSupported()) {
      map.put(
          "userinfo_signing_alg_values_supported",
          authorizationServerConfiguration.userinfoSigningAlgValuesSupported());
    }
    if (authorizationServerConfiguration.hasUserinfoEncryptionAlgValuesSupported()) {
      map.put(
          "userinfo_encryption_alg_values_supported",
          authorizationServerConfiguration.userinfoEncryptionAlgValuesSupported());
    }
    if (authorizationServerConfiguration.hasUserinfoEncryptionEncValuesSupported()) {
      map.put(
          "userinfo_encryption_enc_values_supported",
          authorizationServerConfiguration.userinfoEncryptionEncValuesSupported());
    }
    if (authorizationServerConfiguration.hasRequestObjectSigningAlgValuesSupported()) {
      map.put(
          "request_object_signing_alg_values_supported",
          authorizationServerConfiguration.requestObjectSigningAlgValuesSupported());
    }
    if (authorizationServerConfiguration.hasRequestObjectEncryptionAlgValuesSupported()) {
      map.put(
          "request_object_encryption_alg_values_supported",
          authorizationServerConfiguration.requestObjectEncryptionAlgValuesSupported());
    }
    if (authorizationServerConfiguration.hasRequestObjectEncryptionEncValuesSupported()) {
      map.put(
          "request_object_encryption_enc_values_supported",
          authorizationServerConfiguration.requestObjectEncryptionEncValuesSupported());
    }
    if (authorizationServerConfiguration.hasTokenEndpointAuthMethodsSupported()) {
      map.put(
          "token_endpoint_auth_methods_supported",
          authorizationServerConfiguration.tokenEndpointAuthMethodsSupported());
    }
    if (authorizationServerConfiguration.hasTokenEndpointAuthSigningAlgValuesSupported()) {
      map.put(
          "token_endpoint_auth_signing_alg_values_supported",
          authorizationServerConfiguration.tokenEndpointAuthMethodsSupported());
    }
    if (authorizationServerConfiguration.hasDisplayValuesSupported()) {
      map.put(
          "display_values_supported", authorizationServerConfiguration.displayValuesSupported());
    }
    if (authorizationServerConfiguration.hasClaimTypesSupported()) {
      map.put("claim_types_supported", authorizationServerConfiguration.claimTypesSupported());
    }
    if (authorizationServerConfiguration.hasClaimsSupported()) {
      map.put("claims_supported", authorizationServerConfiguration.claimsSupported());
    }
    map.put(
        "claims_parameter_supported", authorizationServerConfiguration.claimsParameterSupported());
    map.put(
        "request_parameter_supported",
        authorizationServerConfiguration.requestParameterSupported());
    map.put(
        "request_uri_parameter_supported",
        authorizationServerConfiguration.requestUriParameterSupported());
    map.put(
        "require_request_uri_registration",
        authorizationServerConfiguration.requireRequestUriRegistration());

    if (authorizationServerConfiguration.hasBackchannelTokenDeliveryModesSupported()) {
      map.put(
          "backchannel_token_delivery_modes_supported",
          authorizationServerConfiguration.backchannelTokenDeliveryModesSupported());
    }
    if (authorizationServerConfiguration.hasBackchannelAuthenticationEndpoint()) {
      map.put(
          "backchannel_authentication_endpoint",
          authorizationServerConfiguration.hasBackchannelAuthenticationEndpoint());
    }
    if (authorizationServerConfiguration
        .hasBackchannelAuthenticationRequestSigningAlgValuesSupported()) {
      map.put(
          "backchannel_authentication_request_signing_alg_values_supported",
          authorizationServerConfiguration
              .backchannelAuthenticationRequestSigningAlgValuesSupported());
    }
    if (authorizationServerConfiguration.hasBackchannelUserCodeParameterSupported()) {
      map.put(
          "backchannel_user_code_parameter_supported",
          authorizationServerConfiguration.backchannelUserCodeParameterSupported());
    }
    return map;
  }
}

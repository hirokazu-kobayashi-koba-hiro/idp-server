package org.idp.server.dyscovery;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.configuration.ServerConfiguration;

public class ServerConfigurationResponseCreator {

  public Map<String, Object> create(ServerConfiguration serverConfiguration) {
    Map<String, Object> map = new HashMap<>();
    map.put("issuer", serverConfiguration.issuer());
    map.put("authorization_endpoint", serverConfiguration.authorizationEndpoint());
    map.put("token_endpoint", serverConfiguration.tokenEndpoint());
    map.put("userinfo_endpoint", serverConfiguration.userinfoEndpoint());
    map.put("jwks_uri", serverConfiguration.jwksUri());
    if (serverConfiguration.hasScopesSupported()) {
      map.put("scopes_supported", serverConfiguration.scopesSupported());
    }
    if (serverConfiguration.hasResponseTypesSupported()) {
      map.put("response_types_supported", serverConfiguration.responseTypesSupported());
    }
    if (serverConfiguration.hasResponseModesSupported()) {
      map.put("response_modes_supported", serverConfiguration.responseModesSupported());
    }
    if (serverConfiguration.hasGrantTypesSupported()) {
      map.put("grant_types_supported", serverConfiguration.grantTypesSupported());
    }
    if (serverConfiguration.hasAcrValuesSupported()) {
      map.put("acr_values_supported", serverConfiguration.acrValuesSupported());
    }
    if (serverConfiguration.hasSubjectTypesSupported()) {
      map.put("subject_types_supported", serverConfiguration.subjectTypesSupported());
    }
    if (serverConfiguration.hasIdTokenSigningAlgValuesSupported()) {
      map.put(
          "id_token_signing_alg_values_supported",
          serverConfiguration.idTokenSigningAlgValuesSupported());
    }
    return map;
  }
}

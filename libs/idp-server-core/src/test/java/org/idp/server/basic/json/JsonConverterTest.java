package org.idp.server.basic.json;

import java.util.List;
import org.idp.server.core.oidc.configuration.ClientConfiguration;
import org.idp.server.core.oidc.configuration.ServerConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JsonConverterTest {

  @Test
  void readable() {
    String json =
        """
                {
                  "redirect_uris": [
                    "https://client.example.org/callback",
                    "https://client.example.org/callback2"
                  ],
                  "scope": "read write",
                  "example_extension_parameter": "example_value"
                }
                """;
    JsonConverter jsonConverter = JsonConverter.defaultInstance();
    ClientConfiguration clientConfiguration = jsonConverter.read(json, ClientConfiguration.class);
    Assertions.assertEquals(List.of("read", "write"), clientConfiguration.scopes());
  }

  @Test
  void readableWithSnakeCase() {
    String json =
        """
                {
                   "issuer":
                     "https://server.example.com",
                   "authorization_endpoint":
                     "https://server.example.com/connect/authorize",
                   "token_endpoint":
                     "https://server.example.com/connect/token",
                   "token_endpoint_auth_methods_supported":
                     ["client_secret_basic", "private_key_jwt"],
                   "token_endpoint_auth_signing_alg_values_supported":
                     ["RS256", "ES256"],
                   "userinfo_endpoint":
                     "https://server.example.com/connect/userinfo",
                   "check_session_iframe":
                     "https://server.example.com/connect/check_session",
                   "end_session_endpoint":
                     "https://server.example.com/connect/end_session",
                   "jwks_uri":
                     "https://server.example.com/jwks.json",
                   "registration_endpoint":
                     "https://server.example.com/connect/register",
                   "scopes_supported":
                     ["openid", "profile", "email", "address",
                      "phone", "offline_access"],
                   "response_types_supported":
                     ["code", "code id_token", "id_token", "token id_token"],
                   "acr_values_supported":
                     ["urn:mace:incommon:iap:silver",
                      "urn:mace:incommon:iap:bronze"],
                   "subject_types_supported":
                     ["public", "pairwise"],
                   "userinfo_signing_alg_values_supported":
                     ["RS256", "ES256", "HS256"],
                   "userinfo_encryption_alg_values_supported":
                     ["RSA1_5", "A128KW"],
                   "userinfo_encryption_enc_values_supported":
                     ["A128CBC-HS256", "A128GCM"],
                   "id_token_signing_alg_values_supported":
                     ["RS256", "ES256", "HS256"],
                   "id_token_encryption_alg_values_supported":
                     ["RSA1_5", "A128KW"],
                   "id_token_encryption_enc_values_supported":
                     ["A128CBC-HS256", "A128GCM"],
                   "request_object_signing_alg_values_supported":
                     ["none", "RS256", "ES256"],
                   "display_values_supported":
                     ["page", "popup"],
                   "claim_types_supported":
                     ["normal", "distributed"],
                   "claims_supported":
                     ["sub", "iss", "auth_time", "acr",
                      "name", "given_name", "family_name", "nickname",
                      "profile", "picture", "website",
                      "email", "email_verified", "locale", "zoneinfo",
                      "http://example.info/claimsValue/groups"],
                   "claims_parameter_supported":
                     true,
                   "service_documentation":
                     "http://server.example.com/connect/service_documentation.html",
                   "ui_locales_supported":
                     ["en-US", "en-GB", "en-CA", "fr-FR", "fr-CA"]
                  }
                """;
    JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
    ServerConfiguration serverConfiguration = jsonConverter.read(json, ServerConfiguration.class);

    Assertions.assertEquals(
        "https://server.example.com", serverConfiguration.tokenIssuer().value());
  }
}

package org.idp.server.control_plane.schema;

import org.idp.server.basic.json.schema.JsonSchemaDefinition;

public class AuthorizationServerSchemaDefinition {
  JsonSchemaDefinition definition;

  public AuthorizationServerSchemaDefinition() {
    String json =
        """
                        {
                          "type": "object",
                          "required": [
                            "issuer",
                            "authorization_endpoint",
                            "token_endpoint",
                            "jwks_uri",
                            "scopes_supported",
                            "response_types_supported",
                            "response_modes_supported",
                            "subject_types_supported"
                          ],
                          "properties": {
                            "additionalProperties": true,
                            "issuer": {
                              "type": "string",
                              "format": "uri",
                              "minLength": 1
                            },
                            "authorization_endpoint": {
                              "type": "string",
                              "format": "uri",
                              "minLength": 1
                            },
                            "token_endpoint": {
                              "type": "string",
                              "format": "uri",
                              "minLength": 1
                            },
                            "userinfo_endpoint": {
                              "type": "string",
                              "format": "uri"
                            },
                            "jwks_uri": {
                              "type": "string",
                              "format": "uri",
                              "minLength": 1
                            },
                            "registration_endpoint": {
                              "type": "string",
                              "format": "uri"
                            },
                            "scopes_supported": {
                              "type": "array",
                              "items": {
                                "type": "string"
                              }
                            },
                            "response_types_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "code",
                                  "token",
                                  "id_token",
                                  "code token",
                                  "code token id_token",
                                  "token id_token",
                                  "code id_token",
                                  "none"
                                ]
                              }
                            },
                            "response_modes_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "query",
                                  "fragment"
                                ]
                              },
                              "default": [
                                "query",
                                "fragment"
                              ]
                            },
                            "grant_types_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "authorization_code",
                                  "implicit",
                                  "refresh_token",
                                  "password",
                                  "client_credentials",
                                  "urn:openid:params:grant-type:ciba"
                                ]
                              },
                              "default": [
                                "authorization_code",
                                "implicit"
                              ]
                            },
                            "acr_values_supported": {
                              "type": "array",
                              "items": {
                                "type": "string"
                              }
                            },
                            "subject_types_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "pairwise",
                                  "public"
                                ]
                              }
                            },
                            "id_token_signing_alg_values_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "RS256",
                                  "ES256",
                                  "HS256"
                                ]
                              }
                            },
                            "id_token_encryption_alg_values_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "RSA1_5",
                                  "A128KW"
                                ]
                              }
                            },
                            "id_token_encryption_enc_values_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "A128CBC-HS256",
                                  "A128GCM"
                                ]
                              }
                            },
                            "userinfo_signing_alg_values_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "RS256",
                                  "ES256",
                                  "HS256"
                                ]
                              }
                            },
                            "userinfo_encryption_alg_values_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "RSA1_5",
                                  "A128KW"
                                ]
                              }
                            },
                            "userinfo_encryption_enc_values_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "A128CBC-HS256",
                                  "A128GCM"
                                ]
                              }
                            },
                            "request_object_signing_alg_values_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "RS256",
                                  "ES256",
                                  "HS256"
                                ]
                              }
                            },
                            "request_object_encryption_alg_values_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "RSA1_5",
                                  "A128KW"
                                ]
                              }
                            },
                            "request_object_encryption_enc_values_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "A128CBC-HS256",
                                  "A128GCM"
                                ]
                              }
                            },
                            "token_endpoint_auth_methods_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "client_secret_post",
                                  "client_secret_basic",
                                  "client_secret_jwt",
                                  "private_key_jwt",
                                  "tls_client_auth",
                                  "self_signed_tls_client_auth"
                                ]
                              }
                            },
                            "token_endpoint_auth_signing_alg_values_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "RS256",
                                  "ES256",
                                  "HS256"
                                ]
                              }
                            },
                            "display_values_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "page",
                                  "popup"
                                ]
                              }
                            },
                            "claim_types_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "normal"
                                ]
                              }
                            },
                            "claims_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "sub",
                                  "iss",
                                  "auth_time",
                                  "acr",
                                  "name",
                                  "given_name",
                                  "family_name",
                                  "nickname",
                                  "profile",
                                  "picture",
                                  "website",
                                  "email",
                                  "email_verified",
                                  "locale",
                                  "zoneinfo",
                                  "birthdate",
                                  "gender",
                                  "preferred_username",
                                  "middle_name",
                                  "updated_at",
                                  "address",
                                  "phone_number",
                                  "phone_number_verified"
                                ]
                              }
                            },
                            "service_documentation": {
                              "type": "string",
                              "format": "uri"
                            },
                            "claims_locales_supported": {
                              "type": "string",
                              "items": {
                                "type": "string"
                              }
                            },
                            "claims_parameter_supported": {
                              "type": "boolean",
                              "default": true
                            },
                            "request_parameter_supported": {
                              "type": "boolean",
                              "default": false
                            },
                            "request_uri_parameter_supported": {
                              "type": "boolean",
                              "default": false
                            },
                            "require_request_uri_registration": {
                              "type": "boolean",
                              "default": false
                            },
                            "op_policy_uri": {
                              "type": "string",
                              "format": "uri"
                            },
                            "op_tos_uri": {
                              "type": "string",
                              "format": "uri"
                            },
                            "revocation_endpoint": {
                              "type": "string",
                              "format": "uri"
                            },
                            "revocation_endpoint_auth_methods_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "client_secret_post",
                                  "client_secret_basic",
                                  "client_secret_jwt",
                                  "private_key_jwt",
                                  "tls_client_auth",
                                  "self_signed_tls_client_auth"
                                ]
                              }
                            },
                            "revocation_endpoint_auth_signing_alg_values_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "RS256",
                                  "ES256",
                                  "HS256"
                                ]
                              }
                            },
                            "introspection_endpoint": {
                              "type": "string",
                              "format": "uri"
                            },
                            "introspection_endpoint_auth_methods_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "RS256",
                                  "ES256",
                                  "HS256"
                                ]
                              }
                            },
                            "introspection_endpoint_auth_signing_alg_values_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "RS256",
                                  "ES256",
                                  "HS256"
                                ]
                              }
                            },
                            "code_challenge_methods_supported": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": [
                                  "plain",
                                  "S256"
                                ]
                              }
                            },
                            "extension": {
                              "type": "object"
                            }
                          }
                        }
                        """;
    this.definition = JsonSchemaDefinition.fromJson(json);
  }

  public JsonSchemaDefinition definition() {
    return definition;
  }
}

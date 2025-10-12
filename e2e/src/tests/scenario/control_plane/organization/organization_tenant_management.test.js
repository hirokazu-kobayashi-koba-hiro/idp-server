import { describe, expect, it, test } from "@jest/globals";
import { deletion, get, postWithJson, putWithJson } from "../../../../lib/http";
import { backendUrl, clientSecretPostClient, serverConfig } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";
import { v4 as uuidv4 } from "uuid";

describe("organization tenant management api", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";

  describe("success pattern", () => {
    it("crud operations", async () => {
      // Get OAuth token with org-management scope

      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Create a new tenant within the organization
      const timestamp = Date.now();
      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          tenant: {
            "id": uuidv4(),
            "name": `Organization Tenant ${timestamp}`,
            "domain": "http://localhost:8080",
            "description": "Test tenant for organization management",
            "authorization_provider": "idp-server",
            "tenant_type": "BUSINESS"
          },
          authorization_server: {
            "issuer": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9",
            "authorization_endpoint": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/authorizations",
            "token_endpoint": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens",
            "token_endpoint_auth_methods_supported": [
              "client_secret_post",
              "client_secret_basic",
              "client_secret_jwt",
              "private_key_jwt",
              "tls_client_auth",
              "self_signed_tls_client_auth",
              "none"
            ],
            "token_endpoint_auth_signing_alg_values_supported": [
              "RS256",
              "ES256"
            ],
            "userinfo_endpoint": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/userinfo",
            "jwks_uri": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/jwks",
            "jwks": "{ \"keys\": [ { \"kty\": \"EC\", \"d\": \"yIWDrlhnCy3yL9xLuqZGOBFFq4PWGsCeM7Sc_lfeaQQ\", \"use\": \"sig\", \"crv\": \"P-256\", \"kid\": \"access_token\", \"x\": \"iWJINqt0ySv3kVEvlHbvNkPKY2pPSf1cG1PSx3tRfw0\", \"y\": \"rW1FdfXK5AQcv-Go6Xho0CR5AbLai7Gp9IdLTIXTSIQ\", \"alg\": \"ES256\" } ,{ \"kty\": \"EC\", \"d\": \"HrgT4zqM2BvrlwUWagyeNnZ40nZ7rTY4gYG9k99oGJg\", \"use\": \"enc\", \"crv\": \"P-256\", \"kid\": \"request_enc_key\", \"x\": \"PM6be42POiKdNzRKGeZ1Gia8908XfmSSbS4cwPasWTo\", \"y\": \"wksaan9a4h3L8R1UMmvc9w6rPB_F07IA-VHx7n7Add4\", \"alg\": \"ECDH-ES\" },{ \"p\": \"3yO0t6JmIHnsW6SrAfHa_4Ynd3unyrTQnCpPCo1GAlzbIRUgyDWohcfKaQ9rFoeO67b91dcpDiH6jTuxdD1S95ph7KB2RuDTGhhD5jg5_VdEthnlK7Hw3GZhFRnsBAKxTmJuJ3R12tcm33054vEtxAZB3FWyVsf0WI36vbDYRU0\", \"kty\": \"RSA\", \"q\": \"n10rboIDPMk0Qyvug--W_s4yB1j7QdIvduBO-bZgrG_rLLXgFTzKbef_ArygXBlCqinwn7MSw6O5G6IKw5raK7IvehlDT_xC8mOtajiFcj_t9PkaUdHXNR-tbJdLJwohbJNwXvP28DhMCTUPLTxW005sVFfChbtFiQ22eJFsrF0\", \"d\": \"azIA2jVLHvXr2jM0Eq9rQCErJqjNjtDu86k0-kmF3xfjuAS9IBuGnU9W0bkuaOaY85ngHEb0qf-nXRnOaF_6s7glSVm0mDM6mNgDOzNmgqHV14mJTkmixAmxrGmvVOix06mIUc2liG_wUO7OHVkd9kgNV5QVj-EF-VDULTin6FfNcJgcUsYAb59_MHASvS8foWNb3o9bSdPFLDDaT0mzl2nwEe8SERtIsLBrcbbKcedQHxm1SUq8sks-1L6E8Qfx6jFQJJMNLj-XBbwrFxww1DwrhmNz_1AOZM5VDTZg-kMDqjcBvgjHzmA2o9X9p1Gaa5xadO51msiZQAbPSMK1sQ\", \"e\": \"AQAB\", \"use\": \"sig\", \"kid\": \"id_token_nextauth\", \"qi\": \"A3s2bGrlaC6WU-vQGvugen8vx4ouqrTY60ZD-E1YRp6ADbC7g6318WZ7IZl-FoFGto--NvnMFOsNYkSMXrFcaroixNjJEo6HcNWs7BTqS7PICZmtmUWr5V2f9RENwJxbG9GnYDsZTQYM84j2PNsJxAzjmFQvQzygsfeEyuzAtJg\", \"dp\": \"vUWmNtWz1vxUdm-49k9WOcRrmbfz3cd949knboXiyoJFBUzMn8aUCdYsZO1FIrkdi-eObGKzWl-MDVyC61xREeGMCpEZgomVxt6qSY-L8M6jY-uXLncjHXBiDOoN_mDiUODBGwp4JYa2XH_2J__3l_zOxLyUJ3Q4WR0lgN2OtUk\", \"alg\": \"RS256\", \"dq\": \"g1bKAJ1uBZ7dT67ZOCsxinZtjNis2qZbL-HVtL-2FOd4LrUGJPqg6suUw7CpiL3Yz10ZTsTK5in82OVHccYhoHmN31cKvtTsZ8_2j-BdOretaYQTSPNkJgghaamW6mnS-iTZK6htD7WWFNCB3YopFKVBapGZY5XfzQBcLinMIpE\", \"n\": \"iuhjEgaY4KCS_rbvODf-QadNvj9DaoHx8PzPKpZdxx_g0aQx7wvachzc7A_F8RKkToM10qGrDtFFehTCzxcC44WHsFezRd3yxNNhdfVEfcHApLpMYaq8A3HAi8NMN-cMkfqQRIvsvDmbYtt8B6EZG8YsFhjMZRY-7gzF_LGdIMoh3Af0WUx-L_AWRIawXuAwDIm11OQh9bt3hdoJbZFd9B4Wf1H5oxbsJ5MZQAQ9ltc23F60zqoDt5RAehC30w7rMeYH8WKoKpS5-odhVUDqieAS5j8iVegjcl63CoxS2BRLmN9UYzQvuEo-HUeWbucBlXmqD4sdn6Ypyt5QZpzo-Q\" }] }",
            "grant_types_supported": [
              "authorization_code",
              "refresh_token",
              "password",
              "client_credentials",
              "urn:openid:params:grant-type:ciba"
            ],
            "token_signed_key_id": "",
            "id_token_signed_key_id": "debug_id_token_account_opening",
            "scopes_supported": [
              "openid",
              "profile",
              "email",
              "address",
              "phone",
              "offline_access",
              "account",
              "transfers",
              "read",
              "write",
              "identity_verification_application",
              "identity_verification_application_delete",
              "identity_verification_result",
              "identity_credentials_update",
              "management",
              "claims:ex_sub",
              "claims:roles",
              "claims:permissions",
              "claims:assigned_tenants",
              "claims:assigned_organizations",
              "claims:authentication_devices",
              "claims:status",
              "verified_claims:family_name"
            ],
            "response_types_supported": [
              "code",
              "token",
              "id_token",
              "code token",
              "code token id_token",
              "token id_token",
              "code id_token",
              "none"
            ],
            "response_modes_supported": [
              "query",
              "fragment"
            ],
            "acr_values_supported": [
              "urn:mace:incommon:iap:gold",
              "urn:mace:incommon:iap:silver",
              "urn:mace:incommon:iap:bronze"
            ],
            "subject_types_supported": [
              "public",
              "pairwise"
            ],
            "userinfo_signing_alg_values_supported": [
              "RS256",
              "ES256",
              "HS256"
            ],
            "userinfo_encryption_alg_values_supported": [
              "RSA1_5",
              "A128KW"
            ],
            "userinfo_encryption_enc_values_supported": [
              "A128CBC-HS256",
              "A128GCM"
            ],
            "id_token_signing_alg_values_supported": [
              "RS256",
              "ES256",
              "HS256"
            ],
            "id_token_encryption_alg_values_supported": [
              "RSA1_5",
              "A128KW"
            ],
            "id_token_encryption_enc_values_supported": [
              "A128CBC-HS256",
              "A128GCM"
            ],
            "request_object_signing_alg_values_supported": [
              "none",
              "RS256",
              "ES256"
            ],
            "display_values_supported": [
              "page",
              "popup"
            ],
            "claim_types_supported": [
              "normal"
            ],
            "claims_supported": [
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
              "phone_number_verified",
              "roles",
              "permissions",
              "assigned_tenants",
              "assigned_organizations",
              "authentication_devices",
              "status"
            ],
            "claims_parameter_supported": true,
            "service_documentation": "",
            "ui_locales_supported": [
              "en-US",
              "en-GB",
              "en-CA",
              "fr-FR",
              "fr-CA"
            ],
            "token_introspection_endpoint": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens/introspection",
            "token_revocation_endpoint": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens/revocation",
            "backchannel_token_delivery_modes_supported": [
              "poll",
              "ping",
              "push"
            ],
            "backchannel_authentication_endpoint": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/backchannel/authentications",
            "backchannel_authentication_request_signing_alg_values_supported": [
              "RS256",
              "ES256"
            ],
            "backchannel_user_code_parameter_supported": true,
            "authorization_details_types_supported": [
              "payment_initiation",
              "account_information",
              "openid_credential"
            ],
            "authorization_signing_alg_values_supported": [
              "RS256",
              "ES256",
              "HS256"
            ],
            "authorization_encryption_alg_values_supported": [
              "RSA1_5",
              "A128KW"
            ],
            "authorization_encryption_enc_values_supported": [
              "A128CBC-HS256",
              "A128GCM"
            ],
            "tls_client_certificate_bound_access_tokens": true,
            "vp_formats_supported": {
              "jwt_vc_json": {
                "alg_values_supported": [
                  "ES256K",
                  "ES384"
                ]
              },
              "jwt_vp_json": {
                "alg_values_supported": [
                  "ES256K",
                  "EdDSA"
                ]
              }
            },
            "extension": {
              "access_token_type": "JWT",
              "token_signed_key_id": "id_token_nextauth",
              "id_token_signed_key_id": "id_token_nextauth",
              "access_token_duration": 3600,
              "id_token_duration": 3600,
              "id_token_strict_mode": false,
              "default_max_age": 86400,
              "fapi_baseline_scopes": [
                "read"
              ],
              "fapi_advance_scopes": [
                "write"
              ],
              "required_identity_verification_scopes": [
                "transfers"
              ],
              "backchannel_authentication_request_expires_in": 60,
              "backchannel_authentication_polling_interval": 5,
              "custom_claims_scope_mapping": true,
              "access_token_user_custom_properties": true,
              "access_token_selective_verified_claims": true
            }
          }
        }
      });
      console.log("Create tenant response:", createResponse.data);
      expect(createResponse.status).toBe(201);
      expect(createResponse.data).toHaveProperty("result");

      const tenantId = createResponse.data.result.id;

      // List tenants within the organization
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("List tenants response:", JSON.stringify(listResponse.data, null, 2));
      expect(listResponse.status).toBe(200);
      expect(listResponse.data).toHaveProperty("list");
      expect(Array.isArray(listResponse.data.list)).toBe(true);

      // Get specific tenant details
      const detailResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Tenant detail response:", detailResponse.data);
      expect(detailResponse.status).toBe(200);
      expect(detailResponse.data).toHaveProperty("id", tenantId);
      expect(detailResponse.data).toHaveProperty("name");

      // Update tenant
      const updateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": `Updated Organization Tenant ${timestamp}`,
          "description": "Updated description for organization tenant",
          "tenant_type": "BUSINESS"
        }
      });
      console.log("Update tenant response:", updateResponse.data);
      expect(updateResponse.status).toBe(200);
      expect(updateResponse.data).toHaveProperty("result");

      // Delete tenant
      const deleteResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("Delete tenant response:", deleteResponse.data);
      expect(deleteResponse.status).toBe(204);
    });

    it("dry run create functionality", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const timestamp = Date.now();
      const dryRunTenantId = uuidv4();

      // Test dry run for tenant creation
      const dryRunCreateResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants?dry_run=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          tenant: {
            "id": dryRunTenantId,
            "name": `Dry Run Tenant ${timestamp}`,
            "domain": "http://localhost:8080",
            "description": "Test tenant for dry run",
            "authorization_provider": "idp-server",
            "tenant_type": "BUSINESS"
          },
          authorization_server: {
            "issuer": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9",
            "authorization_endpoint": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/authorizations",
            "token_endpoint": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens",
            "token_endpoint_auth_methods_supported": [
              "client_secret_post",
              "client_secret_basic"
            ],
            "userinfo_endpoint": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/userinfo",
            "jwks_uri": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/jwks",
            "grant_types_supported": [
              "authorization_code",
              "refresh_token",
              "password",
              "client_credentials"
            ],
            "scopes_supported": [
              "openid",
              "profile",
              "email",
              "account",
              "management"
            ],
            "response_types_supported": [
              "code"
            ],
            "response_modes_supported": [
              "query",
              "fragment"
            ],
            "subject_types_supported": [
              "public"
            ],
            "id_token_signing_alg_values_supported": [
              "RS256"
            ],
            "claims_supported": [
              "sub",
              "iss",
              "name",
              "email"
            ],
            "extension": {
              "access_token_type": "JWT",
              "access_token_duration": 3600,
              "id_token_duration": 3600
            }
          }
        }
      });
      console.log("Dry run create response:", dryRunCreateResponse.data);
      expect(dryRunCreateResponse.status).toBe(201);
      expect(dryRunCreateResponse.data).toHaveProperty("result");

      // Verify tenant was not actually created by listing tenants
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(listResponse.status).toBe(200);

      // Check that dry-run tenant ID doesn't exist in the actual list
      const tenantIds = listResponse.data.list.map(tenant => tenant.id);
      expect(tenantIds).not.toContain(dryRunTenantId);
    });

    it("dry run update functionality", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const timestamp = Date.now();
      const tenantId = uuidv4();

      // First create a tenant
      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          tenant: {
            "id": tenantId,
            "name": `Test Tenant ${timestamp}`,
            "domain": "http://localhost:8080",
            "description": "Original description",
            "authorization_provider": "idp-server",
            "tenant_type": "BUSINESS"
          },
          authorization_server: {
            "issuer": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9",
            "authorization_endpoint": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/authorizations",
            "token_endpoint": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens",
            "token_endpoint_auth_methods_supported": [
              "client_secret_post",
              "client_secret_basic"
            ],
            "userinfo_endpoint": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/userinfo",
            "jwks_uri": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/jwks",
            "grant_types_supported": [
              "authorization_code",
              "refresh_token"
            ],
            "scopes_supported": [
              "openid",
              "profile",
              "email"
            ],
            "response_types_supported": [
              "code"
            ],
            "response_modes_supported": [
              "query"
            ],
            "subject_types_supported": [
              "public"
            ],
            "id_token_signing_alg_values_supported": [
              "RS256"
            ],
            "claims_supported": [
              "sub",
              "iss",
              "name",
              "email"
            ],
            "extension": {
              "access_token_type": "JWT",
              "access_token_duration": 3600,
              "id_token_duration": 3600
            }
          }
        }
      });
      expect(createResponse.status).toBe(201);

      // Test dry run for tenant update
      const dryRunUpdateResponse = await putWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}?dry_run=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          "name": `Dry Run Updated Tenant ${timestamp}`,
          "description": "Dry run updated description",
          "tenant_type": "BUSINESS"
        }
      });
      console.log("Dry run update response:", dryRunUpdateResponse.data);
      expect(dryRunUpdateResponse.status).toBe(200);
      expect(dryRunUpdateResponse.data).toHaveProperty("dry_run", true);

      // Verify tenant was not actually updated
      const verifyResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log(JSON.stringify(verifyResponse.data, null, 2));
      expect(verifyResponse.status).toBe(200);
      expect(verifyResponse.data.id).toBe(tenantId);

      // Cleanup
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
    });

    it("dry run delete functionality", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const timestamp = Date.now();
      const tenantId = uuidv4();

      // First create a tenant
      const createResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: {
          tenant: {
            "id": tenantId,
            "name": `Test Tenant ${timestamp}`,
            "domain": "http://localhost:8080",
            "description": "Test tenant for dry run delete",
            "authorization_provider": "idp-server",
            "tenant_type": "BUSINESS"
          },
          authorization_server: {
            "issuer": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9",
            "authorization_endpoint": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/authorizations",
            "token_endpoint": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens",
            "token_endpoint_auth_methods_supported": [
              "client_secret_post",
              "client_secret_basic"
            ],
            "userinfo_endpoint": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/userinfo",
            "jwks_uri": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/jwks",
            "grant_types_supported": [
              "authorization_code",
              "refresh_token"
            ],
            "scopes_supported": [
              "openid",
              "profile",
              "email"
            ],
            "response_types_supported": [
              "code"
            ],
            "response_modes_supported": [
              "query"
            ],
            "subject_types_supported": [
              "public"
            ],
            "id_token_signing_alg_values_supported": [
              "RS256"
            ],
            "claims_supported": [
              "sub",
              "iss",
              "name",
              "email"
            ],
            "extension": {
              "access_token_type": "JWT",
              "access_token_duration": 3600,
              "id_token_duration": 3600
            }
          }
        }
      });
      expect(createResponse.status).toBe(201);

      // Test dry run for tenant delete
      const dryRunDeleteResponse = await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}?dry_run=true`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
      console.log("Dry run delete response:", dryRunDeleteResponse.data);
      expect(dryRunDeleteResponse.status).toBe(200);
      expect(dryRunDeleteResponse.data).toHaveProperty("message");
      expect(dryRunDeleteResponse.data).toHaveProperty("id", tenantId);
      expect(dryRunDeleteResponse.data).toHaveProperty("dry_run", true);

      // Verify tenant was not actually deleted
      const verifyResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      expect(verifyResponse.status).toBe(200);
      expect(verifyResponse.data).toHaveProperty("id", tenantId);

      // Cleanup - actual delete
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        }
      });
    });

    it("pagination support", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Test pagination parameters
      const paginatedResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants?limit=5&offset=0`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Paginated response:", JSON.stringify(paginatedResponse.data, null, 2));
      expect(paginatedResponse.status).toBe(200);
      expect(paginatedResponse.data).toHaveProperty("list");
      expect(Array.isArray(paginatedResponse.data.list)).toBe(true);
    });
  });

  describe("error cases", () => {
    it("unauthorized access without proper scope", async () => {
      // Get token without org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro",
        password: "successUserCode001",
        scope: "account", // Missing org-management scope
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Try to access organization tenant API
      const listResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Unauthorized response:", listResponse.data);
      expect(listResponse.status).toBe(403);
    });

    it("invalid organization id", async () => {
      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Try to access with invalid organization ID
      const invalidOrgResponse = await get({
        url: `${backendUrl}/v1/management/organizations/invalid-org-id-123/tenants`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Invalid org response:", invalidOrgResponse.data);
      expect([400, 404]).toContain(invalidOrgResponse.status);
    });

    it("client credentials grant not supported", async () => {
      // Get client credentials token
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "client_credentials",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      // Try to access organization tenant API with client credentials
      const clientCredentialsResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants`,
        headers: {
          Authorization: `Bearer ${accessToken}`
        }
      });
      console.log("Client credentials response:", clientCredentialsResponse.data);
      expect(clientCredentialsResponse.status).toBe(401);
    });

    const invalidRequestCases = [
      ["missing tenant name", {
        tenant: { "id": uuidv4(), "tenant_type": "BUSINESS", "domain": "http://localhost:8080", "authorization_provider": "idp-server" },
        authorization_server: { "issuer": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9" }
      }],
      ["invalid tenant type", {
        tenant: { "id": uuidv4(), "name": "Test", "tenant_type": "INVALID_TYPE", "domain": "http://localhost:8080", "authorization_provider": "idp-server" },
        authorization_server: { "issuer": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9" }
      }],
      ["empty tenant id", {
        tenant: { "id": "", "name": "Test", "tenant_type": "BUSINESS", "domain": "http://localhost:8080", "authorization_provider": "idp-server" },
        authorization_server: { "issuer": "http://localhost:8080/952f6906-3e95-4ed3-86b2-981f90f785f9" }
      }]
    ];

    test.each(invalidRequestCases)("invalid request: %s", async (description, body) => {
      console.log("Testing invalid request:", description, body);

      // Get OAuth token with org-management scope
      const tokenResponse = await requestToken({
        endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
        grantType: "password",
        username: "ito.ichiro",
        password: "successUserCode001",
        scope: "org-management account management",
        clientId: "org-client",
        clientSecret: "org-client-001"
      });
      console.log("Token response:", tokenResponse.data);
      expect(tokenResponse.status).toBe(200);
      const accessToken = tokenResponse.data.access_token;

      const invalidResponse = await postWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants`,
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
        body: body
      });
      console.log("Invalid request response:", invalidResponse.data);
      expect(invalidResponse.status).toBe(400);
    });
  });

});
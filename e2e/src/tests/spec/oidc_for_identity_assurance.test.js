import { beforeAll, describe, expect, it, xit } from "@jest/globals";

import { getConfiguration, getJwks, requestToken } from "../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  serverConfig
} from "../testConfig";
import { requestAuthorizations } from "../../oauth/request";
import { verifyAndDecodeJwt } from "../../lib/jose";
import { get } from "../../lib/http";

// Structured per .claude/rules/e2e-spec-conformance.md:
//   - describe = spec section number + title, verbatim (OpenID Connect for Identity Assurance 1.0 §5–§8)
//   - it = spec sentence verbatim, keeping the normative keywords (MUST / SHALL / SHOULD / REQUIRED / OPTIONAL)
//   - unimplemented / untested requirements are listed as `xit` so the coverage ledger
//     (covered = it / known-pending = xit / missing = absent) is readable at a glance.
// Verbatim quotes are from OIDC for Identity Assurance 1.0 unless marked otherwise. Where a case
// derives from OIDC Core §5.5.1 (empty object / essential) the source is named explicitly,
// because IDA itself does not define those sentences. eKYC module #N references the external OIDF
// conformance-suite repo (github.com/openid/conformance-suite, eKYC-IDA test plan) — not a file in
// this repo.
//
// The end-user is a dedicated, non-privileged IDA fixture (ida.verified.user@gmail.com), provisioned
// by config/scripts/e2e-test-data.sh from config/examples/e2e/test-tenant/user/ida-verified-user.json
// — kept separate from the shared ito.ichiro super-user so its verified_claims can be tuned for these
// tests without side effects elsewhere. It holds verification.trust_framework = "eidas",
// verification.time (drives §5.5.2 max_age), and claims given_name "Sarah" / family_name "Meredyth" /
// birthdate / address. middle_name and gender are intentionally NOT stored (treated as unavailable).
const idaVerifiedUser = {
  username: "ida.verified.user@gmail.com",
  password: "successUserCode001",
};
describe("OpenID Connect for Identity Assurance 1.0", () => {

  // Drives verified_claims via the `claims` parameter's id_token member; returns the ID Token payload.
  // §5.7.4 ("the OP shall not return an error to the RP") is asserted here via the 200 status.
  const idTokenVerifiedClaims = async (verifiedClaimsRequest) => {
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseType: "code",
      state: "verified_claims_id_token",
      scope: "openid " + clientSecretPostClient.scope,
      redirectUri: clientSecretPostClient.redirectUri,
      claims: JSON.stringify({ id_token: { verified_claims: verifiedClaimsRequest } }),
      user: idaVerifiedUser,
    });
    expect(authorizationResponse.code).not.toBeNull();

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      code: authorizationResponse.code,
      grantType: "authorization_code",
      redirectUri: clientSecretPostClient.redirectUri,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    // §5.7.4: "In both cases, the OP shall not return an error to the RP."
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data).toHaveProperty("id_token");

    const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
    const decodedIdToken = verifyAndDecodeJwt({
      jwt: tokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    expect(decodedIdToken.verifyResult).toBe(true);
    return decodedIdToken.payload;
  };

  // Drives verified_claims via the `claims` parameter's userinfo member (no verified_claims:* scope,
  // so this exercises the claims-parameter path, not the scope path); returns the UserInfo body and
  // the ID Token payload. (#1628)
  const userinfoVerifiedClaims = async (verifiedClaimsRequest) => {
    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseType: "code",
      state: "verified_claims_userinfo",
      scope: "openid " + clientSecretPostClient.scope,
      redirectUri: clientSecretPostClient.redirectUri,
      claims: JSON.stringify({ userinfo: { verified_claims: verifiedClaimsRequest } }),
      user: idaVerifiedUser,
    });
    expect(authorizationResponse.code).not.toBeNull();

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      code: authorizationResponse.code,
      grantType: "authorization_code",
      redirectUri: clientSecretPostClient.redirectUri,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    expect(tokenResponse.status).toBe(200);

    const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
    const decodedIdToken = verifyAndDecodeJwt({
      jwt: tokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    expect(decodedIdToken.verifyResult).toBe(true);

    const userinfoResponse = await get({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/userinfo`,
      headers: { Authorization: `Bearer ${tokenResponse.data.access_token}` },
    });
    expect(userinfoResponse.status).toBe(200);
    return { userinfo: userinfoResponse.data, idTokenPayload: decodedIdToken.payload };
  };

  describe("5.2 Verified claims delivery", () => {
    it("A `verified_claims` element can be added to an OpenID Connect UserInfo response and/or an ID Token. (delivered in the ID Token; success pattern)", async () => {
      const verifiedClaims = {
        "claims": {
          "address": {
            "country": "UK",
            "locality": "Edinburgh",
            "postal_code": "EH1 9GP",
            "street_address": "122 Burns Crescent"
          },
          "birthdate": "1976-03-11",
          "given_name": "Sarah",
          "family_name": "Meredyth",
          "place_of_birth": {
            "country": "UK"
          }
        },
        "verification": {
          "evidence": [
            {
              "time": "2021-04-09T14:12Z",
              "type": "electronic_record",
              "record": {
                "type": "mortgage_account",
                "source": {
                  "name": "TheCreditBureau"
                }
              },
              "check_details": [
                {
                  "txn": "kbv1-hf934hn09234ng03jj3",
                  "check_method": "kbv",
                  "organization": "TheCreditBureau"
                }
              ]
            },
            {
              "time": "2021-04-09T14:12Z",
              "type": "electronic_record",
              "record": {
                "type": "bank_account",
                "source": {
                  "name": "TheBank"
                }
              },
              "check_details": [
                {
                  "txn": "kbv2-nm0f23u9459fj38u5j6",
                  "check_method": "kbv",
                  "organization": "OpenBankingTPP"
                }
              ]
            }
          ],
          "trust_framework": "eidas"
        }
      };

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "aiueo",
        scope: "openid profile phone email verified_claims:family_name " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        customParams: {
          organizationId: "123",
          organizationName: "test",
        },
        claims: JSON.stringify({
          id_token: {
            verified_claims: verifiedClaims,
          }
        }),
        user: idaVerifiedUser,
      });
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      expect(tokenResponse.data).toHaveProperty("id_token");

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      expect(jwksResponse.status).toBe(200);

      const decodedIdToken = verifyAndDecodeJwt({
        jwt: tokenResponse.data.id_token,
        jwks: jwksResponse.data,
      });
      const payload = decodedIdToken.payload;
      expect(decodedIdToken.verifyResult).toBe(true);
      expect(payload.iss).not.toBeNull();
      expect(payload.iss).toEqual(serverConfig.issuer);
      expect(payload.verified_claims.claims).not.toBeNull();
      expect(payload.verified_claims.verification).not.toBeNull();
    });

    it("A `verified_claims` element can be added to an OpenID Connect UserInfo response and/or an ID Token. (delivered in the UserInfo response; requested userinfo-only and omitted from the ID Token — eKYC module #9 ekyc-server-request-only-in-userinfo)", async () => {
      const { userinfo, idTokenPayload } = await userinfoVerifiedClaims({
        verification: { trust_framework: null },
        claims: { given_name: null, family_name: null },
      });

      expect(userinfo.verified_claims).toBeDefined();
      expect(userinfo.verified_claims.verification.trust_framework).toEqual("eidas");
      expect(userinfo.verified_claims.claims.given_name).toEqual("Sarah");
      // EnsureIdTokenDoesNotContainVerifiedClaims: requested userinfo-only, so the ID Token must not carry it.
      expect(idTokenPayload.verified_claims).toBeUndefined();
    });
  });

  describe("5.3 Requesting end-user claims", () => {
    it("For each claim, the value is either `null` (default), or an object. (requested with `null` → the claim is returned)", async () => {
      const payload = await idTokenVerifiedClaims({
        verification: { trust_framework: null },
        claims: { given_name: null },
      });
      expect(payload.verified_claims).toBeDefined();
      expect(payload.verified_claims.verification.trust_framework).toEqual("eidas");
      expect(payload.verified_claims.claims.given_name).toEqual("Sarah");
    });

    it("For each claim, the value is either `null` (default), or an object. (requested with an empty JSON object {} — eKYC module #2 ekyc-server-happypath-emptyobject)", async () => {
      const payload = await idTokenVerifiedClaims({
        verification: { trust_framework: null },
        claims: { given_name: {} },
      });
      expect(payload.verified_claims).toBeDefined();
      expect(payload.verified_claims.claims.given_name).toEqual("Sarah");
    });

    it('For each claim, the value is either `null` (default), or an object. (requested with {"essential": false}, an OIDC Core §5.5.1 member — eKYC module #3 ekyc-server-happypath-essentialfalse)', async () => {
      const payload = await idTokenVerifiedClaims({
        verification: { trust_framework: null },
        claims: { given_name: { essential: false } },
      });
      expect(payload.verified_claims).toBeDefined();
      expect(payload.verified_claims.claims.given_name).toEqual("Sarah");
    });
  });

  describe("5.5 Defining further constraints on verification data", () => {
    describe("5.5.1 Value/values", () => {
      it("The OP shall not ignore some or all of the query restrictions on possible values and shall not deliver available verified/verification data that does not match these constraints. (returns the claim when its value/values constraint is satisfied)", async () => {
        const payload = await idTokenVerifiedClaims({
          verification: { trust_framework: { value: "eidas" } },
          claims: { given_name: { values: ["Hanako", "Sarah"] } },
        });
        expect(payload.verified_claims).toBeDefined();
        expect(payload.verified_claims.verification.trust_framework).toEqual("eidas");
        expect(payload.verified_claims.claims.given_name).toEqual("Sarah");
      });
    });

    describe("5.5.2 Max_age", () => {
      it("max_age: Optional. JSON number value only applicable to claims that contain dates or timestamps. The OP should try to fulfill this requirement. (verification.time staler than max_age is dropped; trust_framework and the requested claim are still returned — a max_age miss is not whole-omitted, unlike a value/values miss)", async () => {
        // verification.time is fixed at 2021-04-09; with max_age 3600s it is always far too old, so
        // the OP drops just that element. trust_framework (REQUIRED floor) + claims remain.
        const payload = await idTokenVerifiedClaims({
          verification: { trust_framework: null, time: { max_age: 3600 } },
          claims: { given_name: null },
        });
        expect(payload.verified_claims).toBeDefined();
        expect(payload.verified_claims.verification.trust_framework).toEqual("eidas");
        expect(payload.verified_claims.verification).not.toHaveProperty("time");
        expect(payload.verified_claims.claims.given_name).toEqual("Sarah");
      });

      it("max_age: Optional. JSON number value only applicable to claims that contain dates or timestamps. The OP should try to fulfill this requirement. (verification.time within max_age is returned)", async () => {
        // max_age effectively unbounded (~31700 years), so the 2021 timestamp is fresh enough.
        const payload = await idTokenVerifiedClaims({
          verification: { trust_framework: null, time: { max_age: 1000000000000 } },
          claims: { given_name: null },
        });
        expect(payload.verified_claims.verification.time).toEqual("2021-04-09T14:12Z");
      });

      it("max_age: Optional. JSON number value only applicable to claims that contain dates or timestamps. The OP should try to fulfill this requirement. (a date claim staler than max_age is dropped individually; other claims are unaffected)", async () => {
        const payload = await idTokenVerifiedClaims({
          verification: { trust_framework: null },
          claims: { birthdate: { max_age: 3600 }, given_name: null },
        });
        expect(payload.verified_claims).toBeDefined();
        expect(payload.verified_claims.claims).not.toHaveProperty("birthdate");
        expect(payload.verified_claims.claims.given_name).toEqual("Sarah");
      });

      it("max_age: Optional. JSON number value only applicable to claims that contain dates or timestamps. The OP should try to fulfill this requirement. (max_age on a non-date claim does not apply; the claim is returned)", async () => {
        const payload = await idTokenVerifiedClaims({
          verification: { trust_framework: null },
          claims: { given_name: { max_age: 1 } },
        });
        expect(payload.verified_claims.claims.given_name).toEqual("Sarah");
      });

      xit("max_age: Optional. JSON number value only applicable to claims that contain dates or timestamps. The OP should try to fulfill this requirement. (nested evidence freshness — verification.evidence[].time / evidence[].check_details[].time — is NOT enforced: VerifiedClaimsAssembler applies max_age only to top-level verification elements and top-level claims, it does not recurse into the evidence array)", async () => {});
    });
  });

  describe("5.7 Returning less data than requested", () => {
    describe("5.7.2 Unavailable data", () => {
      it("If the OP does not have data about a certain claim, does not understand/support the respective claim, OPs shall omit the respective claim from any corresponding ID Token or UserInfo response.", async () => {
        // given_name is available; middle_name is not. The unavailable claim is dropped, but
        // verified_claims is still returned because at least one requested claim is available.
        const payload = await idTokenVerifiedClaims({
          verification: { trust_framework: null },
          claims: { given_name: null, middle_name: null },
        });
        expect(payload.verified_claims).toBeDefined();
        expect(payload.verified_claims.verification.trust_framework).toEqual("eidas");
        expect(payload.verified_claims.claims.given_name).toEqual("Sarah");
        expect(payload.verified_claims.claims).not.toHaveProperty("middle_name");
      });

      it("returns verified_claims with an empty claims object when all requested claims are unavailable ([IDA-verified-claims] §5.3: the claims element may be empty).", async () => {
        // middle_name and gender are not stored, so both are dropped individually. The claims object
        // is then empty, which the IDA verified_claims schema explicitly permits, so verified_claims
        // is still returned with verification — NOT omitted as a whole.
        const payload = await idTokenVerifiedClaims({
          verification: { trust_framework: null },
          claims: { middle_name: null, gender: null },
        });
        expect(payload.verified_claims).toBeDefined();
        expect(payload.verified_claims.verification.trust_framework).toEqual("eidas");
        expect(payload.verified_claims.claims).toEqual({});
      });

      // "does not understand/support the respective claim" — the unknown/unsupported-claim cases.
      it("If the OP does not have data about a certain claim, does not understand/support the respective claim, OPs shall omit the respective claim ... (unknown random-named claim — eKYC module #4 ekyc-server-unknown-claim-omitted)", async () => {
        const payload = await idTokenVerifiedClaims({
          verification: { trust_framework: null },
          claims: { given_name: null, unknown_random_claim_xyz: null },
        });
        expect(payload.verified_claims).toBeDefined();
        expect(payload.verified_claims.claims.given_name).toEqual("Sarah");
        expect(payload.verified_claims.claims).not.toHaveProperty("unknown_random_claim_xyz");
      });

      it("If the OP does not have data about a certain claim, does not understand/support the respective claim, OPs shall omit the respective claim ... (unknown claim requested with essential:true — eKYC module #5 ekyc-server-unknown-essential-claim-omitted)", async () => {
        const payload = await idTokenVerifiedClaims({
          verification: { trust_framework: null },
          claims: { given_name: null, unknown_essential_claim: { essential: true } },
        });
        expect(payload.verified_claims).toBeDefined();
        expect(payload.verified_claims.claims.given_name).toEqual("Sarah");
        expect(payload.verified_claims.claims).not.toHaveProperty("unknown_essential_claim");
      });

      it("If the OP does not have data about a certain claim, does not understand/support the respective claim, OPs shall omit the respective claim ... (unknown claim whose name contains special characters — eKYC module #6 ekyc-server-unknown-claim-specialchars-omitted)", async () => {
        const payload = await idTokenVerifiedClaims({
          verification: { trust_framework: null },
          claims: { given_name: null, "claim%1/a&,.b": null },
        });
        expect(payload.verified_claims).toBeDefined();
        expect(payload.verified_claims.claims.given_name).toEqual("Sarah");
        expect(payload.verified_claims.claims).not.toHaveProperty("claim%1/a&,.b");
      });
    });

    describe("5.7.3 Non-consented data", () => {
      it("the OP shall omit from any corresponding ID Token or UserInfo response data that has not had end-user consent for sharing.", async () => {
        // The RP requests verified given_name + family_name, but the end-user denies family_name on
        // the consent screen (denied_claims). The granted verified_claims must keep given_name and
        // omit family_name. (#1653 step②: denied claims are removed from the grant at authorize time.)
        const verifiedClaimsRequest = {
          verification: { trust_framework: null },
          claims: { given_name: null, family_name: null },
        };
        const { authorizationResponse } = await requestAuthorizations({
          endpoint: serverConfig.authorizationEndpoint,
          clientId: clientSecretPostClient.clientId,
          responseType: "code",
          state: "non_consented_verified_claims",
          scope: "openid " + clientSecretPostClient.scope,
          redirectUri: clientSecretPostClient.redirectUri,
          claims: JSON.stringify({ id_token: { verified_claims: verifiedClaimsRequest } }),
          user: idaVerifiedUser,
          deniedClaims: ["family_name"],
        });
        expect(authorizationResponse.code).not.toBeNull();

        const tokenResponse = await requestToken({
          endpoint: serverConfig.tokenEndpoint,
          code: authorizationResponse.code,
          grantType: "authorization_code",
          redirectUri: clientSecretPostClient.redirectUri,
          clientId: clientSecretPostClient.clientId,
          clientSecret: clientSecretPostClient.clientSecret,
        });
        expect(tokenResponse.status).toBe(200);

        const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
        const decodedIdToken = verifyAndDecodeJwt({
          jwt: tokenResponse.data.id_token,
          jwks: jwksResponse.data,
        });
        expect(decodedIdToken.verifyResult).toBe(true);
        const verifiedClaims = decodedIdToken.payload.verified_claims;
        console.log(JSON.stringify(verifiedClaims, null, 2));

        expect(verifiedClaims).toBeDefined();
        // consented verified claim is retained
        expect(verifiedClaims.claims.given_name).toBeDefined();
        // denied verified claim is omitted (§5.7.3)
        expect(verifiedClaims.claims.family_name).toBeUndefined();
      });
    });

    describe("5.7.4 Data not matching requirements", () => {
      it("If the respective requirement was expressed for a claim within verified_claims/verification, the OP shall omit the whole verified_claims element.", async () => {
        // RP constrains verification.trust_framework to "gold", but the user has "eidas" → the
        // verification requirement is unmet, so the whole verified_claims is omitted.
        const payload = await idTokenVerifiedClaims({
          verification: { trust_framework: { value: "gold" } },
          claims: { given_name: null },
        });
        expect(payload.verified_claims).toBeUndefined();
      });

      it("Otherwise, the OP shall omit the respective claim from the response.", async () => {
        // RP constrains given_name to "Bob" (mismatch → dropped), family_name is unconstrained (kept).
        const payload = await idTokenVerifiedClaims({
          verification: { trust_framework: null },
          claims: { given_name: { value: "Bob" }, family_name: null },
        });
        expect(payload.verified_claims).toBeDefined();
        expect(payload.verified_claims.claims.family_name).toEqual("Meredyth");
        expect(payload.verified_claims.claims).not.toHaveProperty("given_name");
      });

      it("In both cases, the OP shall not return an error to the RP.", async () => {
        // verification mismatch → whole verified_claims omitted, yet the token response is still 200
        // (asserted inside idTokenVerifiedClaims). No error is returned to the RP.
        const payload = await idTokenVerifiedClaims({
          verification: { trust_framework: { value: "gold" } },
          claims: { given_name: { value: "Bob" } },
        });
        expect(payload.verified_claims).toBeUndefined();
      });

      it("UserInfo: returns an empty claims object when the only requested claim fails its value constraint (eKYC module #7 ekyc-server-one-claim-with-random-value-omitted).", async () => {
        const { userinfo } = await userinfoVerifiedClaims({
          verification: { trust_framework: null },
          claims: { given_name: { value: "Bob" } },
        });
        expect(userinfo.verified_claims).toBeDefined();
        expect(userinfo.verified_claims.verification.trust_framework).toEqual("eidas");
        expect(userinfo.verified_claims.claims).toEqual({});
      });
    });
  });

  describe("7 Requesting verified claims", () => {
    it("The OP shall not provide the RP with any data it did not request. (IA-7 data minimization — only the requested claim appears in the response)", async () => {
      // Only given_name is requested; nothing else (family_name, birthdate, address, ...) may leak.
      const payload = await idTokenVerifiedClaims({
        verification: { trust_framework: null },
        claims: { given_name: null },
      });
      expect(payload.verified_claims).toBeDefined();
      expect(Object.keys(payload.verified_claims.claims)).toEqual(["given_name"]);
    });
  });

  describe("8 OP metadata", () => {
    let metadata;

    beforeAll(async () => {
      const response = await getConfiguration({
        endpoint: serverConfig.discoveryEndpoint,
      });
      expect(response.status).toBe(200);
      metadata = response.data;
    });

    it("verified_claims_supported: Boolean value indicating support for verified_claims. If omitted, the default value is false.", () => {
      expect(metadata.verified_claims_supported).toBe(true);
    });

    it("trust_frameworks_supported: REQUIRED. JSON array containing all supported trust frameworks. This array shall have at least one member.", () => {
      expect(Array.isArray(metadata.trust_frameworks_supported)).toBe(true);
      expect(metadata.trust_frameworks_supported.length).toBeGreaterThan(0);
    });

    it("claims_in_verified_claims_supported: REQUIRED. JSON array containing all claims supported within verified_claims. This array shall have at least one member.", () => {
      expect(Array.isArray(metadata.claims_in_verified_claims_supported)).toBe(true);
      expect(metadata.claims_in_verified_claims_supported.length).toBeGreaterThan(0);
    });

    it("evidence_supported: Required when one or more type of evidence is supported. JSON array containing all types of identity evidence the OP uses. This array shall have at least one member.", () => {
      expect(Array.isArray(metadata.evidence_supported)).toBe(true);
      expect(metadata.evidence_supported.length).toBeGreaterThan(0);
      // OIDC4IDA 1.0 evidence types only; pre-1.0 draft values (id_document/utility_bill/qes) removed.
      expect(metadata.evidence_supported).toContain("document");
      expect(metadata.evidence_supported).toContain("electronic_record");
      expect(metadata.evidence_supported).not.toContain("id_document");
    });

    it('documents_supported: Required when evidence_supported contains "document". JSON array containing all identity document types utilized by the OP for identity verification.', () => {
      expect(Array.isArray(metadata.documents_supported)).toBe(true);
      expect(metadata.documents_supported).toContain("passport");
    });

    it('documents_methods_supported: Optional. JSON array containing the verification methods the OP supports for evidences of type "document".', () => {
      expect(metadata.documents_methods_supported).toContain("pipp");
    });

    it('documents_check_methods_supported: Optional. JSON array containing the check methods the OP supports for evidences of type "document".', () => {
      expect(metadata.documents_check_methods_supported).toContain("vpip");
    });

    it('electronic_records_supported: Required when evidence_supported contains "electronic_record". JSON array containing all electronic record types the OP supports. When present this array shall have at least one member.', () => {
      expect(Array.isArray(metadata.electronic_records_supported)).toBe(true);
      expect(metadata.electronic_records_supported.length).toBeGreaterThan(0);
    });

    it("legacy pre-1.0 draft names id_documents_supported / id_documents_verification_methods_supported are no longer advertised", () => {
      expect(metadata).not.toHaveProperty("id_documents_supported");
      expect(metadata).not.toHaveProperty("id_documents_verification_methods_supported");
    });
  });

  // conformance E group (IA-8): every value returned inside verified_claims must be advertised in the
  // OP's *_supported metadata (ValidateVerifiedClaimsInIdTokenAgainstOPMetadata / ...InUserinfo...).
  describe("IA-8 returned verified_claims must be within the OP metadata", () => {
    it("the trust_framework returned in verified_claims/verification is advertised in trust_frameworks_supported.", async () => {
      const metaResponse = await getConfiguration({ endpoint: serverConfig.discoveryEndpoint });
      const payload = await idTokenVerifiedClaims({
        verification: { trust_framework: null },
        claims: { given_name: null },
      });
      expect(metaResponse.data.trust_frameworks_supported).toContain(
        payload.verified_claims.verification.trust_framework
      );
    });

    it("every claim name returned in verified_claims/claims is advertised in claims_in_verified_claims_supported.", async () => {
      const metaResponse = await getConfiguration({ endpoint: serverConfig.discoveryEndpoint });
      const payload = await idTokenVerifiedClaims({
        verification: { trust_framework: null },
        claims: { given_name: null, family_name: null },
      });
      Object.keys(payload.verified_claims.claims).forEach((name) => {
        expect(metaResponse.data.claims_in_verified_claims_supported).toContain(name);
      });
    });
  });

  // `purpose` claims-request member: intentionally NOT tracked in this ledger. It was defined in
  // OIDC4IDA Implementer's Draft §5.1 but was REMOVED from the OIDC4IDA 1.0 final spec (1 Oct 2024),
  // and it is not in OpenID Connect Core §5.5.1 (essential / value / values only) — so it is not a
  // requirement of any spec this conformance ledger tracks. The OIDF eKYC test plan
  // (EKYCWithOIDCCoreTestPlan) only documents it as an unimplemented TODO (tag IA-9), not a runnable
  // test module. A reference implementation (3–300 length validation → invalid_request, plus
  // HTML-escaped exposure in the authorization view-data) lives on branch feat/ida-purpose-1651
  // (see #1651), to be revisited if/when purpose returns to the spec.

});

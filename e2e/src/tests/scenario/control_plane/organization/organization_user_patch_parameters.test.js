import { describe, expect, it, beforeAll } from "@jest/globals";
import { deletion, get, patchWithJson, postWithJson } from "../../../../lib/http";
import { backendUrl } from "../../../testConfig";
import { requestToken } from "../../../../api/oauthClient";
import { v4 as uuidv4 } from "uuid";

/**
 * Organization User Management - PATCH API Parameter Test
 *
 * This test validates PATCH API behavior for individual parameter updates:
 * 1. Each parameter can be updated independently
 * 2. Only specified parameters are changed (partial update)
 * 3. Unchanged parameters remain intact
 * 4. Response diff correctly reflects changes
 */
describe("organization user management - PATCH parameter tests", () => {
  const orgId = "72cf4a12-8da3-40fb-8ae4-a77e3cda95e2";
  const tenantId = "952f6906-3e95-4ed3-86b2-981f90f785f9";
  const userStatus = "REGISTERED";

  let accessToken;
  let userId;
  let timestamp;

  beforeAll(async () => {
    // Get OAuth token
    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/952f6906-3e95-4ed3-86b2-981f90f785f9/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro@gmail.com",
      password: "successUserCode001",
      scope: "org-management account management",
      clientId: "org-client",
      clientSecret: "org-client-001"
    });
    expect(tokenResponse.status).toBe(200);
    accessToken = tokenResponse.data.access_token;

    // Create test user
    timestamp = Date.now();
    userId = uuidv4();
    const createResponse = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users`,
      headers: { Authorization: `Bearer ${accessToken}` },
      body: {
        "sub": userId,
        "provider_id": "idp-server",
        "external_user_id": `ext_${timestamp}`,
        "name": `Test User ${timestamp}`,
        "given_name": "Test",
        "family_name": `User${timestamp}`,
        "middle_name": "Middle",
        "nickname": `testuser${timestamp}`,
        "preferred_username": `testuser${timestamp}`,
        "profile": `https://example.com/profile/${timestamp}`,
        "picture": `https://example.com/picture/${timestamp}.jpg`,
        "website": `https://example.com/website/${timestamp}`,
        "email": `testuser${timestamp}@example.com`,
        "email_verified": true,
        "phone_number": "+81-90-1234-5678",
        "phone_number_verified": true,
        "gender": "other",
        "birthdate": "1990-05-15",
        "zoneinfo": "Asia/Tokyo",
        "locale": "ja-JP",
        "address": {
          "street_address": "1-2-3 Test Street",
          "locality": "Shibuya",
          "region": "Tokyo",
          "postal_code": "150-0001",
          "country": "JP"
        },
        "raw_password": "TestPassword123!",
        "custom_properties": {
          "department": "Engineering",
          "employee_id": `EMP${timestamp}`
        },
        status: userStatus,
      }
    });
    expect(createResponse.status).toBe(201);
    console.log(`✅ Test user created: ${userId}`);
  });

  afterAll(async () => {
    // Cleanup test user
    try {
      await deletion({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      console.log(`🧹 Test user deleted: ${userId}`);
    } catch (error) {
      console.warn(`⚠️ Failed to delete test user: ${error.message}`);
    }
  });

  describe("PATCH - Basic profile fields", () => {
    it("should update name only", async () => {
      const newName = `Updated Name ${timestamp}`;
      const response = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: { "name": newName }
      });

      expect(response.status).toBe(200);
      expect(response.data.result).toHaveProperty("name", newName);

      // Verify diff shows only changed field
      expect(response.data).toHaveProperty("diff");
      expect(response.data.diff).toHaveProperty("name");
      expect(response.data.diff.name.after).toEqual(newName);

      expect(Object.keys(response.data.diff)).toHaveLength(1);

      // Verify other fields unchanged
      expect(response.data.result.given_name).toBe("Test");
      expect(response.data.result.family_name).toBe(`User${timestamp}`);

      // Verify status unchanged
      expect(response.data.result.status).toBe(userStatus);

      // Verify by GET
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(getResponse.status).toBe(200);
      expect(getResponse.data.name).toBe(newName);
      expect(getResponse.data.given_name).toBe("Test");
      expect(getResponse.data.status).toBe(userStatus);

      console.log("✅ Name updated successfully with correct diff");
    });

    it("should update given_name only", async () => {
      const newGivenName = "NewGiven";
      const response = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: { "given_name": newGivenName }
      });

      expect(response.status).toBe(200);
      expect(response.data.result.given_name).toBe(newGivenName);

      // Verify diff shows only changed field
      expect(response.data).toHaveProperty("diff");
      expect(response.data.diff).toHaveProperty("given_name");
      expect(response.data.diff.given_name.after).toEqual(newGivenName);
      expect(Object.keys(response.data.diff)).toHaveLength(1);

      // Verify status unchanged
      expect(response.data.result.status).toBe(userStatus);

      // Verify by GET
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(getResponse.status).toBe(200);
      expect(getResponse.data.given_name).toBe(newGivenName);
      expect(getResponse.data.status).toBe(userStatus);

      console.log("✅ Given name updated successfully with correct diff");
    });

    it("should update family_name only", async () => {
      const newFamilyName = "NewFamily";
      const response = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: { "family_name": newFamilyName }
      });

      expect(response.status).toBe(200);
      expect(response.data.result.family_name).toBe(newFamilyName);

      // Verify diff shows only changed field
      expect(response.data).toHaveProperty("diff");
      expect(response.data.diff).toHaveProperty("family_name");
      expect(response.data.diff.family_name).toHaveProperty("before");
      expect(response.data.diff.family_name).toHaveProperty("after");
      expect(response.data.diff.family_name.after).toEqual(newFamilyName);
      expect(Object.keys(response.data.diff)).toHaveLength(1);

      // Verify status unchanged
      expect(response.data.result.status).toBe(userStatus);

      // Verify by GET
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(getResponse.status).toBe(200);
      expect(getResponse.data.family_name).toBe(newFamilyName);
      expect(getResponse.data.status).toBe(userStatus);

      console.log("✅ Family name updated successfully with correct diff");
    });

    it("should update middle_name only", async () => {
      const newMiddleName = "NewMiddle";
      const response = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: { "middle_name": newMiddleName }
      });

      expect(response.status).toBe(200);
      expect(response.data.result.middle_name).toBe(newMiddleName);

      // Verify diff shows only changed field
      expect(response.data).toHaveProperty("diff");
      expect(response.data.diff).toHaveProperty("middle_name");
      expect(response.data.diff.middle_name).toHaveProperty("before");
      expect(response.data.diff.middle_name).toHaveProperty("after");
      expect(response.data.diff.middle_name.after).toEqual(newMiddleName);
      expect(Object.keys(response.data.diff)).toHaveLength(1);

      // Verify status unchanged
      expect(response.data.result.status).toBe(userStatus);

      // Verify by GET
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(getResponse.status).toBe(200);
      expect(getResponse.data.middle_name).toBe(newMiddleName);
      expect(getResponse.data.status).toBe(userStatus);

      console.log("✅ Middle name updated successfully with correct diff");
    });

    it("should update nickname only", async () => {
      const newNickname = `newnick${timestamp}`;
      const response = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: { "nickname": newNickname }
      });

      expect(response.status).toBe(200);
      expect(response.data.result.nickname).toBe(newNickname);

      // Verify diff shows only changed field
      expect(response.data).toHaveProperty("diff");
      expect(response.data.diff).toHaveProperty("nickname");
      expect(response.data.diff.nickname).toHaveProperty("before");
      expect(response.data.diff.nickname).toHaveProperty("after");
      expect(response.data.diff.nickname.after).toEqual(newNickname);
      expect(Object.keys(response.data.diff)).toHaveLength(1);

      // Verify status unchanged
      expect(response.data.result.status).toBe(userStatus);

      // Verify by GET
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(getResponse.status).toBe(200);
      expect(getResponse.data.nickname).toBe(newNickname);
      expect(getResponse.data.status).toBe(userStatus);

      console.log("✅ Nickname updated successfully with correct diff");
    });

    it("should update preferred_username only", async () => {
      const newPreferredUsername = `newpreferred${timestamp}`;
      const response = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: { "preferred_username": newPreferredUsername }
      });

      expect(response.status).toBe(200);
      expect(response.data.result.preferred_username).toBe(newPreferredUsername);

      // Verify diff shows only changed field
      expect(response.data).toHaveProperty("diff");
      expect(response.data.diff).toHaveProperty("preferred_username");
      expect(response.data.diff.preferred_username).toHaveProperty("before");
      expect(response.data.diff.preferred_username).toHaveProperty("after");
      expect(response.data.diff.preferred_username.after).toEqual(newPreferredUsername);
      expect(Object.keys(response.data.diff)).toHaveLength(1);

      // Verify status unchanged
      expect(response.data.result.status).toBe(userStatus);

      // Verify by GET
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(getResponse.status).toBe(200);
      expect(getResponse.data.preferred_username).toBe(newPreferredUsername);
      expect(getResponse.data.status).toBe(userStatus);

      console.log("✅ Preferred username updated successfully with correct diff");
    });
  });

  describe("PATCH - Contact information", () => {
    it("should update email only", async () => {
      const newEmail = `newemail${timestamp}@example.com`;
      const response = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: { "email": newEmail }
      });

      expect(response.status).toBe(200);
      expect(response.data.result.email).toBe(newEmail);

      // Verify diff shows changed fields (email update may trigger preferred_username update)
      expect(response.data).toHaveProperty("diff");
      expect(response.data.diff).toHaveProperty("email");
      expect(response.data.diff.email).toHaveProperty("before");
      expect(response.data.diff.email).toHaveProperty("after");
      expect(response.data.diff.email.after).toEqual(newEmail);

      // Verify status unchanged
      expect(response.data.result.status).toBe(userStatus);

      // Verify by GET
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(getResponse.status).toBe(200);
      expect(getResponse.data.email).toBe(newEmail);
      expect(getResponse.data.status).toBe(userStatus);

      console.log("✅ Email updated successfully with correct diff");
    });

    it("should update phone_number only", async () => {
      const newPhoneNumber = "+81-90-9876-5432";
      const response = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: { "phone_number": newPhoneNumber }
      });

      expect(response.status).toBe(200);
      expect(response.data.result.phone_number).toBe(newPhoneNumber);

      // Verify diff shows only changed field
      expect(response.data).toHaveProperty("diff");
      expect(response.data.diff).toHaveProperty("phone_number");
      expect(response.data.diff.phone_number).toHaveProperty("before");
      expect(response.data.diff.phone_number).toHaveProperty("after");
      expect(response.data.diff.phone_number.after).toEqual(newPhoneNumber);

      expect(Object.keys(response.data.diff)).toHaveLength(1);

      // Verify status unchanged
      expect(response.data.result.status).toBe(userStatus);

      // Verify by GET
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(getResponse.status).toBe(200);
      expect(getResponse.data.phone_number).toBe(newPhoneNumber);
      expect(getResponse.data.status).toBe(userStatus);

      console.log("✅ Phone number updated successfully with correct diff");
    });
  });

  describe("PATCH - Profile URLs", () => {
    it("should update profile URL only", async () => {
      const newProfile = `https://example.com/newprofile/${timestamp}`;
      const response = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: { "profile": newProfile }
      });

      expect(response.status).toBe(200);
      expect(response.data.result.profile).toBe(newProfile);

      // Verify diff shows only changed field
      expect(response.data).toHaveProperty("diff");
      expect(response.data.diff).toHaveProperty("profile");
      expect(response.data.diff.profile).toHaveProperty("before");
      expect(response.data.diff.profile).toHaveProperty("after");
      expect(response.data.diff.profile.after).toEqual(newProfile);
      expect(Object.keys(response.data.diff)).toHaveLength(1);

      // Verify status unchanged
      expect(response.data.result.status).toBe(userStatus);

      // Verify by GET
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(getResponse.status).toBe(200);
      expect(getResponse.data.profile).toBe(newProfile);
      expect(getResponse.data.status).toBe(userStatus);

      console.log("✅ Profile URL updated successfully with correct diff");
    });

    it("should update picture URL only", async () => {
      const newPicture = `https://example.com/newpicture/${timestamp}.jpg`;
      const response = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: { "picture": newPicture }
      });

      expect(response.status).toBe(200);
      expect(response.data.result.picture).toBe(newPicture);

      // Verify diff shows only changed field
      expect(response.data).toHaveProperty("diff");
      expect(response.data.diff).toHaveProperty("picture");
      expect(response.data.diff.picture).toHaveProperty("before");
      expect(response.data.diff.picture).toHaveProperty("after");
      expect(response.data.diff.picture.after).toEqual(newPicture);
      expect(Object.keys(response.data.diff)).toHaveLength(1);

      // Verify status unchanged
      expect(response.data.result.status).toBe(userStatus);

      // Verify by GET
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(getResponse.status).toBe(200);
      expect(getResponse.data.picture).toBe(newPicture);
      expect(getResponse.data.status).toBe(userStatus);

      console.log("✅ Picture URL updated successfully with correct diff");
    });

    it("should update website URL only", async () => {
      const newWebsite = `https://example.com/newwebsite/${timestamp}`;
      const response = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: { "website": newWebsite }
      });

      expect(response.status).toBe(200);
      expect(response.data.result.website).toBe(newWebsite);

      // Verify diff shows only changed field
      expect(response.data).toHaveProperty("diff");
      expect(response.data.diff).toHaveProperty("website");
      expect(response.data.diff.website).toHaveProperty("before");
      expect(response.data.diff.website).toHaveProperty("after");
      expect(response.data.diff.website.after).toEqual(newWebsite);
      expect(Object.keys(response.data.diff)).toHaveLength(1);

      // Verify status unchanged
      expect(response.data.result.status).toBe(userStatus);

      // Verify by GET
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(getResponse.status).toBe(200);
      expect(getResponse.data.website).toBe(newWebsite);
      expect(getResponse.data.status).toBe(userStatus);

      console.log("✅ Website URL updated successfully with correct diff");
    });
  });

  describe("PATCH - Demographics", () => {
    it("should update gender only", async () => {
      const newGender = "male";
      const response = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: { "gender": newGender }
      });

      expect(response.status).toBe(200);
      expect(response.data.result.gender).toBe(newGender);

      // Verify diff shows only changed field
      expect(response.data).toHaveProperty("diff");
      expect(response.data.diff).toHaveProperty("gender");
      expect(response.data.diff.gender).toHaveProperty("before");
      expect(response.data.diff.gender).toHaveProperty("after");
      expect(response.data.diff.gender.after).toEqual(newGender);
      expect(Object.keys(response.data.diff)).toHaveLength(1);

      // Verify status unchanged
      expect(response.data.result.status).toBe(userStatus);

      // Verify by GET
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(getResponse.status).toBe(200);
      expect(getResponse.data.gender).toBe(newGender);
      expect(getResponse.data.status).toBe(userStatus);

      console.log("✅ Gender updated successfully with correct diff");
    });

    it("should update birthdate only", async () => {
      const newBirthdate = "1985-12-25";
      const response = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: { "birthdate": newBirthdate }
      });

      expect(response.status).toBe(200);
      expect(response.data.result.birthdate).toBe(newBirthdate);

      // Verify diff shows only changed field
      expect(response.data).toHaveProperty("diff");
      expect(response.data.diff).toHaveProperty("birthdate");
      expect(response.data.diff.birthdate).toHaveProperty("before");
      expect(response.data.diff.birthdate).toHaveProperty("after");
      expect(response.data.diff.birthdate.after).toEqual(newBirthdate);
      expect(Object.keys(response.data.diff)).toHaveLength(1);

      // Verify status unchanged
      expect(response.data.result.status).toBe(userStatus);

      // Verify by GET
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(getResponse.status).toBe(200);
      expect(getResponse.data.birthdate).toBe(newBirthdate);
      expect(getResponse.data.status).toBe(userStatus);

      console.log("✅ Birthdate updated successfully with correct diff");
    });

    it("should update zoneinfo only", async () => {
      const newZoneinfo = "America/New_York";
      const response = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: { "zoneinfo": newZoneinfo }
      });

      expect(response.status).toBe(200);
      expect(response.data.result.zoneinfo).toBe(newZoneinfo);

      // Verify diff shows only changed field
      expect(response.data).toHaveProperty("diff");
      expect(response.data.diff).toHaveProperty("zoneinfo");
      expect(response.data.diff.zoneinfo).toHaveProperty("before");
      expect(response.data.diff.zoneinfo).toHaveProperty("after");
      expect(response.data.diff.zoneinfo.after).toEqual(newZoneinfo);
      expect(Object.keys(response.data.diff)).toHaveLength(1);

      // Verify status unchanged
      expect(response.data.result.status).toBe(userStatus);

      // Verify by GET
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(getResponse.status).toBe(200);
      expect(getResponse.data.zoneinfo).toBe(newZoneinfo);
      expect(getResponse.data.status).toBe(userStatus);

      console.log("✅ Zoneinfo updated successfully with correct diff");
    });

    it("should update locale only", async () => {
      const newLocale = "en-US";
      const response = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: { "locale": newLocale }
      });

      expect(response.status).toBe(200);
      expect(response.data.result.locale).toBe(newLocale);

      // Verify diff shows only changed field
      expect(response.data).toHaveProperty("diff");
      expect(response.data.diff).toHaveProperty("locale");
      expect(response.data.diff.locale).toHaveProperty("before");
      expect(response.data.diff.locale).toHaveProperty("after");
      expect(response.data.diff.locale.after).toEqual(newLocale);
      expect(Object.keys(response.data.diff)).toHaveLength(1);

      // Verify status unchanged
      expect(response.data.result.status).toBe(userStatus);

      // Verify by GET
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(getResponse.status).toBe(200);
      expect(getResponse.data.locale).toBe(newLocale);
      expect(getResponse.data.status).toBe(userStatus);

      console.log("✅ Locale updated successfully with correct diff");
    });
  });

  describe("PATCH - Address (partial object update)", () => {
    it("should update street_address only", async () => {
      const newStreetAddress = "9-8-7 New Street";
      const response = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          "address": {
            "street_address": newStreetAddress
          }
        }
      });

      expect(response.status).toBe(200);
      expect(response.data.result.address.street_address).toBe(newStreetAddress);

      // Note: Address is replaced, not merged (other fields will be undefined)
      // This is the actual API behavior

      // Verify diff shows address field using dot notation key
      expect(response.data).toHaveProperty("diff");
      expect(response.data.diff["address.street_address"].after).toBe(newStreetAddress);

      // Verify status unchanged
      expect(response.data.result.status).toBe(userStatus);

      // Verify by GET
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(getResponse.status).toBe(200);
      expect(getResponse.data.address.street_address).toBe(newStreetAddress);
      expect(getResponse.data.status).toBe(userStatus);

      console.log("✅ Street address updated successfully with correct diff");
    });

    it("should update postal_code only", async () => {
      const newPostalCode = "100-0001";
      const response = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          "address": {
            "postal_code": newPostalCode
          }
        }
      });

      expect(response.status).toBe(200);
      expect(response.data.result.address.postal_code).toBe(newPostalCode);

      // Note: Address is replaced, not merged (other fields will be undefined)
      // This is the actual API behavior

      // Verify diff shows address field using dot notation key
      expect(response.data).toHaveProperty("diff");
      expect(response.data.diff["address.postal_code"].after).toBe(newPostalCode);

      // Verify status unchanged
      expect(response.data.result.status).toBe(userStatus);

      // Verify by GET
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(getResponse.status).toBe(200);
      expect(getResponse.data.address.postal_code).toBe(newPostalCode);
      expect(getResponse.data.status).toBe(userStatus);

      console.log("✅ Postal code updated successfully with correct diff");
    });
  });

  describe("PATCH - Custom properties (partial object update)", () => {
    it("should update department in custom_properties only", async () => {
      const newDepartment = "Marketing";
      const response = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          "custom_properties": {
            "department": newDepartment
          }
        }
      });

      expect(response.status).toBe(200);
      expect(response.data.result.custom_properties.department).toBe(newDepartment);

      // Note: Custom properties are replaced, not merged (other fields will be undefined)
      // This is the actual API behavior

      // Verify diff shows custom_properties field using dot notation key
      expect(response.data).toHaveProperty("diff");
      expect(response.data.diff["custom_properties.department"].after).toBe(newDepartment);

      // Verify status unchanged
      expect(response.data.result.status).toBe(userStatus);

      // Verify by GET
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(getResponse.status).toBe(200);
      expect(getResponse.data.custom_properties.department).toBe(newDepartment);
      expect(getResponse.data.status).toBe(userStatus);

      console.log("✅ Custom property (department) updated successfully with correct diff");
    });

    it("should add new custom property (replaces all existing ones)", async () => {
      const response = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          "custom_properties": {
            "location": "Tokyo Office"
          }
        }
      });

      expect(response.status).toBe(200);
      expect(response.data.result.custom_properties.location).toBe("Tokyo Office");

      // Note: Custom properties are replaced, not merged (previous department field is lost)
      // This is the actual API behavior

      // Verify diff shows custom_properties field using dot notation key
      expect(response.data).toHaveProperty("diff");
      expect(response.data.diff["custom_properties.location"].after).toBe("Tokyo Office");

      // Verify status unchanged
      expect(response.data.result.status).toBe(userStatus);

      // Verify by GET
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(getResponse.status).toBe(200);
      expect(getResponse.data.custom_properties.location).toBe("Tokyo Office");
      expect(getResponse.data.status).toBe(userStatus);

      console.log("✅ New custom property added successfully (previous properties replaced)");
    });
  });

  describe("PATCH - Multiple fields simultaneously", () => {
    it("should update name and email together", async () => {
      const newName = `Final Name ${timestamp}`;
      const newEmail = `finalemail${timestamp}@example.com`;

      const response = await patchWithJson({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` },
        body: {
          "name": newName,
          "email": newEmail
        }
      });

      expect(response.status).toBe(200);
      expect(response.data.result.name).toBe(newName);
      expect(response.data.result.email).toBe(newEmail);

      // Verify diff shows changed fields (may include preferred_username if email triggers it)
      expect(response.data).toHaveProperty("diff");
      expect(response.data.diff).toHaveProperty("name");
      expect(response.data.diff.name).toHaveProperty("before");
      expect(response.data.diff.name).toHaveProperty("after");
      expect(response.data.diff.name.after).toEqual(newName);
      expect(response.data.diff).toHaveProperty("email");
      expect(response.data.diff.email).toHaveProperty("before");
      expect(response.data.diff.email).toHaveProperty("after");
      expect(response.data.diff.email.after).toEqual(newEmail);

      // Verify status unchanged
      expect(response.data.result.status).toBe(userStatus);

      // Verify by GET
      const getResponse = await get({
        url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/users/${userId}`,
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      expect(getResponse.status).toBe(200);
      expect(getResponse.data.name).toBe(newName);
      expect(getResponse.data.email).toBe(newEmail);
      expect(getResponse.data.status).toBe(userStatus);

      console.log("✅ Multiple fields updated successfully with correct diff");
    });
  });
});

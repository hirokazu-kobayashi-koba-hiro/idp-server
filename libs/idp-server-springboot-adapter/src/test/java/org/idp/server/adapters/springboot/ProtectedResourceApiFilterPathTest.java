/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.idp.server.adapters.springboot;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProtectedResourceApiFilterPathTest {

  static final String TENANT = "/67e7eae6-62b0-4500-9eff-87459f63fc66";

  @Test
  void coversTheResourceOwnersOwnApis() {
    assertTrue(ProtectedResourceApiFilter.isResourceOwnerPath(TENANT + "/v1/me"));
    assertTrue(
        ProtectedResourceApiFilter.isResourceOwnerPath(TENANT + "/v1/me/linked-external-accounts"));
    assertTrue(
        ProtectedResourceApiFilter.isResourceOwnerPath(
            "/idp-admin" + TENANT + "/v1/me/identity-verification/applications"));
  }

  @Test
  void leavesAPathWhoseSegmentOnlyStartsWithMeAlone() {
    // A linking provider named "medium": its browser callback and its token retrieval carry no
    // Bearer token.
    assertFalse(
        ProtectedResourceApiFilter.isResourceOwnerPath(TENANT + "/v1/linking/callback/medium"));
    assertFalse(
        ProtectedResourceApiFilter.isResourceOwnerPath(
            TENANT + "/v1/linked-external-accounts/medium-k3f9x2qa/token"));
    assertFalse(ProtectedResourceApiFilter.isResourceOwnerPath(TENANT + "/v1/metadata"));
  }

  @Test
  void leavesTheManagementApisToTheirOwnFilters() {
    assertFalse(
        ProtectedResourceApiFilter.isResourceOwnerPath(
            "/v1/management/tenants" + TENANT + "/v1/me"));
  }
}

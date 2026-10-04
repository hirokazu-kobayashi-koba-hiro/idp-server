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

package org.idp.server.core.openid.authentication;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.type.oauth.CustomParamSource;
import org.idp.server.core.openid.oauth.type.oauth.CustomParams;
import org.junit.jupiter.api.Test;

/**
 * Issue #1907: what each custom parameter can be trusted as, decided at the authorization endpoint.
 */
class AuthenticationCustomParamsTest {

  @Test
  void keepsTheSourcesOfARequestThatWasNotPushed() {
    CustomParams customParams =
        CustomParams.of(Map.of("variant", "dark"), CustomParamSource.QUERY)
            .assembledWith(
                CustomParams.of(Map.of("member_no", "A123"), CustomParamSource.REQUEST_OBJECT));

    AuthenticationCustomParams params =
        AuthenticationCustomParams.of(customParams, false, true, true);

    assertEquals(CustomParamSource.REQUEST_OBJECT, params.sourceOf("member_no"));
    assertEquals(CustomParamSource.QUERY, params.sourceOf("variant"));
  }

  @Test
  void countsAValueOfAnUnsignedRequestObjectAsTheQuery() {
    CustomParams customParams =
        CustomParams.of(Map.of("member_no", "A123"), CustomParamSource.REQUEST_OBJECT);

    AuthenticationCustomParams params =
        AuthenticationCustomParams.of(customParams, false, true, false);

    assertEquals(CustomParamSource.QUERY, params.sourceOf("member_no"));
  }

  @Test
  void countsAPushedRequestReadBackFromStorageAsPushed() {
    CustomParams stored = new CustomParams(Map.of("member_no", "A123"));

    AuthenticationCustomParams params = AuthenticationCustomParams.of(stored, true, true, false);

    assertEquals(CustomParamSource.PUSHED, params.sourceOf("member_no"));
  }

  @Test
  void countsAPublicClientsPushedRequestAsTheQuery() {
    CustomParams stored = new CustomParams(Map.of("member_no", "A123"));

    AuthenticationCustomParams params = AuthenticationCustomParams.of(stored, true, false, false);

    assertEquals(CustomParamSource.QUERY, params.sourceOf("member_no"));
  }

  @Test
  void countsAValueOfUnknownSourceAsTheQuery() {
    CustomParams stored = new CustomParams(Map.of("member_no", "A123"));

    AuthenticationCustomParams params = AuthenticationCustomParams.of(stored, false, true, true);

    assertEquals(CustomParamSource.QUERY, params.sourceOf("member_no"));
  }

  @Test
  void survivesTheRoundTripThroughStorage() {
    AuthenticationCustomParams params =
        AuthenticationCustomParams.of(
            CustomParams.of(Map.of("member_no", "A123"), CustomParamSource.REQUEST_OBJECT),
            false,
            true,
            true);

    AuthenticationCustomParams read = AuthenticationCustomParams.fromMap(params.toMap());

    assertEquals(
        Map.of("member_no", "A123"), read.valuesFrom(List.of(CustomParamSource.REQUEST_OBJECT)));
    assertEquals(Map.of(), read.valuesFrom(List.of(CustomParamSource.QUERY)));
  }

  @Test
  void leavesTheCustomParamsOutOfWhatTheDeviceSees() {
    AuthenticationContext context =
        new AuthenticationContext(
            new org.idp.server.core.openid.oauth.type.oidc.AcrValues(),
            new org.idp.server.core.openid.oauth.type.oauth.Scopes("openid"),
            new org.idp.server.core.openid.oauth.type.ciba.BindingMessage(),
            new org.idp.server.core.openid.oauth.rar.AuthorizationDetails(),
            AuthenticationCustomParams.of(
                CustomParams.of(Map.of("member_no", "A123"), CustomParamSource.PUSHED),
                true,
                true,
                false));

    assertTrue(context.toMap().containsKey("custom_params"));
    assertFalse(context.toMapForPublic().containsKey("custom_params"));
  }
}

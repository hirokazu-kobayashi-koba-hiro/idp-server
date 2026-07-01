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

package org.idp.server.core.openid.identity.id_token.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrantBuilder;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.type.oauth.GrantType;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.api.Test;

/**
 * Issue #1699: a {@code claims:<custom_property>} whose value is null must be omitted from the ID
 * token (not put as {@code "key": null}).
 */
class ScopeMappingCustomClaimsCreatorTest {

  @Test
  void omitsNullCustomPropertyButKeepsNonNull() {
    HashMap<String, Object> props = new HashMap<>();
    props.put("nullprop", null);
    props.put("realprop", "value");
    User user = new User().setSub(UUID.randomUUID().toString()).setCustomProperties(props);

    AuthorizationGrant grant =
        new AuthorizationGrantBuilder(
                new TenantIdentifier(UUID.randomUUID().toString()),
                new RequestedClientId("test-client"),
                GrantType.authorization_code,
                new Scopes("claims:nullprop claims:realprop"))
            .add(user)
            .build();

    ScopeMappingCustomClaimsCreator creator = new ScopeMappingCustomClaimsCreator();
    Map<String, Object> claims = creator.create(user, null, grant, null, null, null, null);

    assertFalse(claims.containsKey("nullprop"), "null custom property must be omitted");
    assertEquals("value", claims.get("realprop"));
  }
}

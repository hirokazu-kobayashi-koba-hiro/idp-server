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

package org.idp.server.core.openid.identity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the difference between {@link User#enrichWith(User)} and {@link User#updateWith(User)} for
 * {@code custom_properties}.
 *
 * <p>Several authentication methods write into the same flat key set and each only knows its own
 * keys, so an authentication flow has to merge; whichever method ran last would otherwise drop the
 * keys the others had put there (#1772). The management PATCH endpoint keeps replacing, because
 * there the caller states the map it wants and can drop keys on purpose.
 */
class UserEnrichWithTest {

  private static HashMap<String, Object> props(Object... keyValues) {
    HashMap<String, Object> map = new HashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put((String) keyValues[i], keyValues[i + 1]);
    }
    return map;
  }

  private static User userWith(HashMap<String, Object> customProperties) {
    User user = new User();
    user.setSub("sub-1");
    user.setCustomProperties(customProperties);
    return user;
  }

  private static User patchWith(HashMap<String, Object> customProperties) {
    User patch = new User();
    patch.setCustomProperties(customProperties);
    return patch;
  }

  @Nested
  class CustomProperties {

    @Test
    void keysFromDifferentAuthenticationMethodsAccumulate() {
      // federation resolved attr_a / attr_b, then external-api authentication resolves attr_c.
      User afterFederation = userWith(props("attr_a", "a", "attr_b", "b"));

      User afterExternalApi = afterFederation.enrichWith(patchWith(props("attr_c", "c")));

      assertEquals(
          Map.of("attr_a", "a", "attr_b", "b", "attr_c", "c"),
          afterExternalApi.customPropertiesValue());
    }

    @Test
    void sameKeyIsOverwrittenByThePatch() {
      User existing = userWith(props("attr_a", "old", "attr_b", "b"));

      User enriched = existing.enrichWith(patchWith(props("attr_a", "new")));

      assertEquals(Map.of("attr_a", "new", "attr_b", "b"), enriched.customPropertiesValue());
    }

    @Test
    void patchWithoutCustomPropertiesKeepsTheExistingOnes() {
      // e.g. a password method whose user_mapping_rules only carry provider_id / external_user_id.
      User existing = userWith(props("attr_a", "a"));

      User enriched = existing.enrichWith(new User());

      assertEquals(Map.of("attr_a", "a"), enriched.customPropertiesValue());
    }

    @Test
    void aKeyWithNoProducedValueKeepsItsExistingValue() {
      // An unresolved `from` yields a null entry (MappingRuleObjectMapper writes null), so copying
      // the patch wholesale would erase the attribute just because this method's source lacked it.
      User existing = userWith(props("rank", "gold", "tier", "premium"));

      HashMap<String, Object> patchProps = props("rank", "silver");
      patchProps.put("tier", null);

      User enriched = existing.enrichWith(patchWith(patchProps));

      assertEquals(Map.of("rank", "silver", "tier", "premium"), enriched.customPropertiesValue());
    }

    @Test
    void aPatchOfOnlyNullsLeavesEverythingAsItWas() {
      User existing = userWith(props("rank", "gold"));

      HashMap<String, Object> patchProps = new HashMap<>();
      patchProps.put("rank", null);

      User enriched = existing.enrichWith(patchWith(patchProps));

      assertEquals(Map.of("rank", "gold"), enriched.customPropertiesValue());
    }

    @Test
    void enrichingAnEmptyUserYieldsThePatchProperties() {
      User existing = userWith(props());

      User enriched = existing.enrichWith(patchWith(props("attr_c", "c")));

      assertEquals(Map.of("attr_c", "c"), enriched.customPropertiesValue());
    }

    @Test
    void theReceiverAndThePatchAreNotMutated() {
      HashMap<String, Object> existingProps = props("attr_a", "a");
      HashMap<String, Object> patchProps = props("attr_c", "c");
      User existing = userWith(existingProps);
      User patch = patchWith(patchProps);

      existing.enrichWith(patch);

      assertEquals(Map.of("attr_a", "a"), existingProps);
      assertEquals(Map.of("attr_c", "c"), patchProps);
    }

    @Test
    void updateWithStillReplacesForTheManagementPatchEndpoint() {
      // UserPatchService relies on this; organization_user_patch_parameters.test.js pins it.
      User existing = userWith(props("attr_a", "a", "attr_b", "b"));

      User updated = existing.updateWith(patchWith(props("attr_c", "c")));

      assertEquals(Map.of("attr_c", "c"), updated.customPropertiesValue());
    }
  }

  @Nested
  class OtherFields {

    @Test
    void nonCustomPropertyFieldsFollowUpdateWithSemantics() {
      User existing = userWith(props("attr_a", "a"));
      existing.setName("old name");
      existing.setEmail("old@example.com");

      User patch = patchWith(props("attr_c", "c"));
      patch.setName("new name");

      User enriched = existing.enrichWith(patch);

      assertEquals("new name", enriched.name());
      assertEquals("old@example.com", enriched.email());
      assertEquals("sub-1", enriched.sub());
    }
  }
}

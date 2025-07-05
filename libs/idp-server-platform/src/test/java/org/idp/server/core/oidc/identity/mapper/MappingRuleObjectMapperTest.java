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

package org.idp.server.core.oidc.identity.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.mapper.MappingRule;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.junit.jupiter.api.Test;

public class MappingRuleObjectMapperTest {

  @Test
  public void testExecute() {
    String json =
        """
            {
              "result": {
                "first_name": "Sarah",
                "last_name": "Meredyth",
                "birth": "1976-03-11",
                "addr": {
                  "street_address": "122 Burns Crescent",
                  "locality": "Edinburgh",
                  "postal_code": "EH1 9GP",
                  "country": "UK"
                },
                "tf": "uk_tfida",
                "ev": [
                  {
                    "type": "electronic_record"
                  }
                ]
              }
            }
        """;

    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(json);

    List<MappingRule> mappingRules =
        List.of(
            new MappingRule("$.result.first_name", "claims.given_name"),
            new MappingRule("$.result.last_name", "claims.family_name"),
            new MappingRule("$.result.birth", "claims.birthdate"),
            new MappingRule("$.result.addr", "claims.address"),
            new MappingRule("$.result.tf", "verification.trust_framework"),
            new MappingRule("$.result.ev", "verification.evidence"));

    MappingRuleObjectMapper mapper = new MappingRuleObjectMapper(mappingRules, jsonNodeWrapper);
    Map<String, Object> result = mapper.execute();

    assertEquals("Sarah", ((Map<String, Object>) result.get("claims")).get("given_name"));
    assertEquals("Meredyth", ((Map<String, Object>) result.get("claims")).get("family_name"));
    assertEquals("1976-03-11", ((Map<String, Object>) result.get("claims")).get("birthdate"));

    Map<String, Object> address =
        (Map<String, Object>) ((Map<String, Object>) result.get("claims")).get("address");
    assertEquals("Edinburgh", address.get("locality"));
    assertEquals("UK", address.get("country"));

    Map<String, Object> verification = (Map<String, Object>) result.get("verification");
    assertEquals("uk_tfida", verification.get("trust_framework"));
    assertTrue(verification.get("evidence") instanceof List);
  }

  @Test
  public void testListType() {
    String json =
        """
            {
              "consumer": {
                "first_name": "Sarah",
                "last_name": "Meredyth",
                "birth": "1976-03-11",
                "addr": {
                  "street_address": "122 Burns Crescent",
                  "locality": "Edinburgh",
                  "postal_code": "EH1 9GP",
                  "country": "UK"
                },
                "tf": "uk_tfida"
              },
              "ekyc": {
                "ev": [
                  {
                    "type": "electronic_record"
                  }
                ]
              }
            }
            """;

    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(json);

    List<MappingRule> mappingRules =
        List.of(
            new MappingRule("$.consumer.first_name", "claims.given_name"),
            new MappingRule("$.consumer.last_name", "claims.family_name"),
            new MappingRule("$.consumer.birth", "claims.birthdate"),
            new MappingRule("$.consumer.addr", "claims.address"),
            new MappingRule("$.consumer.tf", "verification.trust_framework"),
            new MappingRule("$.ekyc.ev[0].type", "verification.evidence.0.type"));

    MappingRuleObjectMapper mapper = new MappingRuleObjectMapper(mappingRules, jsonNodeWrapper);
    Map<String, Object> result = mapper.execute();

    Map<String, Object> claims = (Map<String, Object>) result.get("claims");
    assertNotNull(claims);
    assertEquals("Sarah", claims.get("given_name"));
    assertEquals("Meredyth", claims.get("family_name"));
    assertEquals("1976-03-11", claims.get("birthdate"));

    Map<String, Object> address = (Map<String, Object>) claims.get("address");
    assertNotNull(address);
    assertEquals("Edinburgh", address.get("locality"));
    assertEquals("UK", address.get("country"));

    Map<String, Object> verification = (Map<String, Object>) result.get("verification");
    assertNotNull(verification);
    assertEquals("uk_tfida", verification.get("trust_framework"));

    List<Map<String, Object>> evidence = (List<Map<String, Object>>) verification.get("evidence");
    assertNotNull(evidence);
    assertEquals(1, evidence.size());
    assertEquals("electronic_record", evidence.get(0).get("type"));
  }
}

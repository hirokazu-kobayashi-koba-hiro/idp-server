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
import org.idp.server.platform.date.LocalDateTimeParser;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
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

    JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
    Map<String, Object> result = MappingRuleObjectMapper.execute(mappingRules, jsonPathWrapper);

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

    JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
    Map<String, Object> result = MappingRuleObjectMapper.execute(mappingRules, jsonPathWrapper);

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

  @Test
  public void convertType() {
    String json =
        """
      {
        "source": {
          "name": "Alice",
          "age": "30",
          "active": "true"
        }
      }
    """;

    JsonNodeWrapper wrapper = JsonNodeWrapper.fromString(json);
    JsonPathWrapper pathWrapper = new JsonPathWrapper(wrapper.toJson());

    List<MappingRule> rules =
        List.of(
            new MappingRule("$.source.name", "user.name", "string"),
            new MappingRule("$.source.age", "user.age", "int"),
            new MappingRule("$.source.active", "user.active", "boolean"));

    Map<String, Object> result = MappingRuleObjectMapper.execute(rules, pathWrapper);

    Map<String, Object> user = (Map<String, Object>) result.get("user");
    assertEquals("Alice", user.get("name"));
    assertEquals(30, user.get("age"));
    assertEquals(true, user.get("active"));
  }

  @Test
  public void array() {
    String json =
        """
            [{"processName": "apply", "requested_at": "2025-07-06T11:51:20.797165171"}, {"process": "crm-registration", "requested_at": "2025-07-06T11:51:20.816208671"}, {"process": "request-ekyc", "requested_at": "2025-07-06T11:51:20.835968755"}, {"process": "complete-ekyc", "requested_at": "2025-07-06T11:51:20.857751130"}, {"process": "callback-examination", "requested_at": "2025-07-06T11:51:20.917423380"}]
            """;

    JsonNodeWrapper wrapper = JsonNodeWrapper.fromString(json);
    JsonPathWrapper pathWrapper = new JsonPathWrapper(wrapper.toJson());

    List<MappingRule> rules =
        List.of(
            new MappingRule("$.[0].processName", "process", "string"),
            new MappingRule("$.[0].requested_at", "requested_at", "datetime"));

    Map<String, Object> result = MappingRuleObjectMapper.execute(rules, pathWrapper);

    assertEquals("apply", result.get("process"));
    assertEquals(
        LocalDateTimeParser.parse("2025-07-06T11:51:20.797165171"), result.get("requested_at"));
  }
}

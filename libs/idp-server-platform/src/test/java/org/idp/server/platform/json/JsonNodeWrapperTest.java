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

package org.idp.server.platform.json;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JsonNodeWrapperTest {

  @Nested
  class FactoryMethods {

    @Test
    void emptyReturnsEmptyObject() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.empty();

      assertTrue(wrapper.exists());
      assertFalse(wrapper.existsWithValue());
      assertEquals("{}", wrapper.toJson());
    }

    @Test
    void fromMapCreatesWrapper() {
      Map<String, Object> map = Map.of("name", "test", "count", 42);

      JsonNodeWrapper wrapper = JsonNodeWrapper.fromMap(map);

      assertTrue(wrapper.exists());
      assertTrue(wrapper.existsWithValue());
      assertTrue(wrapper.contains("name"));
      assertTrue(wrapper.contains("count"));
    }

    @Test
    void fromMapWithNullReturnsEmpty() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromMap(null);

      assertFalse(wrapper.existsWithValue());
    }

    @Test
    void fromMapWithEmptyMapReturnsEmpty() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromMap(Map.of());

      assertFalse(wrapper.existsWithValue());
    }

    @Test
    void fromStringCreatesWrapper() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromString("{\"key\":\"value\"}");

      assertTrue(wrapper.existsWithValue());
      assertEquals("value", wrapper.getValueOrEmptyAsString("key"));
    }

    @Test
    void fromStringWithNullReturnsEmpty() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromString(null);

      assertFalse(wrapper.existsWithValue());
    }

    @Test
    void fromStringWithEmptyReturnsEmpty() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromString("");

      assertFalse(wrapper.existsWithValue());
    }

    @Test
    void fromObjectCreatesWrapper() {
      Object obj = Map.of("a", 1);

      JsonNodeWrapper wrapper = JsonNodeWrapper.fromObject(obj);

      assertTrue(wrapper.existsWithValue());
    }

    @Test
    void fromObjectWithNullReturnsEmpty() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromObject(null);

      assertFalse(wrapper.existsWithValue());
    }
  }

  @Nested
  class ValueAccess {

    @Test
    void getValueOrEmptyAsStringReturnsValue() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromString("{\"name\":\"hello\"}");

      assertEquals("hello", wrapper.getValueOrEmptyAsString("name"));
    }

    @Test
    void getValueOrEmptyAsStringReturnsEmptyForMissingField() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromString("{\"name\":\"hello\"}");

      assertEquals("", wrapper.getValueOrEmptyAsString("missing"));
    }

    @Test
    void getValueAsIntReturnsValue() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromString("{\"count\":42}");

      assertEquals(42, wrapper.getValueAsInt("count"));
    }

    @Test
    void getValueAsIntegerReturnsNullForMissing() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromString("{\"other\":1}");

      assertNull(wrapper.getValueAsInteger("missing"));
    }

    @Test
    void getValueAsBooleanReturnsValue() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromString("{\"flag\":true}");

      assertTrue(wrapper.getValueAsBoolean("flag"));
    }

    @Test
    void optValueAsBooleanReturnsDefaultForMissing() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromString("{\"other\":1}");

      assertTrue(wrapper.optValueAsBoolean("missing", true));
      assertFalse(wrapper.optValueAsBoolean("missing", false));
    }
  }

  @Nested
  class NestedAccess {

    @Test
    void getNodeReturnsNestedWrapper() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromString("{\"outer\":{\"inner\":\"deep\"}}");

      JsonNodeWrapper nested = wrapper.getNode("outer");

      assertTrue(nested.existsWithValue());
      assertEquals("deep", nested.getValueOrEmptyAsString("inner"));
    }

    @Test
    void getNodeReturnsEmptyForMissing() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromString("{\"key\":\"value\"}");

      JsonNodeWrapper missing = wrapper.getNode("nonexistent");

      assertFalse(missing.existsWithValue());
    }
  }

  @Nested
  class ArrayHandling {

    @Test
    void toListReturnsStringList() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromString("[\"a\",\"b\",\"c\"]");

      List<String> list = wrapper.toList();

      assertEquals(List.of("a", "b", "c"), list);
    }

    @Test
    void elementsReturnsWrappedList() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromString("[{\"id\":1},{\"id\":2}]");

      List<JsonNodeWrapper> elements = wrapper.elements();

      assertEquals(2, elements.size());
      assertEquals(1, elements.get(0).getValueAsInt("id"));
      assertEquals(2, elements.get(1).getValueAsInt("id"));
    }

    @Test
    void getValueAsJsonNodeListReturnsWrappedArray() {
      JsonNodeWrapper wrapper =
          JsonNodeWrapper.fromString("{\"items\":[{\"name\":\"a\"},{\"name\":\"b\"}]}");

      List<JsonNodeWrapper> items = wrapper.getValueAsJsonNodeList("items");

      assertEquals(2, items.size());
      assertEquals("a", items.get(0).getValueOrEmptyAsString("name"));
      assertEquals("b", items.get(1).getValueOrEmptyAsString("name"));
    }
  }

  @Nested
  class ToMap {

    @Test
    void convertsSimpleObjectToMap() {
      JsonNodeWrapper wrapper =
          JsonNodeWrapper.fromString("{\"name\":\"test\",\"count\":42,\"active\":true}");

      Map<String, Object> map = wrapper.toMap();

      assertEquals("test", map.get("name"));
      assertEquals(42, map.get("count"));
      assertEquals(true, map.get("active"));
    }

    @Test
    void convertsNestedObjectToMap() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromString("{\"outer\":{\"inner\":\"value\"}}");

      Map<String, Object> map = wrapper.toMap();

      @SuppressWarnings("unchecked")
      Map<String, Object> outerMap = (Map<String, Object>) map.get("outer");
      assertEquals("value", outerMap.get("inner"));
    }

    @Test
    void convertsArrayInObjectToMap() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromString("{\"tags\":[\"a\",\"b\"]}");

      Map<String, Object> map = wrapper.toMap();

      @SuppressWarnings("unchecked")
      List<Object> tags = (List<Object>) map.get("tags");
      assertEquals(List.of("a", "b"), tags);
    }

    @Test
    void handlesNullValueInMap() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromString("{\"present\":\"yes\",\"absent\":null}");

      Map<String, Object> map = wrapper.toMap();

      assertEquals("yes", map.get("present"));
      assertNull(map.get("absent"));
    }
  }

  @Nested
  class NodeType {

    @Test
    void detectsStringType() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromString("\"hello\"");

      assertTrue(wrapper.isString());
      assertEquals(JsonNodeType.STRING, wrapper.nodeType());
    }

    @Test
    void detectsBooleanType() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromString("true");

      assertTrue(wrapper.isBoolean());
      assertEquals(JsonNodeType.BOOLEAN, wrapper.nodeType());
    }

    @Test
    void detectsIntType() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromString("42");

      assertTrue(wrapper.isInt());
      assertEquals(JsonNodeType.INT, wrapper.nodeType());
    }

    @Test
    void detectsObjectType() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromString("{\"key\":\"value\"}");

      assertTrue(wrapper.isObject());
      assertEquals(JsonNodeType.OBJECT, wrapper.nodeType());
    }

    @Test
    void detectsArrayType() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromString("[1,2,3]");

      assertTrue(wrapper.isArray());
      assertEquals(JsonNodeType.ARRAY, wrapper.nodeType());
    }
  }

  @Nested
  class FieldNames {

    @Test
    void fieldNamesAsListReturnsAllFields() {
      JsonNodeWrapper wrapper = JsonNodeWrapper.fromString("{\"a\":1,\"b\":2,\"c\":3}");

      List<String> names = wrapper.fieldNamesAsList();

      assertEquals(3, names.size());
      assertTrue(names.contains("a"));
      assertTrue(names.contains("b"));
      assertTrue(names.contains("c"));
    }
  }
}

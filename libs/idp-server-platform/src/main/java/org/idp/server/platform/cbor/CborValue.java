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

package org.idp.server.platform.cbor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.dataformat.cbor.CBORMapper;

/**
 * A value of a parsed CBOR structure (RFC 8949).
 *
 * <p>Wraps the CBOR implementation so that callers read CBOR without depending on it, the same way
 * {@code org.idp.server.platform.asn1} wraps ASN.1. Attestation objects are the reason this exists:
 * Apple App Attest delivers one as CBOR, and no standard Java API decodes it.
 *
 * <p>Reads are strict. Asking for a key or a type the value does not hold is an error rather than a
 * default, because the input is evidence supplied by the party being verified — a lenient read is
 * how a malformed structure ends up being interpreted as an empty one, and an empty one is exactly
 * what an attacker would like a check to run against.
 */
public class CborValue {

  private static final CBORMapper MAPPER = CBORMapper.builder().build();

  Object value;

  CborValue(Object value) {
    this.value = value;
  }

  public static CborValue parse(byte[] cbor) throws CborInvalidException {
    try {
      return new CborValue(MAPPER.readValue(cbor, Object.class));
    } catch (Exception e) {
      throw new CborInvalidException("failed to parse CBOR: " + e.getMessage(), e);
    }
  }

  /** The value held at {@code key}, when this is a map. */
  public CborValue get(String key) throws CborInvalidException {
    if (!(value instanceof Map<?, ?> map)) {
      throw new CborInvalidException("CBOR value is not a map: " + typeName());
    }
    if (!map.containsKey(key)) {
      throw new CborInvalidException("CBOR map has no key: " + key);
    }
    return new CborValue(map.get(key));
  }

  public boolean contains(String key) {
    return value instanceof Map<?, ?> map && map.containsKey(key);
  }

  /** The elements, when this is an array. */
  public List<CborValue> elements() throws CborInvalidException {
    if (!(value instanceof List<?> list)) {
      throw new CborInvalidException("CBOR value is not an array: " + typeName());
    }
    List<CborValue> values = new ArrayList<>();
    for (Object element : list) {
      values.add(new CborValue(element));
    }
    return values;
  }

  /**
   * A byte string.
   *
   * <p>CBOR distinguishes byte strings from text strings, and this keeps that distinction: decoding
   * a text string as bytes would silently accept evidence encoded the wrong way.
   */
  public byte[] byteString() throws CborInvalidException {
    if (!(value instanceof byte[] bytes)) {
      throw new CborInvalidException("CBOR value is not a byte string: " + typeName());
    }
    return bytes;
  }

  public String textString() throws CborInvalidException {
    if (!(value instanceof String text)) {
      throw new CborInvalidException("CBOR value is not a text string: " + typeName());
    }
    return text;
  }

  private String typeName() {
    return value == null ? "null" : value.getClass().getSimpleName();
  }
}

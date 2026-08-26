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

package org.idp.server.platform.asn1;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Enumerated;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.ASN1TaggedObject;

/**
 * A node of a parsed ASN.1 structure.
 *
 * <p>Wraps the ASN.1 implementation so that callers read DER without depending on it: certificate
 * extensions carry structures no standard Java API exposes, and every caller writing against a
 * third-party ASN.1 type would spread that dependency across modules.
 *
 * <p>Reads are strict. Asking for a type the node does not hold is an error rather than a default,
 * because the input is a certificate extension supplied by the caller being verified — a lenient
 * read is how a malformed structure ends up being interpreted as an empty one.
 */
public class Asn1Node {

  ASN1Encodable value;

  Asn1Node(ASN1Encodable value) {
    this.value = value;
  }

  /** Parses DER bytes, unwrapping the outer OCTET STRING that wraps a certificate extension. */
  public static Asn1Node parseExtension(byte[] extensionValue) throws Asn1InvalidException {
    try {
      byte[] octets = ASN1OctetString.getInstance(extensionValue).getOctets();
      return new Asn1Node(ASN1Primitive.fromByteArray(octets));
    } catch (Exception e) {
      throw new Asn1InvalidException("failed to parse ASN.1 extension: " + e.getMessage(), e);
    }
  }

  public static Asn1Node parse(byte[] der) throws Asn1InvalidException {
    try {
      return new Asn1Node(ASN1Primitive.fromByteArray(der));
    } catch (Exception e) {
      throw new Asn1InvalidException("failed to parse ASN.1: " + e.getMessage(), e);
    }
  }

  /** The elements of a SEQUENCE or a SET. */
  public List<Asn1Node> elements() throws Asn1InvalidException {
    try {
      List<Asn1Node> nodes = new ArrayList<>();
      Iterable<ASN1Encodable> iterable =
          value instanceof ASN1Set set ? set : ASN1Sequence.getInstance(value);
      for (ASN1Encodable element : iterable) {
        nodes.add(new Asn1Node(element));
      }
      return nodes;
    } catch (Exception e) {
      throw new Asn1InvalidException("ASN.1 node is not a sequence or a set: " + e.getMessage(), e);
    }
  }

  public Asn1Node at(int index) throws Asn1InvalidException {
    List<Asn1Node> elements = elements();
    if (index >= elements.size()) {
      throw new Asn1InvalidException(
          "ASN.1 sequence has " + elements.size() + " elements, index " + index + " requested");
    }
    return elements.get(index);
  }

  public int size() throws Asn1InvalidException {
    return elements().size();
  }

  public byte[] octets() throws Asn1InvalidException {
    try {
      return ASN1OctetString.getInstance(value).getOctets();
    } catch (Exception e) {
      throw new Asn1InvalidException("ASN.1 node is not an octet string: " + e.getMessage(), e);
    }
  }

  public int intValue() throws Asn1InvalidException {
    try {
      return ASN1Integer.getInstance(value).getValue().intValue();
    } catch (Exception e) {
      throw new Asn1InvalidException("ASN.1 node is not an integer: " + e.getMessage(), e);
    }
  }

  /**
   * The value of an ENUMERATED node.
   *
   * <p>An INTEGER carrying the same number is rejected. Accepting it would buy nothing — the
   * producers whose output reaches a verifier that pins the issuer's root follow the schema they
   * publish — while costing the property that a fixture built with the wrong tag fails, which is
   * how this class of mistake is normally noticed.
   */
  public int enumeratedValue() throws Asn1InvalidException {
    if (!(value instanceof ASN1Enumerated enumerated)) {
      throw new Asn1InvalidException(
          "ASN.1 node is not an enumerated: " + value.getClass().getSimpleName());
    }
    return enumerated.getValue().intValue();
  }

  /** The context specific tag number, when this node is a tagged object. */
  public Optional<Integer> tagNumber() {
    if (!(value instanceof ASN1TaggedObject tagged)) {
      return Optional.empty();
    }
    return Optional.of(tagged.getTagNo());
  }

  /** The content of a tagged object. */
  public Asn1Node taggedContent() throws Asn1InvalidException {
    if (!(value instanceof ASN1TaggedObject tagged)) {
      throw new Asn1InvalidException("ASN.1 node is not a tagged object");
    }
    return new Asn1Node(tagged.getBaseObject());
  }

  /** Finds the first element of this sequence carrying {@code tagNumber}. */
  public Optional<Asn1Node> findTagged(int tagNumber) throws Asn1InvalidException {
    for (Asn1Node element : elements()) {
      if (element.tagNumber().filter(tag -> tag == tagNumber).isPresent()) {
        return Optional.of(element);
      }
    }
    return Optional.empty();
  }
}

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

package org.idp.server.platform.x509;

public enum X509SubjectAlternativeNameType {
  otherName(0, "OtherName"),
  rfc822Name(1, "IA5String"),
  dNSName(2, "IA5String"),
  x400Address(3, "ORAddress"),
  directoryName(4, "Name"),
  ediPartyName(5, "EDIPartyName"),
  uniformResourceIdentifier(6, "IA5String"),
  iPAddress(7, "OCTET STRING"),
  registeredID(8, "OBJECT");

  int value;
  String type;

  X509SubjectAlternativeNameType(int value, String type) {
    this.value = value;
    this.type = type;
  }

  public static X509SubjectAlternativeNameType of(int value) throws X509CertInvalidException {
    for (X509SubjectAlternativeNameType type : X509SubjectAlternativeNameType.values()) {
      if (type.value == value) {
        return type;
      }
    }
    throw new X509CertInvalidException("unknown type");
  }
}

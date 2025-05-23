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

package org.idp.server.basic.x509;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.*;

public class X509SubjectAlternativeNames {

  Map<X509SubjectAlternativeNameType, String> values;

  public X509SubjectAlternativeNames() {
    this.values = new HashMap<>();
  }

  public X509SubjectAlternativeNames(Map<X509SubjectAlternativeNameType, String> values) {
    this.values = values;
  }

  public static X509SubjectAlternativeNames parse(X509Certificate x509Certificate)
      throws X509CertInvalidException {
    try {
      Collection<List<?>> subjectAlternativeNames = x509Certificate.getSubjectAlternativeNames();
      if (Objects.isNull(subjectAlternativeNames) || subjectAlternativeNames.isEmpty()) {
        return new X509SubjectAlternativeNames();
      }
      Map<X509SubjectAlternativeNameType, String> values = new HashMap<>();
      for (List<?> names : subjectAlternativeNames) {
        X509SubjectAlternativeNameType type =
            X509SubjectAlternativeNameType.of((Integer) names.get(0));
        String value = (String) names.get(1);
        values.put(type, value);
      }
      return new X509SubjectAlternativeNames(values);
    } catch (CertificateParsingException exception) {
      throw new X509CertInvalidException(exception);
    }
  }

  public String otherName() {
    return getOrEmpty(X509SubjectAlternativeNameType.otherName);
  }

  public boolean hasOtherName() {
    return contains(X509SubjectAlternativeNameType.otherName);
  }

  public String rfc822Name() {
    return getOrEmpty(X509SubjectAlternativeNameType.rfc822Name);
  }

  public boolean hasRfc822Name() {
    return contains(X509SubjectAlternativeNameType.rfc822Name);
  }

  public String dNSName() {
    return getOrEmpty(X509SubjectAlternativeNameType.dNSName);
  }

  public boolean hasDNSName() {
    return contains(X509SubjectAlternativeNameType.dNSName);
  }

  public String x400Address() {
    return getOrEmpty(X509SubjectAlternativeNameType.x400Address);
  }

  public boolean hasX400Address() {
    return contains(X509SubjectAlternativeNameType.x400Address);
  }

  public String directoryName() {
    return getOrEmpty(X509SubjectAlternativeNameType.directoryName);
  }

  public boolean hasDirectoryName() {
    return contains(X509SubjectAlternativeNameType.directoryName);
  }

  public String ediPartyName() {
    return getOrEmpty(X509SubjectAlternativeNameType.ediPartyName);
  }

  public boolean hasEditPartyName() {
    return contains(X509SubjectAlternativeNameType.ediPartyName);
  }

  public String uniformResourceIdentifier() {
    return getOrEmpty(X509SubjectAlternativeNameType.uniformResourceIdentifier);
  }

  public boolean hasUniformResourceIdentifier() {
    return contains(X509SubjectAlternativeNameType.uniformResourceIdentifier);
  }

  public String registeredID() {
    return getOrEmpty(X509SubjectAlternativeNameType.registeredID);
  }

  public boolean hasRegisteredID() {
    return contains(X509SubjectAlternativeNameType.registeredID);
  }

  public String iPAddress() {
    return getOrEmpty(X509SubjectAlternativeNameType.iPAddress);
  }

  public boolean hasIPAddress() {
    return contains(X509SubjectAlternativeNameType.iPAddress);
  }

  String getOrEmpty(X509SubjectAlternativeNameType type) {
    return values.getOrDefault(type, "");
  }

  boolean contains(X509SubjectAlternativeNameType type) {
    return values.containsKey(type);
  }
}

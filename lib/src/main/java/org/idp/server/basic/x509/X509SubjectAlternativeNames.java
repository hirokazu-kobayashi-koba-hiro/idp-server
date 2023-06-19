package org.idp.server.basic.x509;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.*;

public class X509SubjectAlternativeNames {

  Map<String, String> values;

  public X509SubjectAlternativeNames() {
    this.values = new HashMap<>();
  }

  public X509SubjectAlternativeNames(Map<String, String> values) {
    this.values = values;
  }

  public static X509SubjectAlternativeNames parse(X509Certificate x509Certificate)
      throws X509CertInvalidException {
    try {
      Collection<List<?>> subjectAlternativeNames = x509Certificate.getSubjectAlternativeNames();
      if (Objects.isNull(subjectAlternativeNames) || subjectAlternativeNames.isEmpty()) {
        return new X509SubjectAlternativeNames();
      }
      Map<String, String> values = new HashMap<>();
      subjectAlternativeNames.forEach(
          names -> {
            values.put((String) names.get(0), (String) names.get(1));
          });
      return new X509SubjectAlternativeNames(values);
    } catch (CertificateParsingException exception) {
      throw new X509CertInvalidException(exception);
    }
  }

  /**
   * GeneralName ::= CHOICE { otherName [0] OtherName, rfc822Name [1] IA5String, dNSName [2]
   * IA5String, x400Address [3] ORAddress, directoryName [4] Name, ediPartyName [5] EDIPartyName,
   * uniformResourceIdentifier [6] IA5String, iPAddress [7] OCTET STRING, registeredID [8] OBJECT
   * IDENTIFIER}
   */
  public String otherName() {
    return getOrEmpty("otherName");
  }

  public boolean hasOtherName() {
    return contains("otherName");
  }

  public String rfc822Name() {
    return getOrEmpty("rfc822Name");
  }

  public boolean hasRfc822Name() {
    return contains("rfc822Name");
  }

  public String dNSName() {
    return getOrEmpty("dNSName");
  }

  public boolean hasDNSName() {
    return contains("dNSName");
  }

  public String x400Address() {
    return getOrEmpty("x400Address");
  }

  public boolean hasX400Address() {
    return contains("x400Address");
  }

  public String directoryName() {
    return getOrEmpty("directoryName");
  }

  public boolean hasDirectoryName() {
    return contains("directoryName");
  }

  public String ediPartyName() {
    return getOrEmpty("ediPartyName");
  }

  public boolean hasEditPartyName() {
    return contains("ediPartyName");
  }

  public String uniformResourceIdentifier() {
    return getOrEmpty("uniformResourceIdentifier");
  }

  public boolean hasUniformResourceIdentifier() {
    return contains("uniformResourceIdentifier");
  }

  public String registeredID() {
    return getOrEmpty("registeredID");
  }

  public boolean hasRegisteredID() {
    return contains("registeredID");
  }

  public String iPAddress() {
    return getOrEmpty("iPAddress");
  }

  public boolean hasIPAddress() {
    return contains("iPAddress");
  }

  String getOrEmpty(String key) {
    return values.getOrDefault(key, "");
  }

  boolean contains(String key) {
    return values.containsKey(key);
  }
}

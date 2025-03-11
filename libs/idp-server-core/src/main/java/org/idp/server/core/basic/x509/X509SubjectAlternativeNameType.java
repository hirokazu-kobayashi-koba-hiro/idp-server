package org.idp.server.core.basic.x509;

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

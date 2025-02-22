package org.idp.sample.subdomain.webauthn;

public class WebAuthnCredential {
  String id;

  String userId;

  String rpId;

  byte[] publicKey;

  byte[] attestationObject;

  long signCount;

  public WebAuthnCredential() {}

  public WebAuthnCredential(
      String id,
      String userId,
      String rpId,
      byte[] publicKey,
      byte[] attestationObject,
      long signCount) {
    this.id = id;
    this.userId = userId;
    this.rpId = rpId;
    this.publicKey = publicKey;
    this.attestationObject = attestationObject;
    this.signCount = signCount;
  }

  public String id() {
    return id;
  }

  public String userId() {
    return userId;
  }

  public String rpId() {
    return rpId;
  }

  public byte[] publicKey() {
    return publicKey;
  }

  public long signCount() {
    return signCount;
  }

  public byte[] attestationObject() {
    return attestationObject;
  }
}

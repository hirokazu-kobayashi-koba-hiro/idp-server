package org.idp.server.adapters.springboot.subdomain.webauthn;

public class WebAuthnCredential {
  byte[] id;

  String userId;

  String rpId;

  byte[] publicKey;

  byte[] attestationObject;

  long signCount;

  public WebAuthnCredential() {}

  public WebAuthnCredential(
      byte[] id,
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

  public byte[] id() {
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

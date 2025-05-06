package org.idp.server.basic.jose;

import com.nimbusds.jose.*;

public class NestedJsonWebEncryptionCreator {

  JsonWebSignature jsonWebSignature;
  String jweAlgorithm;
  String encryptionMethod;
  String publicKeys;

  public NestedJsonWebEncryptionCreator(JsonWebSignature jsonWebSignature, String jweAlgorithm, String encryptionMethod, String publicKeys) {
    this.jsonWebSignature = jsonWebSignature;
    this.jweAlgorithm = jweAlgorithm;
    this.encryptionMethod = encryptionMethod;
    this.publicKeys = publicKeys;
  }

  public String create() throws JoseInvalidException {
    try {
      JWEAlgorithm algorithm = JWEAlgorithm.parse(jweAlgorithm);
      EncryptionMethod method = EncryptionMethod.parse(encryptionMethod);
      JsonWebKeys jsonWebKeys = JwkParser.parseKeys(publicKeys);
      JsonWebKey jsonWebKey = jsonWebKeys.findByAlgorithm(jweAlgorithm);
      JWEEncrypter jweEncrypter = new JsonWebEncrypterFactory(jsonWebKey).create();
      JWEObject jweObject = new JWEObject(new JWEHeader.Builder(algorithm, method).contentType("JWT").build(), new Payload(jsonWebSignature.value()));
      jweObject.encrypt(jweEncrypter);
      return jweObject.serialize();
    } catch (JsonWebKeyInvalidException | JOSEException e) {
      throw new JoseInvalidException(e.getMessage(), e);
    }
  }
}

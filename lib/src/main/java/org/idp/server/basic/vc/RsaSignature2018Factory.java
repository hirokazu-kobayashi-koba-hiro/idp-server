package org.idp.server.basic.vc;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import info.weboftrust.ldsignatures.signer.RsaSignature2018LdSigner;
import java.security.KeyPair;
import java.text.ParseException;

public class RsaSignature2018Factory implements LdSignerFactory {

  @Override
  public RsaSignature2018LdSigner create(String privateKey) throws VcInvalidKeyException {
    try {
      JWK jwk = JWK.parse(privateKey);
      KeyPair keyPair = jwk.toRSAKey().toKeyPair();
      return new RsaSignature2018LdSigner(keyPair);
    } catch (ParseException e) {
      throw new VcInvalidKeyException(
          "invalid private key format, failed RsaSignature creation", e);
    } catch (JOSEException e) {
      throw new VcInvalidKeyException("invalid key pair, failed RsaSignature creation", e);
    }
  }
}

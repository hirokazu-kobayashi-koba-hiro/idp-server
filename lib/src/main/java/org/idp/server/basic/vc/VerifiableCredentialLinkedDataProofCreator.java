package org.idp.server.basic.vc;

import foundation.identity.jsonld.ConfigurableDocumentLoader;
import foundation.identity.jsonld.JsonLDException;
import foundation.identity.jsonld.JsonLDObject;
import foundation.identity.jsonld.JsonLDUtils;
import info.weboftrust.ldsignatures.LdProof;
import info.weboftrust.ldsignatures.signer.LdSigner;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;
import org.idp.server.basic.date.SystemDateTime;

public class VerifiableCredentialLinkedDataProofCreator {

  LdSignerFactories ldSignerFactories;

  public VerifiableCredentialLinkedDataProofCreator() {
    this.ldSignerFactories = new LdSignerFactories();
  }

  public VerifiableCredentialContext create(
      Map<String, Object> credentials,
      ProofType proofType,
      String verificationMethod,
      String privateKey)
      throws VcInvalidException, VcInvalidKeyException {
    try {
      LdSignerFactory ldSignerFactory = ldSignerFactories.get(proofType);
      LdSigner signer = ldSignerFactory.create(privateKey);
      ConfigurableDocumentLoader configurableDocumentLoader = new ConfigurableDocumentLoader();
      configurableDocumentLoader.setEnableHttps(true);
      JsonLDObject jsonLDObject = JsonLDObject.fromJsonObject(credentials);
      jsonLDObject.setDocumentLoader(configurableDocumentLoader);
      // did:sov:1yvXbmgPoUm4dl66D7KhyD#keys-1
      URI verificationMethodUri = URI.create(verificationMethod);
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
      Date created = JsonLDUtils.DATE_FORMAT.parse(formatter.format(SystemDateTime.now()));
      String domain = null;
      String nonce = "c0ae1c8e-c7e7-469f-b252-86e6a0e7387e";
      signer.setVerificationMethod(verificationMethodUri);
      signer.setCreated(created);
      signer.setDomain(domain);
      signer.setNonce(nonce);
      LdProof ldSignature = signer.sign(jsonLDObject, true, false);
      credentials.put("proof", ldSignature.getJsonObject());
      VerifiableCredential verifiableCredential = new VerifiableCredential(credentials);
      return new VerifiableCredentialContext(VerifiableCredentialFormat.ldp, verifiableCredential);
    } catch (JsonLDException e) {
      throw new RuntimeException(e);
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

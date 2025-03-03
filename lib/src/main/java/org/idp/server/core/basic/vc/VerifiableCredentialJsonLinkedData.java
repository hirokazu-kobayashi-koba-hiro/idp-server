package org.idp.server.core.basic.vc;

import foundation.identity.jsonld.JsonLDObject;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.json.JsonConvertable;

/**
 * example
 *
 * <pre>
 * {
 *
 *   "@context": [
 *     "https://www.w3.org/2018/credentials/v1",
 *     "https://www.w3.org/2018/credentials/examples/v1"
 *   ],
 *
 *   "id": "http://example.edu/credentials/1872",
 *
 *   "type": ["VerifiableCredential", "AlumniCredential"],
 *
 *   "issuer": "https://example.edu/issuers/565049",
 *
 *   "issuanceDate": "2010-01-01T19:23:24Z",
 *
 *   "credentialSubject": {
 *
 *     "id": "did:example:ebfeb1f712ebc6f1c276e12ec21",
 *
 *     "alumniOf": {
 *       "id": "did:example:c276e12ec21ebfeb1f712ebc6f1",
 *       "name": [{
 *         "value": "Example University",
 *         "lang": "en"
 *       }, {
 *         "value": "Exemple d'Université",
 *         "lang": "fr"
 *       }]
 *     }
 *   },
 *
 *
 *   "proof": {
 *
 *     "type": "RsaSignature2018",
 *
 *     "created": "2017-06-18T21:19:10Z",
 *
 *     "proofPurpose": "assertionMethod",
 *
 *     "verificationMethod": "https://example.edu/issuers/565049#key-1",
 *
 *     "jws": "eyJhbGciOiJSUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19..TCYt5X
 *       sITJX1CxPCT8yAV-TVkIEq_PbChOMqsLfRoPsnsgw5WEuts01mq-pQy7UJiN5mgRxD-WUc
 *       X16dUEMGlv50aqzpqh4Qktb3rk-BuQy72IFLOqV0G_zS245-kronKb78cPN25DGlcTwLtj
 *       PAYuNzVBAh4vGHSrQyHUdBBPM"
 *   }
 * }
 *
 * </pre>
 *
 * @see <a href="https://www.w3.org/TR/vc-data-model/">vc-data-model</a>
 */
public class VerifiableCredentialJsonLinkedData {
  JsonLDObject values;

  public VerifiableCredentialJsonLinkedData() {
    this.values = new JsonLDObject();
  }

  public VerifiableCredentialJsonLinkedData(Map<String, Object> values) {
    this.values = JsonLDObject.fromJsonObject(values);
  }

  public VerifiableCredentialJsonLinkedData(JsonLDObject values) {
    this.values = values;
  }

  public static VerifiableCredentialJsonLinkedData parse(String json) {
    return new VerifiableCredentialJsonLinkedData(JsonConvertable.read(json, Map.class));
  }

  public List<URI> context() {
    return values.getContexts();
  }

  public URI id() {
    return values.getId();
  }

  public String type() {
    return values.getType();
  }

  public Map<String, Object> tpMap() {
    return values.toMap();
  }

  public String toJson() {
    return values.toJson();
  }
}

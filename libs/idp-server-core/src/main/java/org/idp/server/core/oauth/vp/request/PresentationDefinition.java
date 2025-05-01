package org.idp.server.core.oauth.vp.request;

import java.util.Objects;
import org.idp.server.basic.json.JsonReadable;

/**
 * example
 *
 * <pre>
 * {
 *     "id": "example with selective disclosure",
 *     "input_descriptors": [
 *         {
 *             "id": "ID card with constraints",
 *             "format": {
 *                 "ldp_vc": {
 *                     "proof_type": [
 *                         "Ed25519Signature2018"
 *                     ]
 *                 }
 *             },
 *             "constraints": {
 *                 "limit_disclosure": "required",
 *                 "fields": [
 *                     {
 *                         "path": [
 *                             "$.type"
 *                         ],
 *                         "filter": {
 *                             "type": "string",
 *                             "pattern": "IDCardCredential"
 *                         }
 *                     },
 *                     {
 *                         "path": [
 *                             "$.credentialSubject.given_name"
 *                         ]
 *                     },
 *                     {
 *                         "path": [
 *                             "$.credentialSubject.family_name"
 *                         ]
 *                     },
 *                     {
 *                         "path": [
 *                             "$.credentialSubject.birthdate"
 *                         ]
 *                     }
 *                 ]
 *             }
 *         }
 *     ]
 * }
 * </pre>
 */
public class PresentationDefinition implements JsonReadable {
  String id;
  PresentationDefinitionInputDescriptors inputDescriptors;

  public PresentationDefinition() {}

  public PresentationDefinition(
      String id, PresentationDefinitionInputDescriptors inputDescriptors) {
    this.id = id;
    this.inputDescriptors = inputDescriptors;
  }

  public String id() {
    return id;
  }

  public PresentationDefinitionInputDescriptors inputDescriptors() {
    return inputDescriptors;
  }

  public boolean exists() {
    return Objects.nonNull(id) && !id.isEmpty();
  }
}

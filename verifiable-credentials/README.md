# verifiable-credentials

## api confirmation

```shell
curl -X POST http://localhost:3000/v1/verifiable-credentials/did-jwt \
-H "Content-Type: application/json" \
-d '{ "vc": { "@context": [ "https://www.w3.org/2018/credentials/v1" ], "type": [ "VerifiableCredential" ], "credentialSubject": { "degree": { "type": "BachelorDegree", "name": "Baccalauréat en musiques numériques" } } }, "sub": "did:ethr:0x435df3eda57154cf8cf7926079881f2912f54db4", "nbf": 1562950282, "iss": "did:ethr:0xF1232F840f3aD7d23FcDaA84d6C66dac24EFb198" }'
```

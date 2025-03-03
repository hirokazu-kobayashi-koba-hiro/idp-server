# verifiable-credentials

## What is Verifiable Credentials



## Prerequisites

1. create wallet
   1. https://metamask.io/
2. create alchemy account
   1. https://www.alchemy.com/
3. create apikey of sepolia at alchemy
4. send eth to wallet
   1. https://sepoliafaucet.com/


## local debug

example
```shell
export ADDRESS=0xf1232f840f3ad7d23fcdaa84d6c66dac24efb198
export PRIVATE_KEY=d8b595680851765f38ea5405129244ba3cbad84467d190859f4c8b20c1ff6c75
export WEB3_URL=wss://eth-sepolia.g.alchemy.com/v2/xxx
export VERIFICATION_Method='did:web:assets.dev.trustid.sbi-fc.com#key-2'
export CHAIN=ethereum_sepolia

node ./app/index.js
```

## api confirmation

```shell
curl -v http://localhost:8000/health
```

```shell
curl -v -X POST http://localhost:8000/v1/verifiable-credentials/block-cert \
-H "Content-Type: application/json" \
-d '{ "credential": { "issuer": "did:web:assets.dev.trustid.sbi-fc.com", "issuanceDate": "2024-01-03T21:57:00Z","@context": [ "https://www.w3.org/2018/credentials/v1" ], "type": [ "VerifiableCredential" ], "credentialSubject": { "id": "did:example:test" } }, "sub": "did:ethr:0x435df3eda57154cf8cf7926079881f2912f54db4", "nbf": 1562950282, "iss": "did:ethr:0xF1232F840f3aD7d23FcDaA84d6C66dac24EFb198" }'
```

```shell
curl -v -X POST http://localhost:8000/v1/verifiable-credentials/block-cert/verify \
-H "Content-Type: application/json" \
-d '{ "vc_block_cert": { "@context": [ "https://www.w3.org/2018/credentials/v1", "https://www.w3.org/2018/credentials/examples/v1" ], "id": "http://example.edu/credentials/1872", "type": [ "VerifiableCredential", "AlumniCredential" ], "issuer": "did:web:assets.dev.trustid.sbi-fc.com", "issuanceDate": "2010-01-01T19:23:24Z", "credentialSubject": { "id": "did:example:ebfeb1f712ebc6f1c276e12ec21", "alumniOf": { "id": "did:example:c276e12ec21ebfeb1f712ebc6f1" } }, "proof": { "type": "MerkleProof2019", "created": "2024-02-01T00:10:37.236Z", "proofValue": "z9rVnpGQKsMTs1STQ65jRc79RQF3se4CMR3xTtFhDruUKcS2N5rNnkY7AdKnBT6SzNN2u51gwFhBFRmAwRdGDSNEjW9kgBvC66ZqAeKkaoiWh91ciCKSESPf7z7275z6xWLR72JhjW5Lt7rZiAj49GS2ww3xrroq4x19naDa714QqhzFC57r6S1LvuRwiWPtmCiF6iLHVMTrDzxu8k75ttZqh2PvC9TC5zAS", "proofPurpose": "assertionMethod", "verificationMethod": "did:web:assets.dev.trustid.sbi-fc.com#key-2" } } }'
```

```shell
curl -v -X POST http://localhost:8000/v1/verifiable-credentials/did-jwt \
-H "Content-Type: application/json" \
-d '{ "headers": {"typ": "openid4vci-proof+jwt"}, "credential": {"@context": [ "https://www.w3.org/2018/credentials/v1" ], "type": [ "VerifiableCredential" ], "credentialSubject": { "degree": { "type": "BachelorDegree", "name": "Baccalauréat en musiques numériques" } } }, "sub": "did:ethr:0x435df3eda57154cf8cf7926079881f2912f54db4", "nbf": 1562950282, "iss": "did:ethr:0xF1232F840f3aD7d23FcDaA84d6C66dac24EFb198" }'
```

```shell
curl -v -X POST http://localhost:8000/v1/verifiable-credentials/did-jwt/verify \
-H "Content-Type: application/json" \
-d '{ "vc_jwt": "eyJ0eXAiOiJvcGVuaWQ0dmNpLXByb29mK2p3dCIsImFsZyI6IkVTMjU2Sy1SIn0.eyJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIl0sImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImRlZ3JlZSI6eyJ0eXBlIjoiQmFjaGVsb3JEZWdyZWUiLCJuYW1lIjoiQmFjY2FsYXVyw6lhdCBlbiBtdXNpcXVlcyBudW3DqXJpcXVlcyJ9fX0sImlzcyI6ImRpZDpldGhyOjB4MjYwODQxRjI0NDBmZmQxODg0RjdlZUFDNTUxYjg5MmRENDQzNDA3MyJ9.J8dVF6wpNI87ZYbNT76vqviiVHZKaqkHa9Hpqleel_UgjFC6IqceopkCuvEF88ESTU0T9Uoyn8IMM3j0kkwAgAA" }'
```

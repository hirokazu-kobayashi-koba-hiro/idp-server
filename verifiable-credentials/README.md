# verifiable-credentials

## pre

1. create wallet
   1. https://metamask.io/
2. create alchemy account
   1. https://www.alchemy.com/
3. create apikey of sepolia at alchemy
4. send eth to wallet
   1. https://sepoliafaucet.com/

## setup
configure conf.ini
configure privateKey.txt

## api confirmation

```shell
curl -v http://localhost:3000/health
```

```shell
curl -X POST http://localhost:3000/v1/verifiable-credentials/block-cert \
-H "Content-Type: application/json" \
-d '{ "vc": { "issuer": "did:web:assets.dev.trustid.sbi-fc.com", "issuanceDate": "2024-01-03T21:57:00Z","@context": [ "https://www.w3.org/2018/credentials/v1" ], "type": [ "VerifiableCredential" ], "credentialSubject": { "id": "did:example:test" } }, "sub": "did:ethr:0x435df3eda57154cf8cf7926079881f2912f54db4", "nbf": 1562950282, "iss": "did:ethr:0xF1232F840f3aD7d23FcDaA84d6C66dac24EFb198" }'
```

```shell
curl -X POST http://localhost:3000/v1/verifiable-credentials/did-jwt \
-H "Content-Type: application/json" \
-d '{ "vc": {"@context": [ "https://www.w3.org/2018/credentials/v1" ], "type": [ "VerifiableCredential" ], "credentialSubject": { "degree": { "type": "BachelorDegree", "name": "Baccalauréat en musiques numériques" } } }, "sub": "did:ethr:0x435df3eda57154cf8cf7926079881f2912f54db4", "nbf": 1562950282, "iss": "did:ethr:0xF1232F840f3aD7d23FcDaA84d6C66dac24EFb198" }'
```

```shell
curl -X POST http://localhost:3000/v1/verifiable-credentials/did-jwt/verify \
-H "Content-Type: application/json" \
-d '{ "vc_jwt": "eyJhbGciOiJFUzI1NkstUiIsInR5cCI6IkpXVCJ9.eyJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIl0sImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImRlZ3JlZSI6eyJ0eXBlIjoiQmFjaGVsb3JEZWdyZWUiLCJuYW1lIjoiQmFjY2FsYXVyw6lhdCBlbiBtdXNpcXVlcyBudW3DqXJpcXVlcyJ9fX0sImlzcyI6ImRpZDpldGhyOjB4RjEyMzJGODQwZjNhRDdkMjNGY0RhQTg0ZDZDNjZkYWMyNEVGYjE5OCJ9.XwVFF4VD1YDAtRXbF6BpJYG1bG95fjIWy2R8dGs1CxcbNmIbKwFT-oAWUxziveZdZF0YCZM0_cvCyT9yyLG8tQA" }'
```

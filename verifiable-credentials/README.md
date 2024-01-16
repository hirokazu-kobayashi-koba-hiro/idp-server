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

## local debug

example
```shell
export ADDRESS=0xf1232f840f3ad7d23fcdaa84d6c66dac24efb198
export PRIVATE_KEY=d8b595680851765f38ea5405129244ba3cbad84467d190859f4c8b20c1ff6c75
export WEB3_URL=wss://eth-sepolia.g.alchemy.com/v2/xxx
export VERIFICATION_Method='did:web:assets.dev.trustid.sbi-fc.com#key-2'
export CHAIN=ethereum_sepolia

node index.js
```

## api confirmation

```shell
curl -v http://localhost:3000/health
```

```shell
curl -v -X POST http://localhost:3000/v1/verifiable-credentials/block-cert \
-H "Content-Type: application/json" \
-d '{ "vc": { "issuer": "did:web:assets.dev.trustid.sbi-fc.com", "issuanceDate": "2024-01-03T21:57:00Z","@context": [ "https://www.w3.org/2018/credentials/v1" ], "type": [ "VerifiableCredential" ], "credentialSubject": { "id": "did:example:test" } }, "sub": "did:ethr:0x435df3eda57154cf8cf7926079881f2912f54db4", "nbf": 1562950282, "iss": "did:ethr:0xF1232F840f3aD7d23FcDaA84d6C66dac24EFb198" }'
```

```shell
curl -v -X POST http://localhost:3000/v1/verifiable-credentials/did-jwt \
-H "Content-Type: application/json" \
-d '{ "vc": {"@context": [ "https://www.w3.org/2018/credentials/v1" ], "type": [ "VerifiableCredential" ], "credentialSubject": { "degree": { "type": "BachelorDegree", "name": "Baccalauréat en musiques numériques" } } }, "sub": "did:ethr:0x435df3eda57154cf8cf7926079881f2912f54db4", "nbf": 1562950282, "iss": "did:ethr:0xF1232F840f3aD7d23FcDaA84d6C66dac24EFb198" }'
```

```shell
curl -v -X POST http://localhost:3000/v1/verifiable-credentials/did-jwt/verify \
-H "Content-Type: application/json" \
-d '{ "vc_jwt": "eyJhbGciOiJFUzI1NkstUiIsInR5cCI6IkpXVCJ9.eyJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIl0sImNyZWRlbnRpYWxTdWJqZWN0Ijp7ImRlZ3JlZSI6eyJ0eXBlIjoiQmFjaGVsb3JEZWdyZWUiLCJuYW1lIjoiQmFjY2FsYXVyw6lhdCBlbiBtdXNpcXVlcyBudW3DqXJpcXVlcyJ9fX0sImlzcyI6ImRpZDpldGhyOjB4RjEyMzJGODQwZjNhRDdkMjNGY0RhQTg0ZDZDNjZkYWMyNEVGYjE5OCJ9.XwVFF4VD1YDAtRXbF6BpJYG1bG95fjIWy2R8dGs1CxcbNmIbKwFT-oAWUxziveZdZF0YCZM0_cvCyT9yyLG8tQA" }'
```

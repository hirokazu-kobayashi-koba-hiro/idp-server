const { parse } = require("did-resolver")
const express = require('express')
const bodyParser = require('body-parser')
const app = express()
const port = 3000
const { EthrDID } = require('ethr-did')
const { createVerifiableCredentialJwt, verifyCredential } = require('did-jwt-vc')
const  axios = require("axios")

const issuer = new EthrDID({
  identifier: '0xf1232f840f3ad7d23fcdaa84d6c66dac24efb198',
  privateKey: 'd8b595680851765f38ea5405129244ba3cbad84467d190859f4c8b20c1ff6c75'
})

app.use(bodyParser.json())

app.listen(port, () => {
  console.log(`Example app listening on port ${port}`)
})

app.get("/health", (req, res) => {
  res.send("OK")
})

app.post("/v1/verifiable-credentials/block-cert", async (request, response) => {
  console.log(request.body)
  if (!request.body.vc) {
    response.status(400)
    response.send('{"error": "invalid_request", "error_description": "vc is required"}').status(400)
    return
  }
  const { payload, error } = await issueVcBlockCert({
    vcPayload: request.body.vc,
  }) || {}
  if (payload && !error) {
    console.log(payload)
    response.send(`{ "vc": ${JSON.stringify(payload)}}`)
    return
  }
  response.status(400)
  response.send(`{"error": "invalid_request", "error_description": ${error}"}`)
})

app.post('/v1/verifiable-credentials/did-jwt', async (request, response) => {
  console.log(request.body)
  if (!request.body.vc) {
    response.status(400)
    response.send('{"error": "invalid_request", "error_description": "vc is required"}').status(400)
    return
  }
  const { payload, error } = await issueVcJwt({
    vcPayload: request.body.vc,
  })
  if (payload && !error) {
    response.send(`{ "vc": "${payload}"}`)
    return
  }
  response.status(400)
  response.send('{"error": "invalid_request"}')
})

app.post("/v1/verifiable-credentials/did-jwt/verify", async (request, response) => {
  console.log(request.body)
  if (!request.body.vc_jwt) {
    response.status(400)
    response.send('{"error": "invalid_request", "error_description": "vc_jwt is required"}').status(400)
    return
  }
  const { payload, error } = await verifyVcJwt({
    vcJwt: request.body.vc_jwt,
  })
  if (payload && !error) {
    console.log(payload)
    response.send(`{ "verified_vc": ${JSON.stringify(payload)}}`)
    return
  }
  response.status(400)
  response.send('{"error": "invalid_request"}')
})

const issueVcJwt = async ({ vcPayload }) => {
  try {

    const vcJwt = await createVerifiableCredentialJwt(vcPayload, issuer)
    console.log(vcJwt)
    return {
      payload: vcJwt,
    }
  } catch (e) {
    console.error(e)
    return {
      error: e
    }
  }
}

const verifyVcJwt = async ({ vcJwt }) => {
  try {
    const verifiedVC = await verifyCredential(vcJwt, new UniversalResolver())
    console.log(verifiedVC)
    return {
      payload: verifiedVC
    }
  } catch (e) {
   console.error(e)
   return {
     error: e
   }
  }
}

class UniversalResolver {

  async resolve(didUrl, options = {}) {
    const parsed = parse(didUrl)
    console.log(parsed)
    if (parsed === null) {
      return {
        didResolutionMetadata: { error: 'invalidDid' },
      }
    }
    const { did } = parsed
    const encodedUrl = encodeURI(`https://dev.uniresolver.io/1.0/identifiers/${did}`)
    const didResponse = await get({ url: encodedUrl, headers: {} })
    console.log(didResponse)
    return didResponse.data
  }
}

const get = async ({ url, headers }) => {
  try {
    return await axios.get(url, {
      maxRedirects: 0,
      headers,
    });
  } catch (e) {
    return e.response ? e.response : e;
  }
};

const { spawn, exec } = require('child_process');
const fs = require('fs');
const path = require('path');

let ISSUER_PATH = '/usr/local/bin/cert-issuer';
const UNSIGNED_CERTIFICATES_DIR = '/etc/cert-issuer/data/unsigned_certificates';
const SIGNED_CERTIFICATES_DIR = '/etc/cert-issuer/data/blockchain_certificates';

function setIssuerPath (pathToIssuer) {
  ISSUER_PATH = pathToIssuer; // TODO: refactor to class to avoid global variable
}

function getIssuerPath () {
  return ISSUER_PATH; // TODO: refactor to class to avoid global variable
}

function isRelativePath (path) {
  return path.startsWith('./') || path.startsWith('../');
}

function getRootPath () {
  if (isRelativePath(getIssuerPath())) {
    return path.join(process.cwd(), getIssuerPath());
  }

  return getIssuerPath();
}

function getUnsignedCertificatesPath (i) {
  return path.join(UNSIGNED_CERTIFICATES_DIR, getFileName(i));
}

function getSignedCertificatesPath (i) {
  return path.join(SIGNED_CERTIFICATES_DIR, getFileName(i));
}

function getFileName (i) {
  return `sample-${i}.json`;
}

function saveFileToUnsignedCertificates (data, i) {
  const targetPath = getUnsignedCertificatesPath(i);
  // console.log('save file to', targetPath);
  try {
    fs.writeFileSync(targetPath, JSON.stringify(data));
  } catch (e) {
    console.error('Error saving file at', targetPath, e);
  }
}

async function getSignedCertificates (count) {
  let targetPaths = [];
  // console.log(`retrieving ${count} certificates after issuance`);


  targetPaths.push(getSignedCertificatesPath(1));

  // console.log('certificates are located at', targetPaths);

  return new Promise((resolve, reject) => {
    const certificates = targetPaths.map(path => fs.readFileSync(path, 'utf8'));
    resolve(certificates);
  });
}

function deleteTestCertificates (count) {
  const targetPaths = [];
  for (let i = 0; i < count; i++) {
    targetPaths.push(getUnsignedCertificatesPath(i));
    targetPaths.push(getSignedCertificatesPath(i));
  }
  targetPaths.forEach(path => {
    try {
      fs.unlinkSync(path)
    } catch {}
  });
}

function getPythonPath () {
  return new Promise((resolve, reject) => {
    exec('which python3', (error, stdout, stderr) => {
      if (error) {
        console.error(`Error finding path to python3: ${error}`);
        reject(`Error finding path to python3: ${error}`);
        return;
      }

      const pythonPath = stdout.trim();
      // console.log(`Found path to python3: ${pythonPath}`);
      resolve(pythonPath);
    });
  });
}

const issueVcBlockCert = async ({ vcPayload }) =>{

  saveFileToUnsignedCertificates(vcPayload, 1);
  let stdout = [];
  let stderr = [];
  const pythonPath = await getPythonPath();
  const spawnArgs = ['-c', `./conf.ini`]
  console.log('Spawning python from path:', pythonPath, 'with args', spawnArgs);
  const verificationProcess = spawn("cert-issuer", spawnArgs);
  verificationProcess.stdout.pipe(process.stdout);

  try {
    verificationProcess.on('error', err => {
      console.log('python script error', err);
    });
    verificationProcess.stdout.on('error', err => reject(new Error(err)));
    verificationProcess.stderr.on('error', err => reject(new Error(err)));
    verificationProcess.stdin.on('error', err => reject(new Error(err)));

    verificationProcess.stdout.on('data', data => stdout.push(data));
    verificationProcess.stderr.on('data', data => stderr.push(data));

    verificationProcess.stdout.on('end', () => {
      console.log('Issue.js stdout end:', Buffer.concat(stdout).toString());
    });
    verificationProcess.stderr.on('end', () => {
      console.log('Issue.js stderr end (ERROR):', Buffer.concat(stderr).toString());
    });

    verificationProcess.stdin.end('');

    verificationProcess.on('exit', code => {
      if (code !== 0) {
        console.log('exit event in python cert-issuer', code);
      }
    });

    verificationProcess.on('close', async code => {
      stdout = stdout.join('').trim();
      stderr = stderr.join('').trim();
      if (code === 0) {
        const certificates = await getSignedCertificates(1);
        deleteTestCertificates(1);
        return {
          payload: certificates
        };
      }
      deleteTestCertificates(1);
      return {
        error: "can not issue vc",
      };
    });
  } catch (e) {
    console.log('caught server error', e);
    return {
      error: e
    }
  }
}



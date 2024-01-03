const { spawn, exec } = require('child_process');
const fs = require('fs');
const path = require('path');

let ISSUER_PATH = '../cert-issuer';
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
  return path.join(getRootPath(), UNSIGNED_CERTIFICATES_DIR, getFileName(i));
}

function getSignedCertificatesPath (i) {
  return path.join(getRootPath(), SIGNED_CERTIFICATES_DIR, getFileName(i));
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

  for (let i = 0; i < count; i++) {
    targetPaths.push(getSignedCertificatesPath(i));
  }

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

export const issueVcBlockCert = async ({ vcPayload }) =>{

  saveFileToUnsignedCertificates(vcPayload, 1);
  let stdout = [];
  let stderr = [];
  const pythonPath = await getPythonPath();
  const spawnArgs = [`${ISSUER_PATH}/cert_issuer`, '-c', `${ISSUER_PATH}/conf.ini`]
  // console.log('Spawning python from path:', pythonPath, 'with args', spawnArgs);
  const verificationProcess = spawn(pythonPath, spawnArgs, {
    cwd: ISSUER_PATH
  });
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
        success: false,
        stderr
      };
    });
  } catch (e) {
    console.log('caught server error', e);
    return {
      error: e
    }
  }
}


module.exports = issue;

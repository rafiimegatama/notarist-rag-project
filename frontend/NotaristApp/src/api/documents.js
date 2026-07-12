import client from './client';
import * as Crypto from 'expo-crypto';
import { File } from 'expo-file-system';

export async function listDocuments(page = 0, size = 20) {
  const response = await client.get('/documents', { params: { page, size } });
  return response.data;
}

export async function getDocument(documentId) {
  const response = await client.get(`/documents/${documentId}`);
  return response.data.data;
}

export async function computeFileSha256(fileUri) {
  const file = new File(fileUri);
  const buffer = await file.arrayBuffer();
  const digest = await Crypto.digest(Crypto.CryptoDigestAlgorithm.SHA256, buffer);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

export async function initiateUpload(payload) {
  // payload: { originalFilename, checksumSha256, documentType, classificationLevel }
  const response = await client.post('/ingest', payload);
  return response.data.data;
}

export async function uploadFileToSignedUrl(signedUrl, fileUri, requiredHeaders) {
  const file = new File(fileUri);
  const result = await file.upload(signedUrl, {
    httpMethod: 'PUT',
    headers: requiredHeaders,
  });
  if (result.status < 200 || result.status >= 300) {
    throw new Error(`File upload failed with status ${result.status}`);
  }
  return result;
}

export async function confirmUpload(jobId, checksumSha256) {
  const response = await client.post(`/ingest/${jobId}/confirm`, { checksumSha256 });
  return response.data;
}

export async function getIngestionStatus(jobId) {
  const response = await client.get(`/ingest/${jobId}/status`);
  return response.data.data;
}

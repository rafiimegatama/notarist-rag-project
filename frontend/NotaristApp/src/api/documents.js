import client from './client';

export async function listDocuments(page = 0, size = 20) {
  const response = await client.get('/documents', { params: { page, size } });
  return response.data;
}

export async function getDocument(documentId) {
  const response = await client.get(`/documents/${documentId}`);
  return response.data.data;
}

export async function initiateUpload(payload) {
  // payload: { originalFilename, checksumSha256, documentType, classificationLevel }
  const response = await client.post('/ingest/initiate', payload);
  return response.data.data;
}

export async function confirmUpload(jobId, checksumSha256) {
  const response = await client.post(`/ingest/confirm/${jobId}`, { checksumSha256 });
  return response.data;
}

export async function getIngestionStatus(jobId) {
  const response = await client.get(`/ingest/status/${jobId}`);
  return response.data.data;
}

export async function deleteDocument(documentId) {
  const response = await client.delete(`/documents/${documentId}`);
  return response.data;
}

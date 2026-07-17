// Isolates account registration behind a single seam. The backend has NO POST /auth/register
// endpoint, so this service reports itself unavailable and refuses to call anything. It never
// fakes a success. When the endpoint exists, implement register() against the API client and flip
// FEATURES.registerEndpoint to true — the RegisterScreen already reacts to `available`.

import { FEATURES } from '../constants/config';

export class EndpointUnavailableError extends Error {
  constructor(message = 'Endpoint backend belum tersedia.') {
    super(message);
    this.name = 'EndpointUnavailableError';
    this.code = 'ENDPOINT_UNAVAILABLE';
  }
}

export const RegisterService = {
  // UI reads this to decide whether to enable the submit button.
  available: FEATURES.registerEndpoint,

  /**
   * Attempt to register. Currently always throws EndpointUnavailableError because no backend
   * endpoint exists. Intentionally does not touch the API client so no phantom request is sent.
   *
   * @param {{fullName:string, username:string, email:string, password:string}} _payload
   */
  async register(_payload) {
    if (!this.available) {
      throw new EndpointUnavailableError();
    }
    // Future: return client.post('/auth/register', _payload).then(r => r.data.data);
    throw new EndpointUnavailableError();
  },
};

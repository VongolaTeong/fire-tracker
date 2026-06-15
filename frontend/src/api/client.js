// Thin REST client for the FIRE Tracker backend. The base URL is environment-driven so the same
// build points at localhost in dev and the deployed API in production (Step 9).
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(/\/+$/, '');

async function getJson(path) {
  const response = await fetch(`${API_BASE_URL}${path}`);
  if (!response.ok) {
    // The backend returns RFC 9457 problem responses ({ title, detail, ... }); surface `detail`.
    let message = `Request failed: ${response.status} ${response.statusText}`;
    try {
      const problem = await response.json();
      if (problem && problem.detail) {
        message = problem.detail;
      }
    } catch {
      // non-JSON error body — keep the status-line message
    }
    throw new Error(message);
  }
  return response.json();
}

export const api = {
  baseUrl: API_BASE_URL,
  value: () => getJson('/api/portfolio/value'),
  valueHistory: () => getJson('/api/portfolio/value-history'),
  performance: () => getJson('/api/portfolio/performance'),
  holdings: () => getJson('/api/portfolio/holdings'),
  projection: (targetDate) =>
    getJson(`/api/portfolio/projection?targetDate=${encodeURIComponent(targetDate)}`),
};

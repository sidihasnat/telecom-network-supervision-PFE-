/**
 * AI API Service — Flask (port 5000)
 * AI Engine endpoints.
 */

const AI_BASE_URL = '/ai';

async function request(url, options = {}) {
  try {
    const res = await fetch(url, options);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return res.json();
  } catch (err) {
    console.error(`AI API Error [${url}]:`, err.message);
    throw err;
  }
}

/** Get AI engine status */
export function getAiStatus() {
  return request(`${AI_BASE_URL}/status`);
}

/** Start live prediction */
export function startLivePrediction() {
  return request(`${AI_BASE_URL}/predict/live`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ action: 'start' }),
  });
}

/** Stop live prediction */
export function stopLivePrediction() {
  return request(`${AI_BASE_URL}/predict/live`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ action: 'stop' }),
  });
}

/** Update AI config (sliding window, threshold) */
export function updateAiConfig(config) {
  return request(`${AI_BASE_URL}/config`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      slidingWindowSize: config.slidingWindowSize ?? config.window_size,
      highConfidenceThreshold: config.highConfidenceThreshold ?? config.high_confidence_threshold,
    }),
  });
}

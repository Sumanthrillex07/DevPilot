export interface HealthResponse {
  status: string;
  app: string;
  timestamp: string;
  version: string;
}

export async function fetchHealthStatus(): Promise<HealthResponse> {
  const response = await fetch('/api/v1/health', {
    headers: {
      'Accept': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`Health check failed with HTTP ${response.status}: ${response.statusText}`);
  }

  return response.json();
}

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HealthStatus } from './HealthStatus';
import * as client from '@/api/client';

describe('HealthStatus Component', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    });
    vi.restoreAllMocks();
  });

  const renderComponent = () => {
    return render(
      <QueryClientProvider client={queryClient}>
        <HealthStatus />
      </QueryClientProvider>
    );
  };

  it('renders loading state initially', () => {
    vi.spyOn(client, 'fetchHealthStatus').mockImplementation(
      () => new Promise(() => {}) // never resolves
    );

    renderComponent();
    expect(screen.getByText(/Connecting.../i)).toBeInTheDocument();
  });

  it('renders healthy state when backend returns UP', async () => {
    vi.spyOn(client, 'fetchHealthStatus').mockResolvedValue({
      status: 'UP',
      app: 'devpilot-backend',
      timestamp: '2026-10-27T14:15:00Z',
      version: '1.0.0',
    });

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText(/Online & Healthy/i)).toBeInTheDocument();
    });

    expect(screen.getByText('devpilot-backend')).toBeInTheDocument();
  });

  it('renders error banner when backend fails', async () => {
    vi.spyOn(client, 'fetchHealthStatus').mockRejectedValue(
      new Error('Connection refused')
    );

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText(/Service Offline/i)).toBeInTheDocument();
    });

    expect(screen.getByText(/Unable to connect to backend/i)).toBeInTheDocument();
  });
});

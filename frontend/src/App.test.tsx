import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import App from './App';
import * as client from '@/api/client';

describe('App Root Component', () => {
  it('renders application brand and navigation shell', async () => {
    vi.spyOn(client, 'fetchHealthStatus').mockResolvedValue({
      status: 'UP',
      app: 'devpilot-backend',
      timestamp: '2026-10-27T14:15:00Z',
      version: '1.0.0',
    });

    vi.spyOn(client, 'fetchRepositories').mockResolvedValue([]);

    render(<App />);

    expect(screen.getByText('DevPilot')).toBeInTheDocument();
    expect(screen.getByText('Overview')).toBeInTheDocument();
    expect(screen.getByText('Repositories')).toBeInTheDocument();
    expect(screen.getByText('Codebase Chat')).toBeInTheDocument();
    expect(screen.getByText('Bug Investigator')).toBeInTheDocument();
    expect(screen.getByText('PR Reviewer')).toBeInTheDocument();
    expect(screen.getByText('Patch Studio')).toBeInTheDocument();
  });
});

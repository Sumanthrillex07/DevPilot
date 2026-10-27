import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import App from './App';
import * as client from '@/api/client';

describe('App Root Component', () => {
  it('renders application brand and navigation shell', () => {
    vi.spyOn(client, 'fetchHealthStatus').mockResolvedValue({
      status: 'UP',
      app: 'devpilot-backend',
      timestamp: '2026-10-27T14:15:00Z',
      version: '1.0.0',
    });

    render(<App />);

    expect(screen.getByText('DevPilot')).toBeInTheDocument();
    expect(screen.getByText('AI-Powered Codebase Intelligence')).toBeInTheDocument();
    expect(screen.getByText('Engineering Roadmap')).toBeInTheDocument();
  });
});

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BugInvestigatorPage } from './BugInvestigatorPage';
import { RepoProvider } from '@/lib/repoContext';
import * as client from '@/api/client';
import type { ConnectedRepository, BugReportResponse } from '@/api/types';

const mockRepo: ConnectedRepository = {
  id: 'repo-1',
  githubRepoId: 101,
  owner: 'octocat',
  name: 'backend',
  fullName: 'octocat/backend',
  isPrivate: false,
  defaultBranch: 'main',
  language: 'Java',
  htmlUrl: null,
  description: 'Backend API',
  indexStatus: 'INDEXED',
  indexedAt: null,
  chunkCount: 10,
  filesTotal: 5,
  filesProcessed: 5,
  errorMessage: null,
};

const mockReport: BugReportResponse = {
  summary: 'Null pointer in UserService',
  rootCause: 'Missing null check when querying non-existent user by ID.',
  suspectedFiles: ['src/services/UserService.java'],
  reproductionSteps: ['Call GET /users/999 without seeded data'],
  suggestedFixes: ['Use Optional<User> and return 404 instead of throwing NPE.'],
  citations: [],
};

describe('BugInvestigatorPage', () => {
  it('submits bug trace and renders diagnostic report', async () => {
    vi.spyOn(client, 'fetchRepositories').mockResolvedValue([mockRepo]);
    const investigateSpy = vi.spyOn(client, 'investigateBug').mockResolvedValue(mockReport);

    render(
      <RepoProvider>
        <BugInvestigatorPage />
      </RepoProvider>
    );

    await waitFor(() => {
      expect(screen.getByText(/Autonomous Bug Investigator/i)).toBeInTheDocument();
    });

    const descInput = screen.getByLabelText(/Bug Description/i);
    fireEvent.change(descInput, { target: { value: 'NPE on missing user' } });

    const submitBtn = screen.getByRole('button', { name: /Diagnose Bug/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(investigateSpy).toHaveBeenCalledWith('repo-1', expect.objectContaining({
        description: 'NPE on missing user',
      }));
      expect(screen.getByText('Null pointer in UserService')).toBeInTheDocument();
      expect(screen.getByText(/Missing null check/i)).toBeInTheDocument();
      expect(screen.getByText('src/services/UserService.java')).toBeInTheDocument();
    });
  });
});

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { RepositoriesPage } from './RepositoriesPage';
import { RepoProvider } from '@/lib/repoContext';
import * as client from '@/api/client';
import type { ConnectedRepository } from '@/api/types';

const mockRepo: ConnectedRepository = {
  id: 'repo-123',
  githubRepoId: 999,
  owner: 'octocat',
  name: 'hello-world',
  fullName: 'octocat/hello-world',
  isPrivate: false,
  defaultBranch: 'main',
  language: 'TypeScript',
  htmlUrl: 'https://github.com/octocat/hello-world',
  description: 'Test repository for DevPilot',
  indexStatus: 'INDEXED',
  indexedAt: '2026-10-30T12:00:00Z',
  chunkCount: 24,
  filesTotal: 6,
  filesProcessed: 6,
  errorMessage: null,
};

describe('RepositoriesPage', () => {
  it('renders repository list and triggers indexing', async () => {
    vi.spyOn(client, 'fetchRepositories').mockResolvedValue([mockRepo]);
    const indexSpy = vi.spyOn(client, 'indexRepository').mockResolvedValue({
      repositoryId: 'repo-123',
      indexStatus: 'INDEXED',
      filesTotal: 6,
      filesProcessed: 6,
      chunkCount: 24,
      indexedAt: '2026-10-30T12:00:00Z',
      errorMessage: null,
    });

    render(
      <RepoProvider>
        <RepositoriesPage />
      </RepoProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('hello-world')).toBeInTheDocument();
      expect(screen.getByText('Indexed')).toBeInTheDocument();
      expect(screen.getByText('24 chunks')).toBeInTheDocument();
    });

    const indexBtn = screen.getByRole('button', { name: /Re-Index/i });
    fireEvent.click(indexBtn);

    await waitFor(() => {
      expect(indexSpy).toHaveBeenCalledWith('repo-123');
    });
  });
});

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { PrReviewPage } from './PrReviewPage';
import { RepoProvider } from '@/lib/repoContext';
import * as client from '@/api/client';
import type { ConnectedRepository, PrReviewResponse } from '@/api/types';

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

const mockReview: PrReviewResponse = {
  verdict: 'APPROVED',
  riskScore: 15,
  summary: 'Code changes look clean and follow standard patterns.',
  examinedFilesCount: 1,
  comments: [
    {
      filePath: 'src/AuthService.java',
      lineNumber: 28,
      comment: 'Good use of cryptographically secure token generation.',
      severity: 'INFO',
      category: 'SECURITY',
    },
  ],
  citations: [],
};

describe('PrReviewPage', () => {
  it('submits diff for review and displays verdict and inline annotations', async () => {
    vi.spyOn(client, 'fetchRepositories').mockResolvedValue([mockRepo]);
    const reviewSpy = vi.spyOn(client, 'reviewDiff').mockResolvedValue(mockReview);

    render(
      <RepoProvider>
        <PrReviewPage />
      </RepoProvider>
    );

    await waitFor(() => {
      expect(screen.getByText(/Pull Request Review/i)).toBeInTheDocument();
    });

    const submitBtn = screen.getByRole('button', { name: /Analyze Code Quality/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(reviewSpy).toHaveBeenCalled();
      expect(screen.getByText('Approved')).toBeInTheDocument();
      expect(screen.getByText('15')).toBeInTheDocument();
      expect(screen.getByText(/Good use of cryptographically secure/i)).toBeInTheDocument();
      expect(screen.getByText('src/AuthService.java:28')).toBeInTheDocument();
    });
  });
});

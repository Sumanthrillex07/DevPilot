import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { EvaluationPage } from './EvaluationPage';
import { RepoProvider } from '@/lib/repoContext';
import * as client from '@/api/client';
import type { ConnectedRepository, BenchmarkSuiteResult } from '@/api/types';

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

const mockSuite: BenchmarkSuiteResult = {
  repositoryId: 'repo-1',
  totalCases: 3,
  passedCases: 3,
  averageLatencyMs: 45.2,
  averageRelevanceScore: 0.95,
  overallBenchmarkScore: 1.0,
  cases: [
    {
      caseName: 'Authentication',
      query: 'Find login endpoints',
      retrievedCitationsCount: 2,
      latencyMs: 40,
      contextRelevanceScore: 0.9,
      citationValidityScore: 1.0,
      passed: true,
    },
  ],
  executedAt: '2026-10-30T12:00:00Z',
};

describe('EvaluationPage', () => {
  it('runs benchmark suite and renders metric cards and case table', async () => {
    vi.spyOn(client, 'fetchRepositories').mockResolvedValue([mockRepo]);
    const benchmarkSpy = vi.spyOn(client, 'runBenchmark').mockResolvedValue(mockSuite);

    render(
      <RepoProvider>
        <EvaluationPage />
      </RepoProvider>
    );

    await waitFor(() => {
      expect(screen.getByText(/Observability & RAG Evaluation Suite/i)).toBeInTheDocument();
    });

    const runBtn = screen.getByRole('button', { name: /Run Benchmark Suite/i });
    fireEvent.click(runBtn);

    await waitFor(() => {
      expect(benchmarkSpy).toHaveBeenCalledWith('repo-1');
      expect(screen.getByText('100%')).toBeInTheDocument();
      expect(screen.getByText('45.2')).toBeInTheDocument();
      expect(screen.getByText('Authentication')).toBeInTheDocument();
    });
  });
});

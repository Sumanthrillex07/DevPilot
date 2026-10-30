import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { PatchStudioPage } from './PatchStudioPage';
import { RepoProvider } from '@/lib/repoContext';
import * as client from '@/api/client';
import type { ConnectedRepository, GeneratedPatchResponse } from '@/api/types';

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

const mockPatch: GeneratedPatchResponse = {
  patchId: 'patch-abc',
  instruction: 'Fix addition bug',
  explanation: 'Replaced minus with plus operator',
  unifiedDiff: 'diff --git a/Calc.java b/Calc.java\n+return a + b;',
  affectedFiles: ['Calc.java'],
  validation: {
    valid: true,
    errors: [],
    warnings: [],
    filesChanged: 1,
    linesAdded: 1,
    linesDeleted: 1,
    dryRunSuccess: true,
  },
  citations: [],
  generatedAt: '2026-10-30T12:00:00Z',
};

describe('PatchStudioPage', () => {
  it('generates patch and shows validation status banner', async () => {
    vi.spyOn(client, 'fetchRepositories').mockResolvedValue([mockRepo]);
    const generateSpy = vi.spyOn(client, 'generatePatch').mockResolvedValue(mockPatch);

    render(
      <RepoProvider>
        <PatchStudioPage />
      </RepoProvider>
    );

    await waitFor(() => {
      expect(screen.getByText(/Safe Patch Studio/i)).toBeInTheDocument();
    });

    const instructionInput = screen.getByLabelText(/Patch Instruction/i);
    fireEvent.change(instructionInput, { target: { value: 'Fix addition bug' } });

    const generateBtn = screen.getByRole('button', { name: /Generate AI Patch/i });
    fireEvent.click(generateBtn);

    await waitFor(() => {
      expect(generateSpy).toHaveBeenCalled();
      expect(screen.getByText(/Patch Passed Security & AST Safety Checks/i)).toBeInTheDocument();
      expect(screen.getByText(/Replaced minus with plus operator/i)).toBeInTheDocument();
      expect(screen.getByText(/diff --git a\/Calc.java/i)).toBeInTheDocument();
    });
  });
});

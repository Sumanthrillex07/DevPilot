import type {
  ConnectedRepository,
  IndexStatusResponse,
  ChatSession,
  ChatMessage,
  BugReportResponse,
  PrReviewResponse,
  GeneratedPatchResponse,
  PatchValidationResult,
  DryRunPatchResponse,
  EvaluationResult,
  BenchmarkSuiteResult,
  UserProfile,
  CitationDto
} from './types';

export interface HealthResponse {
  status: string;
  app: string;
  timestamp: string;
  version: string;
}

const API_BASE = '/api/v1';

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const url = `${API_BASE}${endpoint}`;
  const response = await fetch(url, {
    ...options,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      ...options.headers,
    },
  });

  if (!response.ok) {
    const errorBody = await response.text().catch(() => '');
    throw new Error(`API error ${response.status}: ${errorBody || response.statusText}`);
  }

  return response.json();
}

// ---- Health & Auth ----
export async function fetchHealthStatus(): Promise<HealthResponse> {
  return request<HealthResponse>('/health');
}

export async function fetchCurrentUser(): Promise<UserProfile> {
  return request<UserProfile>('/auth/me');
}

// ---- Repositories & Indexing ----
export async function fetchRepositories(refresh: boolean = false): Promise<ConnectedRepository[]> {
  return request<ConnectedRepository[]>(`/repos?refresh=${refresh}`);
}

export async function indexRepository(repoId: string): Promise<IndexStatusResponse> {
  return request<IndexStatusResponse>(`/repos/${repoId}/index`, { method: 'POST' });
}

export async function fetchIndexStatus(repoId: string): Promise<IndexStatusResponse> {
  return request<IndexStatusResponse>(`/repos/${repoId}/status`);
}

// ---- Chat Sessions & Messages ----
export async function fetchChatSessions(repoId: string): Promise<ChatSession[]> {
  return request<ChatSession[]>(`/chat/sessions?repositoryId=${repoId}`);
}

export async function createChatSession(repoId: string, title?: string): Promise<ChatSession> {
  return request<ChatSession>(`/chat/sessions`, {
    method: 'POST',
    body: JSON.stringify({ repositoryId: repoId, title: title || 'New Chat' }),
  });
}

export async function fetchChatMessages(sessionId: string): Promise<ChatMessage[]> {
  return request<ChatMessage[]>(`/chat/sessions/${sessionId}`);
}

// ---- Bug Investigation ----
export interface InvestigateBugPayload {
  description: string;
  rawErrorLog?: string;
  failingTestSnippet?: string;
  environmentDetails?: string;
}

export async function investigateBug(repoId: string, payload: InvestigateBugPayload): Promise<BugReportResponse> {
  return request<BugReportResponse>(`/repos/${repoId}/investigate`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

// ---- PR Review ----
export async function reviewGitHubPr(repoId: string, prNumber: number): Promise<PrReviewResponse> {
  return request<PrReviewResponse>(`/repos/${repoId}/reviews/github/${prNumber}`, {
    method: 'POST',
  });
}

export interface ReviewDiffPayload {
  title: string;
  description: string;
  diffText: string;
}

export async function reviewDiff(repoId: string, payload: ReviewDiffPayload): Promise<PrReviewResponse> {
  return request<PrReviewResponse>(`/repos/${repoId}/reviews/diff`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

// ---- Patch Studio ----
export interface GeneratePatchPayload {
  instruction: string;
  targetFile?: string;
  errorContext?: string;
  contextFilePaths?: string[];
}

export async function generatePatch(repoId: string, payload: GeneratePatchPayload): Promise<GeneratedPatchResponse> {
  return request<GeneratedPatchResponse>(`/repos/${repoId}/patches/generate`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export interface ValidatePatchPayload {
  diff: string;
  targetFilePath?: string;
  originalFileContent?: string;
}

export async function validatePatch(repoId: string, payload: ValidatePatchPayload): Promise<PatchValidationResult> {
  return request<PatchValidationResult>(`/repos/${repoId}/patches/validate`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export interface DryRunPatchPayload {
  diff: string;
  originalContent: string;
}

export async function dryRunPatch(repoId: string, payload: DryRunPatchPayload): Promise<DryRunPatchResponse> {
  return request<DryRunPatchResponse>(`/repos/${repoId}/patches/dry-run`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

// ---- Evaluation & Benchmarks ----
export interface EvaluationPayload {
  query: string;
  answer: string;
  retrievedContext?: string;
  citations?: CitationDto[];
}

export async function evaluateInteraction(repoId: string, payload: EvaluationPayload): Promise<EvaluationResult> {
  return request<EvaluationResult>(`/repos/${repoId}/evaluation/evaluate`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function runBenchmark(repoId: string): Promise<BenchmarkSuiteResult> {
  return request<BenchmarkSuiteResult>(`/repos/${repoId}/evaluation/benchmark`);
}

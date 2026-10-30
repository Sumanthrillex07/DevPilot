export type IndexStatus = 'PENDING' | 'IN_PROGRESS' | 'INDEXED' | 'FAILED';

export interface ConnectedRepository {
  id: string;
  githubRepoId: number;
  owner: string;
  name: string;
  fullName: string;
  isPrivate: boolean;
  defaultBranch: string;
  language: string | null;
  htmlUrl: string | null;
  description: string | null;
  indexStatus: IndexStatus;
  indexedAt: string | null;
  chunkCount: number;
  filesTotal: number;
  filesProcessed: number;
  errorMessage: string | null;
}

export interface IndexStatusResponse {
  repositoryId: string;
  indexStatus: IndexStatus;
  filesTotal: number;
  filesProcessed: number;
  chunkCount: number;
  indexedAt: string | null;
  errorMessage: string | null;
}

export interface CitationDto {
  filePath: string;
  startLine: number | null;
  endLine: number | null;
  language: string | null;
}

export interface ChatSession {
  id: string;
  repositoryId: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  citations?: CitationDto[];
  createdAt: string;
}

export interface BugReportResponse {
  summary: string;
  rootCause: string;
  suspectedFiles: string[];
  reproductionSteps: string[];
  suggestedFixes: string[];
  citations: CitationDto[];
}

export type ReviewVerdict = 'APPROVED' | 'COMMENT' | 'REQUEST_CHANGES';
export type CommentSeverity = 'CRITICAL' | 'WARNING' | 'INFO' | 'SUGGESTION';
export type CommentCategory = 'SECURITY' | 'BUG_RISK' | 'PERFORMANCE' | 'MAINTAINABILITY' | 'CODE_STYLE';

export interface PrReviewCommentDto {
  filePath: string;
  lineNumber: number;
  comment: string;
  severity: CommentSeverity;
  category: CommentCategory;
  suggestedCode?: string;
}

export interface PrReviewResponse {
  verdict: ReviewVerdict;
  riskScore: number;
  summary: string;
  examinedFilesCount: number;
  comments: PrReviewCommentDto[];
  citations: CitationDto[];
}

export interface PatchValidationResult {
  valid: boolean;
  errors: string[];
  warnings: string[];
  filesChanged: number;
  linesAdded: number;
  linesDeleted: number;
  dryRunSuccess: boolean;
}

export interface GeneratedPatchResponse {
  patchId: string;
  instruction: string;
  explanation: string;
  unifiedDiff: string;
  affectedFiles: string[];
  validation: PatchValidationResult;
  citations: CitationDto[];
  generatedAt: string;
}

export interface DryRunPatchResponse {
  success: boolean;
  patchedContent: string | null;
  errors: string[];
  linesAdded: number;
  linesDeleted: number;
}

export interface EvaluationResult {
  contextRelevanceScore: number;
  groundednessScore: number;
  citationValidityScore: number;
  overallScore: number;
  detectedHallucinations: string[];
  recommendations: string[];
  evaluatedAt: string;
}

export interface BenchmarkCaseResult {
  caseName: string;
  query: string;
  retrievedCitationsCount: number;
  latencyMs: number;
  contextRelevanceScore: number;
  citationValidityScore: number;
  passed: boolean;
}

export interface BenchmarkSuiteResult {
  repositoryId: string;
  totalCases: number;
  passedCases: number;
  averageLatencyMs: number;
  averageRelevanceScore: number;
  overallBenchmarkScore: number;
  cases: BenchmarkCaseResult[];
  executedAt: string;
}

export interface UserProfile {
  id: string;
  githubUsername: string;
  displayName: string | null;
  avatarUrl: string | null;
  email: string | null;
}

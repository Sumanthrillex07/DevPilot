import { useState } from 'react';
import { useRepo } from '@/lib/repoContext';
import { runBenchmark, evaluateInteraction } from '@/api/client';
import type { BenchmarkSuiteResult, EvaluationResult } from '@/api/types';
import {
  Activity,
  Play,
  Gauge,
  Timer,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  FolderGit2,
  Loader2,
  Sparkles,
} from 'lucide-react';

export function EvaluationPage() {
  const { activeRepo } = useRepo();
  const [isRunningBenchmark, setIsRunningBenchmark] = useState(false);
  const [benchmarkResult, setBenchmarkResult] = useState<BenchmarkSuiteResult | null>(null);

  // Single Evaluation State
  const [evalQuery, setEvalQuery] = useState('How does GitHub OAuth login create users in the database?');
  const [evalAnswer, setEvalAnswer] = useState('The `GithubOAuth2UserService` processes the OAuth callback, checks if the user exists by githubId, and calls `UserRepository.save()`.');
  const [evalContext, setEvalContext] = useState('public class GithubOAuth2UserService { public OAuth2User loadUser() { userRepository.save(user); } }');
  const [isEvaluating, setIsEvaluating] = useState(false);
  const [evalResult, setEvalResult] = useState<EvaluationResult | null>(null);

  const [error, setError] = useState<string | null>(null);

  const handleRunBenchmark = async () => {
    if (!activeRepo) return;
    setIsRunningBenchmark(true);
    setError(null);
    try {
      const res = await runBenchmark(activeRepo.id);
      setBenchmarkResult(res);
    } catch (err: any) {
      setError(err.message || 'Benchmark run failed');
    } finally {
      setIsRunningBenchmark(false);
    }
  };

  const handleEvaluate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeRepo || !evalQuery.trim() || !evalAnswer.trim()) return;

    setIsEvaluating(true);
    setError(null);
    try {
      const res = await evaluateInteraction(activeRepo.id, {
        query: evalQuery.trim(),
        answer: evalAnswer.trim(),
        retrievedContext: evalContext.trim() || undefined,
        citations: [{ filePath: 'GithubOAuth2UserService.java', startLine: 1, endLine: 30, language: 'java' }],
      });
      setEvalResult(res);
    } catch (err: any) {
      setError(err.message || 'Evaluation failed');
    } finally {
      setIsEvaluating(false);
    }
  };

  if (!activeRepo) {
    return (
      <div className="glass-card rounded-xl p-12 text-center max-w-lg mx-auto mt-12">
        <FolderGit2 className="w-12 h-12 text-indigo-400 mx-auto mb-3" />
        <h2 className="text-lg font-bold text-white">Select a Repository</h2>
        <p className="text-sm text-slate-400 mt-1 mb-6">
          Connect or select a target repository to run RAG benchmarks and groundedness audits.
        </p>
        <a href="/repos" className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-indigo-600 text-white font-medium text-sm">
          Go to Repositories
        </a>
      </div>
    );
  }

  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2.5">
            <Activity className="w-7 h-7 text-indigo-400" />
            Observability & RAG Evaluation Suite
          </h1>
          <p className="text-sm text-slate-400 mt-1">
            Quantify retrieval latency, context relevance, answer groundedness, and hallucination rates across pgvector.
          </p>
        </div>

        <button
          id="run-benchmark-btn"
          onClick={handleRunBenchmark}
          disabled={isRunningBenchmark}
          className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white font-medium text-sm transition-all shadow-lg shadow-indigo-600/20 cursor-pointer self-start"
        >
          {isRunningBenchmark ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin" />
              Running Suite...
            </>
          ) : (
            <>
              <Play className="w-4 h-4" />
              Run Benchmark Suite
            </>
          )}
        </button>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-400 text-sm flex items-center gap-2.5">
          <AlertTriangle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Benchmark Suite Summary Cards */}
      {benchmarkResult && (
        <div className="space-y-5 animate-in fade-in duration-300">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="glass-card rounded-xl p-5 text-center">
              <div className="flex items-center justify-center gap-1.5 text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                <Gauge className="w-4 h-4 text-emerald-400" />
                Benchmark Score
              </div>
              <div className="text-3xl font-extrabold text-emerald-400">
                {Math.round(benchmarkResult.overallBenchmarkScore * 100)}%
              </div>
              <div className="text-xs text-slate-400 mt-1">
                {benchmarkResult.passedCases} of {benchmarkResult.totalCases} cases passed
              </div>
            </div>

            <div className="glass-card rounded-xl p-5 text-center">
              <div className="flex items-center justify-center gap-1.5 text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                <Timer className="w-4 h-4 text-indigo-400" />
                Avg Retrieval Latency
              </div>
              <div className="text-3xl font-extrabold text-white">
                {benchmarkResult.averageLatencyMs} <span className="text-base font-normal text-slate-400">ms</span>
              </div>
              <div className="text-xs text-slate-400 mt-1">HNSW cosine distance similarity</div>
            </div>

            <div className="glass-card rounded-xl p-5 text-center">
              <div className="flex items-center justify-center gap-1.5 text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                <Sparkles className="w-4 h-4 text-indigo-400" />
                Avg Context Relevance
              </div>
              <div className="text-3xl font-extrabold text-white">
                {Math.round(benchmarkResult.averageRelevanceScore * 100)}%
              </div>
              <div className="text-xs text-slate-400 mt-1">Query token match in chunks</div>
            </div>
          </div>

          {/* Benchmark Cases Table */}
          <div className="glass-card rounded-xl overflow-hidden border border-slate-800">
            <div className="px-5 py-3 bg-slate-900 border-b border-slate-800 text-xs font-semibold text-slate-400 uppercase tracking-wider">
              Test Case Breakdown
            </div>
            <div className="divide-y divide-slate-800/80">
              {benchmarkResult.cases.map((c, i) => (
                <div key={i} className="p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
                  <div className="space-y-1">
                    <div className="font-semibold text-white flex items-center gap-2">
                      {c.passed ? (
                        <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                      ) : (
                        <XCircle className="w-4 h-4 text-rose-400 shrink-0" />
                      )}
                      <span>{c.caseName}</span>
                    </div>
                    <p className="text-slate-400 italic">"{c.query}"</p>
                  </div>

                  <div className="flex items-center gap-4 text-slate-300 font-mono text-[11px] shrink-0">
                    <span>{c.retrievedCitationsCount} citations</span>
                    <span className="text-slate-500">|</span>
                    <span>{c.latencyMs} ms</span>
                    <span className="text-slate-500">|</span>
                    <span className={c.passed ? 'text-emerald-400' : 'text-rose-400'}>
                      {Math.round(c.contextRelevanceScore * 100)}% relevance
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Ad-Hoc Groundedness Evaluator */}
      <div className="glass-card rounded-xl p-5 space-y-4">
        <h2 className="text-base font-bold text-white flex items-center gap-2">
          <Sparkles className="w-5 h-5 text-indigo-400" />
          Single Interaction Groundedness Audit
        </h2>
        <p className="text-xs text-slate-400">
          Inspect an AI response against retrieved context to calculate groundedness and detect hallucinations.
        </p>

        <form onSubmit={handleEvaluate} className="space-y-3">
          <div>
            <label htmlFor="eval-query" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1">
              Query
            </label>
            <input
              id="eval-query"
              type="text"
              required
              value={evalQuery}
              onChange={(e) => setEvalQuery(e.target.value)}
              className="w-full px-3 py-2 rounded-lg bg-slate-900 border border-slate-800 text-xs text-slate-200 focus:outline-none focus:border-indigo-500"
            />
          </div>

          <div>
            <label htmlFor="eval-answer" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1">
              Assistant Answer
            </label>
            <textarea
              id="eval-answer"
              rows={2}
              required
              value={evalAnswer}
              onChange={(e) => setEvalAnswer(e.target.value)}
              className="w-full px-3 py-2 rounded-lg bg-slate-900 border border-slate-800 text-xs text-slate-200 focus:outline-none focus:border-indigo-500 resize-y"
            />
          </div>

          <div>
            <label htmlFor="eval-context" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1">
              Retrieved Context
            </label>
            <textarea
              id="eval-context"
              rows={3}
              value={evalContext}
              onChange={(e) => setEvalContext(e.target.value)}
              className="w-full px-3 py-2 rounded-lg bg-slate-900 border border-slate-800 text-xs font-mono text-slate-200 focus:outline-none focus:border-indigo-500 resize-y"
            />
          </div>

          <button
            id="evaluate-interaction-btn"
            type="submit"
            disabled={isEvaluating}
            className="w-full py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-slate-200 transition-colors flex items-center justify-center gap-1.5 cursor-pointer"
          >
            {isEvaluating ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Activity className="w-3.5 h-3.5 text-indigo-400" />}
            Audit Groundedness & Citations
          </button>
        </form>

        {evalResult && (
          <div className="p-4 rounded-xl bg-slate-900/90 border border-slate-800 space-y-3 animate-in fade-in duration-200">
            <div className="grid grid-cols-4 gap-2 text-center">
              <div className="p-2 rounded bg-slate-950">
                <div className="text-base font-bold text-indigo-400">{Math.round(evalResult.overallScore * 100)}%</div>
                <div className="text-[10px] text-slate-400 uppercase">Overall</div>
              </div>
              <div className="p-2 rounded bg-slate-950">
                <div className="text-base font-bold text-white">{Math.round(evalResult.groundednessScore * 100)}%</div>
                <div className="text-[10px] text-slate-400 uppercase">Grounded</div>
              </div>
              <div className="p-2 rounded bg-slate-950">
                <div className="text-base font-bold text-white">{Math.round(evalResult.contextRelevanceScore * 100)}%</div>
                <div className="text-[10px] text-slate-400 uppercase">Relevance</div>
              </div>
              <div className="p-2 rounded bg-slate-950">
                <div className="text-base font-bold text-white">{Math.round(evalResult.citationValidityScore * 100)}%</div>
                <div className="text-[10px] text-slate-400 uppercase">Citations</div>
              </div>
            </div>

            {evalResult.recommendations.length > 0 && (
              <div className="text-xs text-slate-300">
                <span className="font-semibold text-slate-400">Recommendations: </span>
                {evalResult.recommendations.join(' ')}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

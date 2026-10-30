import { useState } from 'react';
import { useRepo } from '@/lib/repoContext';
import { reviewGitHubPr, reviewDiff } from '@/api/client';
import type { PrReviewResponse, CommentSeverity } from '@/api/types';
import {
  GitPullRequest,
  CheckCircle2,
  AlertTriangle,
  AlertOctagon,
  MessageSquare,
  Flame,
  Code2,
  Loader2,
  FolderGit2,
  FileCode,
} from 'lucide-react';

export function PrReviewPage() {
  const { activeRepo } = useRepo();
  const [mode, setMode] = useState<'github' | 'diff'>('diff');
  const [prNumber, setPrNumber] = useState('1');
  const [diffTitle, setDiffTitle] = useState('Refactor authentication tokens');
  const [diffText, setDiffText] = useState(`diff --git a/src/AuthService.java b/src/AuthService.java
--- a/src/AuthService.java
+++ b/src/AuthService.java
@@ -25,4 +25,5 @@ public class AuthService {
     public String generateToken(User user) {
-        return "token_" + user.getId();
+        String rawToken = UUID.randomUUID().toString();
+        return encryptor.encrypt(rawToken);
     }
 }`);
  const [isLoading, setIsLoading] = useState(false);
  const [review, setReview] = useState<PrReviewResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [severityFilter, setSeverityFilter] = useState<string>('ALL');

  const handleReview = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeRepo) return;

    setIsLoading(true);
    setError(null);

    try {
      if (mode === 'github') {
        const prNum = parseInt(prNumber, 10);
        if (isNaN(prNum)) throw new Error('Please enter a valid PR number');
        const res = await reviewGitHubPr(activeRepo.id, prNum);
        setReview(res);
      } else {
        if (!diffText.trim()) throw new Error('Diff cannot be empty');
        const res = await reviewDiff(activeRepo.id, {
          title: diffTitle,
          description: 'Submitted for automated code quality review',
          diffText: diffText.trim(),
        });
        setReview(res);
      }
    } catch (err: any) {
      setError(err.message || 'Review execution failed');
    } finally {
      setIsLoading(false);
    }
  };

  const filteredComments = review?.comments.filter((c) =>
    severityFilter === 'ALL' ? true : c.severity === severityFilter
  ) || [];

  const getVerdictBadge = (verdict: PrReviewResponse['verdict']) => {
    switch (verdict) {
      case 'APPROVED':
        return (
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 text-sm font-semibold">
            <CheckCircle2 className="w-4 h-4" />
            Approved
          </div>
        );
      case 'COMMENT':
        return (
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-amber-500/10 text-amber-400 border border-amber-500/30 text-sm font-semibold">
            <MessageSquare className="w-4 h-4" />
            Comments Made
          </div>
        );
      case 'REQUEST_CHANGES':
        return (
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-rose-500/10 text-rose-400 border border-rose-500/30 text-sm font-semibold">
            <AlertOctagon className="w-4 h-4" />
            Changes Requested
          </div>
        );
    }
  };

  const getSeverityBadge = (severity: CommentSeverity) => {
    switch (severity) {
      case 'CRITICAL':
        return <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-rose-500/20 text-rose-400 border border-rose-500/30">CRITICAL</span>;
      case 'WARNING':
        return <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-amber-500/20 text-amber-400 border border-amber-500/30">WARNING</span>;
      case 'SUGGESTION':
        return <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-indigo-500/20 text-indigo-400 border border-indigo-500/30">SUGGESTION</span>;
      default:
        return <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-slate-700 text-slate-300">INFO</span>;
    }
  };

  if (!activeRepo) {
    return (
      <div className="glass-card rounded-xl p-12 text-center max-w-lg mx-auto mt-12">
        <FolderGit2 className="w-12 h-12 text-indigo-400 mx-auto mb-3" />
        <h2 className="text-lg font-bold text-white">Select a Repository</h2>
        <p className="text-sm text-slate-400 mt-1 mb-6">
          Connect or select a target repository to review pull requests or code diffs.
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
            <GitPullRequest className="w-7 h-7 text-indigo-400" />
            Pull Request Review & Code Quality Agent
          </h1>
          <p className="text-sm text-slate-400 mt-1">
            Autonomous static and security analysis checking PRs for bugs, vulnerabilities, and anti-patterns.
          </p>
        </div>

        {/* Tab switch */}
        <div className="flex p-1 rounded-lg bg-slate-900 border border-slate-800 self-start">
          <button
            onClick={() => setMode('diff')}
            className={`px-3 py-1.5 text-xs font-medium rounded-md transition-all ${
              mode === 'diff' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-400 hover:text-white'
            }`}
          >
            Raw Diff Review
          </button>
          <button
            onClick={() => setMode('github')}
            className={`px-3 py-1.5 text-xs font-medium rounded-md transition-all ${
              mode === 'github' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-400 hover:text-white'
            }`}
          >
            GitHub PR #
          </button>
        </div>
      </div>

      {/* Review Form */}
      <form onSubmit={handleReview} className="glass-card rounded-xl p-5 space-y-4">
        {mode === 'github' ? (
          <div>
            <label htmlFor="pr-number-input" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
              GitHub Pull Request Number
            </label>
            <input
              id="pr-number-input"
              type="number"
              min={1}
              required
              value={prNumber}
              onChange={(e) => setPrNumber(e.target.value)}
              placeholder="e.g. 42"
              className="w-full px-4 py-2.5 rounded-lg bg-slate-900 border border-slate-800 text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition-all font-mono"
            />
          </div>
        ) : (
          <>
            <div>
              <label htmlFor="diff-title-input" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                Change Title
              </label>
              <input
                id="diff-title-input"
                type="text"
                required
                value={diffTitle}
                onChange={(e) => setDiffTitle(e.target.value)}
                placeholder="e.g. Fix memory leak in buffer handler"
                className="w-full px-4 py-2 rounded-lg bg-slate-900 border border-slate-800 text-sm text-slate-200 focus:outline-none focus:border-indigo-500"
              />
            </div>
            <div>
              <label htmlFor="diff-text-input" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                Unified Git Diff
              </label>
              <textarea
                id="diff-text-input"
                rows={6}
                required
                value={diffText}
                onChange={(e) => setDiffText(e.target.value)}
                className="w-full px-4 py-2.5 rounded-lg bg-slate-900 border border-slate-800 text-xs font-mono text-slate-200 focus:outline-none focus:border-indigo-500 resize-y"
              />
            </div>
          </>
        )}

        <button
          id="run-pr-review-btn"
          type="submit"
          disabled={isLoading}
          className="w-full py-2.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white font-medium text-sm transition-all flex items-center justify-center gap-2 shadow-lg shadow-indigo-600/20 cursor-pointer"
        >
          {isLoading ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin text-white" />
              <span>Analyzing hunks, retrieving surrounding code, and assessing risk...</span>
            </>
          ) : (
            <>
              <GitPullRequest className="w-4 h-4" />
              <span>Analyze Code Quality & Security</span>
            </>
          )}
        </button>
      </form>

      {error && (
        <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-400 text-sm flex items-center gap-2.5">
          <AlertTriangle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Review Results */}
      {review && (
        <div className="space-y-5 animate-in fade-in duration-300">
          {/* Verdict and Risk Score Card */}
          <div className="glass-card rounded-xl p-6 flex flex-col md:flex-row md:items-center justify-between gap-6 border-slate-700">
            <div className="space-y-2">
              <div className="flex items-center gap-3">
                {getVerdictBadge(review.verdict)}
                <span className="text-xs text-slate-400">
                  Examined {review.examinedFilesCount} file{review.examinedFilesCount !== 1 ? 's' : ''}
                </span>
              </div>
              <p className="text-sm text-slate-300 leading-relaxed max-w-2xl">{review.summary}</p>
            </div>

            {/* Risk Meter */}
            <div className="p-4 rounded-xl bg-slate-900/80 border border-slate-800 min-w-[200px] text-center shrink-0">
              <div className="flex items-center justify-center gap-1.5 text-xs font-semibold text-slate-400 mb-1">
                <Flame className={`w-4 h-4 ${review.riskScore > 60 ? 'text-rose-400' : 'text-amber-400'}`} />
                Risk Score
              </div>
              <div className="text-3xl font-extrabold text-white">{review.riskScore}<span className="text-base font-normal text-slate-500">/100</span></div>
              <div className="w-full bg-slate-800 h-2 rounded-full overflow-hidden mt-2">
                <div
                  className={`h-full rounded-full transition-all ${
                    review.riskScore > 70
                      ? 'bg-rose-500'
                      : review.riskScore > 40
                      ? 'bg-amber-500'
                      : 'bg-emerald-500'
                  }`}
                  style={{ width: `${review.riskScore}%` }}
                />
              </div>
            </div>
          </div>

          {/* Filter Bar */}
          <div className="flex items-center justify-between border-b border-slate-800 pb-3">
            <h2 className="text-base font-bold text-white flex items-center gap-2">
              <Code2 className="w-5 h-5 text-indigo-400" />
              Inline Code Annotations ({filteredComments.length})
            </h2>

            <div className="flex gap-1.5">
              {['ALL', 'CRITICAL', 'WARNING', 'SUGGESTION'].map((filter) => (
                <button
                  key={filter}
                  onClick={() => setSeverityFilter(filter)}
                  className={`px-2.5 py-1 rounded text-xs font-medium transition-colors ${
                    severityFilter === filter
                      ? 'bg-indigo-600 text-white font-semibold'
                      : 'bg-slate-900 text-slate-400 hover:text-slate-200'
                  }`}
                >
                  {filter}
                </button>
              ))}
            </div>
          </div>

          {/* Comments List */}
          {filteredComments.length === 0 ? (
            <div className="glass-card rounded-xl p-8 text-center text-slate-400 text-xs">
              No review comments matching the selected filter.
            </div>
          ) : (
            <div className="space-y-3">
              {filteredComments.map((comment, idx) => (
                <div key={idx} className="glass-card rounded-xl p-4.5 border-l-4 border-l-indigo-500 space-y-2">
                  <div className="flex items-center justify-between gap-3">
                    <div className="flex items-center gap-2 font-mono text-xs text-indigo-300">
                      <FileCode className="w-3.5 h-3.5 text-slate-400" />
                      <span>{comment.filePath}:{comment.lineNumber}</span>
                    </div>

                    <div className="flex items-center gap-2">
                      <span className="px-2 py-0.5 rounded text-[10px] font-mono font-medium bg-slate-800 text-slate-400 border border-slate-700">
                        {comment.category}
                      </span>
                      {getSeverityBadge(comment.severity)}
                    </div>
                  </div>

                  <p className="text-xs text-slate-200 leading-relaxed font-sans">{comment.comment}</p>

                  {comment.suggestedCode && (
                    <div className="mt-2 p-3 rounded-lg bg-slate-950 font-mono text-xs text-emerald-300 border border-emerald-950">
                      <div className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider mb-1">
                        Suggested Replacement
                      </div>
                      <pre className="overflow-x-auto">{comment.suggestedCode}</pre>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

import { useState } from 'react';
import { useRepo } from '@/lib/repoContext';
import { investigateBug } from '@/api/client';
import type { BugReportResponse } from '@/api/types';
import {
  Bug,
  AlertTriangle,
  FileCode,
  CheckCircle2,
  ListOrdered,
  Wrench,
  Loader2,
  FolderGit2,
  Sparkles,
} from 'lucide-react';

export function BugInvestigatorPage() {
  const { activeRepo } = useRepo();
  const [description, setDescription] = useState('');
  const [rawErrorLog, setRawErrorLog] = useState('');
  const [failingTest, setFailingTest] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [report, setReport] = useState<BugReportResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleInvestigate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeRepo || !description.trim()) return;

    setIsLoading(true);
    setError(null);
    try {
      const response = await investigateBug(activeRepo.id, {
        description: description.trim(),
        rawErrorLog: rawErrorLog.trim() || undefined,
        failingTestSnippet: failingTest.trim() || undefined,
      });
      setReport(response);
    } catch (err: any) {
      setError(err.message || 'Investigation failed');
    } finally {
      setIsLoading(false);
    }
  };

  const loadSampleBug = () => {
    setDescription('NullPointerException during user profile lookup when OAuth token has expired');
    setRawErrorLog(`java.lang.NullPointerException: Cannot invoke "com.devpilot.entities.User.getId()" because "user" is null
    at com.devpilot.services.UserService.getProfile(UserService.java:42)
    at com.devpilot.controllers.UserController.getMe(UserController.java:19)`);
  };

  if (!activeRepo) {
    return (
      <div className="glass-card rounded-xl p-12 text-center max-w-lg mx-auto mt-12">
        <FolderGit2 className="w-12 h-12 text-indigo-400 mx-auto mb-3" />
        <h2 className="text-lg font-bold text-white">Select a Repository</h2>
        <p className="text-sm text-slate-400 mt-1 mb-6">
          Connect or select an active repository to run autonomous bug investigation against.
        </p>
        <a
          href="/repos"
          className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-indigo-600 text-white font-medium text-sm"
        >
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
            <Bug className="w-7 h-7 text-rose-400" />
            Autonomous Bug Investigator
          </h1>
          <p className="text-sm text-slate-400 mt-1">
            Diagnoses stack traces, error logs, and repository context to uncover root causes and suggest code fixes.
          </p>
        </div>

        <button
          onClick={loadSampleBug}
          className="text-xs px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 transition-colors self-start"
        >
          Load Sample Bug Trace
        </button>
      </div>

      {/* Input Form */}
      <form onSubmit={handleInvestigate} className="glass-card rounded-xl p-5 space-y-4">
        <div>
          <label htmlFor="bug-description" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
            Bug Description <span className="text-rose-400">*</span>
          </label>
          <input
            id="bug-description"
            type="text"
            required
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="e.g. NullPointerException when querying user session after token refresh..."
            className="w-full px-4 py-2.5 rounded-lg bg-slate-900 border border-slate-800 text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition-all"
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label htmlFor="raw-error-log" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
              Raw Error Log / Stack Trace
            </label>
            <textarea
              id="raw-error-log"
              rows={4}
              value={rawErrorLog}
              onChange={(e) => setRawErrorLog(e.target.value)}
              placeholder="Paste Java, Python, or TS error stack trace..."
              className="w-full px-4 py-2.5 rounded-lg bg-slate-900 border border-slate-800 text-xs font-mono text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition-all resize-y"
            />
          </div>

          <div>
            <label htmlFor="failing-test-snippet" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
              Failing Test Snippet (Optional)
            </label>
            <textarea
              id="failing-test-snippet"
              rows={4}
              value={failingTest}
              onChange={(e) => setFailingTest(e.target.value)}
              placeholder="Paste failing test method snippet..."
              className="w-full px-4 py-2.5 rounded-lg bg-slate-900 border border-slate-800 text-xs font-mono text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition-all resize-y"
            />
          </div>
        </div>

        <button
          id="investigate-bug-btn"
          type="submit"
          disabled={isLoading || !description.trim()}
          className="w-full py-2.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white font-medium text-sm transition-all flex items-center justify-center gap-2 shadow-lg shadow-indigo-600/20 cursor-pointer"
        >
          {isLoading ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin text-white" />
              <span>Analyzing stack frames and tracing dependencies...</span>
            </>
          ) : (
            <>
              <Sparkles className="w-4 h-4" />
              <span>Diagnose Bug with Grounded Intelligence</span>
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

      {/* Diagnostic Report View */}
      {report && (
        <div className="space-y-5 animate-in fade-in duration-300">
          {/* Root Cause Banner */}
          <div className="glass-card rounded-xl p-6 border-l-4 border-l-rose-500">
            <div className="flex items-center gap-2 text-rose-400 text-xs font-semibold uppercase tracking-wider mb-1.5">
              <AlertTriangle className="w-4 h-4" />
              Root Cause Diagnosis
            </div>
            <h2 className="text-lg font-bold text-white mb-2">{report.summary}</h2>
            <p className="text-sm text-slate-300 leading-relaxed">{report.rootCause}</p>

            {report.suspectedFiles.length > 0 && (
              <div className="mt-4 pt-4 border-t border-slate-800 flex flex-wrap gap-2 items-center">
                <span className="text-xs text-slate-400">Suspected Files:</span>
                {report.suspectedFiles.map((file, i) => (
                  <span
                    key={i}
                    className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-slate-800 text-xs font-mono text-indigo-300 border border-slate-700"
                  >
                    <FileCode className="w-3 h-3 text-indigo-400" />
                    {file}
                  </span>
                ))}
              </div>
            )}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
            {/* Reproduction Steps */}
            <div className="glass-card rounded-xl p-5">
              <h3 className="text-sm font-semibold text-white flex items-center gap-2 mb-3">
                <ListOrdered className="w-4 h-4 text-indigo-400" />
                Reproduction Steps
              </h3>
              <ol className="space-y-2 text-xs text-slate-300 list-decimal list-inside leading-relaxed">
                {report.reproductionSteps.map((step, idx) => (
                  <li key={idx} className="pl-1">
                    <span className="text-slate-200">{step}</span>
                  </li>
                ))}
              </ol>
            </div>

            {/* Suggested Fixes */}
            <div className="glass-card rounded-xl p-5">
              <h3 className="text-sm font-semibold text-white flex items-center gap-2 mb-3">
                <Wrench className="w-4 h-4 text-emerald-400" />
                Suggested Fixes
              </h3>
              <ul className="space-y-2 text-xs text-slate-300 leading-relaxed">
                {report.suggestedFixes.map((fix, idx) => (
                  <li key={idx} className="flex items-start gap-2">
                    <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0 mt-0.5" />
                    <span>{fix}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

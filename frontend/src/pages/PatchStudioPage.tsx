import { useState } from 'react';
import { useRepo } from '@/lib/repoContext';
import { generatePatch, validatePatch, dryRunPatch } from '@/api/client';
import type { GeneratedPatchResponse, PatchValidationResult, DryRunPatchResponse } from '@/api/types';
import {
  FileDiff,
  ShieldCheck,
  PlayCircle,
  Sparkles,
  AlertTriangle,
  CheckCircle2,
  XCircle,
  Copy,
  Check,
  FolderGit2,
  Loader2,
  Code2,
} from 'lucide-react';

export function PatchStudioPage() {
  const { activeRepo } = useRepo();
  const [tab, setTab] = useState<'generate' | 'validate' | 'dry-run'>('generate');

  // Generate State
  const [instruction, setInstruction] = useState('');
  const [targetFile, setTargetFile] = useState('');
  const [errorContext, setErrorContext] = useState('');
  const [isGenerating, setIsGenerating] = useState(false);
  const [generatedPatch, setGeneratedPatch] = useState<GeneratedPatchResponse | null>(null);
  const [copiedDiff, setCopiedDiff] = useState(false);

  // Validate State
  const [validateDiffText, setValidateDiffText] = useState(`diff --git a/Calculator.java b/Calculator.java
--- a/Calculator.java
+++ b/Calculator.java
@@ -1,5 +1,5 @@
 public class Calculator {
     public int add(int a, int b) {
-        return a - b;
+        return a + b;
     }
 }`);
  const [isValidating, setIsValidating] = useState(false);
  const [validationResult, setValidationResult] = useState<PatchValidationResult | null>(null);

  // Dry-Run State
  const [dryRunOriginal, setDryRunOriginal] = useState(`public class Calculator {
    public int add(int a, int b) {
        return a - b;
    }
}`);
  const [dryRunDiff, setDryRunDiff] = useState(`diff --git a/Calculator.java b/Calculator.java
--- a/Calculator.java
+++ b/Calculator.java
@@ -1,5 +1,5 @@
 public class Calculator {
     public int add(int a, int b) {
-        return a - b;
+        return a + b;
     }
 }`);
  const [isDryRunning, setIsDryRunning] = useState(false);
  const [dryRunResult, setDryRunResult] = useState<DryRunPatchResponse | null>(null);

  const [error, setError] = useState<string | null>(null);

  const handleGenerate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeRepo || !instruction.trim()) return;

    setIsGenerating(true);
    setError(null);
    try {
      const res = await generatePatch(activeRepo.id, {
        instruction: instruction.trim(),
        targetFile: targetFile.trim() || undefined,
        errorContext: errorContext.trim() || undefined,
      });
      setGeneratedPatch(res);
    } catch (err: any) {
      setError(err.message || 'Patch generation failed');
    } finally {
      setIsGenerating(false);
    }
  };

  const handleValidate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeRepo || !validateDiffText.trim()) return;

    setIsValidating(true);
    setError(null);
    try {
      const res = await validatePatch(activeRepo.id, {
        diff: validateDiffText.trim(),
      });
      setValidationResult(res);
    } catch (err: any) {
      setError(err.message || 'Validation failed');
    } finally {
      setIsValidating(false);
    }
  };

  const handleDryRun = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeRepo || !dryRunDiff.trim() || !dryRunOriginal.trim()) return;

    setIsDryRunning(true);
    setError(null);
    try {
      const res = await dryRunPatch(activeRepo.id, {
        diff: dryRunDiff.trim(),
        originalContent: dryRunOriginal,
      });
      setDryRunResult(res);
    } catch (err: any) {
      setError(err.message || 'Dry-run failed');
    } finally {
      setIsDryRunning(false);
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedDiff(true);
    setTimeout(() => setCopiedDiff(false), 2000);
  };

  if (!activeRepo) {
    return (
      <div className="glass-card rounded-xl p-12 text-center max-w-lg mx-auto mt-12">
        <FolderGit2 className="w-12 h-12 text-indigo-400 mx-auto mb-3" />
        <h2 className="text-lg font-bold text-white">Select a Repository</h2>
        <p className="text-sm text-slate-400 mt-1 mb-6">
          Connect or select an active repository to generate and test code patches.
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
            <FileDiff className="w-7 h-7 text-indigo-400" />
            Safe Patch Studio
          </h1>
          <p className="text-sm text-slate-400 mt-1">
            Generate minimal Git unified diffs, execute AST safety validations, and simulate dry-run applications in-memory.
          </p>
        </div>

        {/* Tab switch */}
        <div className="flex p-1 rounded-lg bg-slate-900 border border-slate-800 self-start">
          <button
            onClick={() => setTab('generate')}
            className={`px-3 py-1.5 text-xs font-medium rounded-md transition-all flex items-center gap-1.5 ${
              tab === 'generate' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-400 hover:text-white'
            }`}
          >
            <Sparkles className="w-3.5 h-3.5" />
            Generate
          </button>
          <button
            onClick={() => setTab('validate')}
            className={`px-3 py-1.5 text-xs font-medium rounded-md transition-all flex items-center gap-1.5 ${
              tab === 'validate' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-400 hover:text-white'
            }`}
          >
            <ShieldCheck className="w-3.5 h-3.5" />
            Validate
          </button>
          <button
            onClick={() => setTab('dry-run')}
            className={`px-3 py-1.5 text-xs font-medium rounded-md transition-all flex items-center gap-1.5 ${
              tab === 'dry-run' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-400 hover:text-white'
            }`}
          >
            <PlayCircle className="w-3.5 h-3.5" />
            Dry-Run
          </button>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-400 text-sm flex items-center gap-2.5">
          <AlertTriangle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Tab 1: Generate */}
      {tab === 'generate' && (
        <div className="space-y-6">
          <form onSubmit={handleGenerate} className="glass-card rounded-xl p-5 space-y-4">
            <div>
              <label htmlFor="patch-instruction" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                Patch Instruction <span className="text-rose-400">*</span>
              </label>
              <input
                id="patch-instruction"
                type="text"
                required
                value={instruction}
                onChange={(e) => setInstruction(e.target.value)}
                placeholder="e.g. Add validation for expired OAuth tokens in AuthService..."
                className="w-full px-4 py-2.5 rounded-lg bg-slate-900 border border-slate-800 text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500"
              />
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label htmlFor="target-file" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                  Target File (Optional)
                </label>
                <input
                  id="target-file"
                  type="text"
                  value={targetFile}
                  onChange={(e) => setTargetFile(e.target.value)}
                  placeholder="e.g. src/services/AuthService.java"
                  className="w-full px-4 py-2 rounded-lg bg-slate-900 border border-slate-800 text-sm text-slate-200 font-mono focus:outline-none focus:border-indigo-500"
                />
              </div>

              <div>
                <label htmlFor="error-context" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                  Error Context (Optional)
                </label>
                <input
                  id="error-context"
                  type="text"
                  value={errorContext}
                  onChange={(e) => setErrorContext(e.target.value)}
                  placeholder="e.g. java.lang.NullPointerException at line 42"
                  className="w-full px-4 py-2 rounded-lg bg-slate-900 border border-slate-800 text-sm text-slate-200 font-mono focus:outline-none focus:border-indigo-500"
                />
              </div>
            </div>

            <button
              id="generate-patch-btn"
              type="submit"
              disabled={isGenerating || !instruction.trim()}
              className="w-full py-2.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white font-medium text-sm transition-all flex items-center justify-center gap-2 shadow-lg shadow-indigo-600/20 cursor-pointer"
            >
              {isGenerating ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin text-white" />
                  <span>Retrieving vector context and constructing safe diff...</span>
                </>
              ) : (
                <>
                  <Sparkles className="w-4 h-4" />
                  <span>Generate AI Patch</span>
                </>
              )}
            </button>
          </form>

          {/* Generated Patch Output */}
          {generatedPatch && (
            <div className="space-y-4 animate-in fade-in duration-300">
              {/* Validation Status Banner */}
              <div
                className={`p-4 rounded-xl border flex items-center justify-between gap-4 ${
                  generatedPatch.validation.valid
                    ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-400'
                    : 'bg-rose-500/10 border-rose-500/20 text-rose-400'
                }`}
              >
                <div className="flex items-center gap-2.5 text-sm font-semibold">
                  {generatedPatch.validation.valid ? (
                    <CheckCircle2 className="w-5 h-5 shrink-0" />
                  ) : (
                    <XCircle className="w-5 h-5 shrink-0" />
                  )}
                  <span>
                    {generatedPatch.validation.valid
                      ? 'Patch Passed Security & AST Safety Checks'
                      : 'Patch Safety Violations Detected'}
                  </span>
                </div>
                <div className="text-xs font-mono text-slate-300">
                  +{generatedPatch.validation.linesAdded} / -{generatedPatch.validation.linesDeleted} lines
                </div>
              </div>

              {/* Explanation */}
              <div className="glass-card rounded-xl p-5">
                <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">
                  Explanation & Verification
                </h3>
                <p className="text-sm text-slate-200 leading-relaxed">{generatedPatch.explanation}</p>
              </div>

              {/* Unified Diff Block */}
              <div className="glass-card rounded-xl overflow-hidden border border-slate-800">
                <div className="px-4 py-2.5 bg-slate-900 border-b border-slate-800 flex items-center justify-between">
                  <div className="flex items-center gap-2 text-xs font-mono text-slate-300">
                    <Code2 className="w-4 h-4 text-indigo-400" />
                    <span>Unified Git Diff</span>
                  </div>
                  <button
                    onClick={() => copyToClipboard(generatedPatch.unifiedDiff)}
                    className="flex items-center gap-1.5 px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-xs text-slate-300 font-medium transition-colors"
                  >
                    {copiedDiff ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                    {copiedDiff ? 'Copied' : 'Copy Diff'}
                  </button>
                </div>
                <pre className="p-4 text-xs font-mono text-slate-200 bg-slate-950/70 overflow-x-auto leading-relaxed">
                  {generatedPatch.unifiedDiff}
                </pre>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Tab 2: Validate */}
      {tab === 'validate' && (
        <div className="space-y-6">
          <form onSubmit={handleValidate} className="glass-card rounded-xl p-5 space-y-4">
            <div>
              <label htmlFor="validate-diff-input" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                Paste Unified Git Diff to Verify
              </label>
              <textarea
                id="validate-diff-input"
                rows={8}
                required
                value={validateDiffText}
                onChange={(e) => setValidateDiffText(e.target.value)}
                className="w-full px-4 py-2.5 rounded-lg bg-slate-900 border border-slate-800 text-xs font-mono text-slate-200 focus:outline-none focus:border-indigo-500 resize-y"
              />
            </div>

            <button
              id="validate-diff-btn"
              type="submit"
              disabled={isValidating}
              className="w-full py-2.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white font-medium text-sm transition-all flex items-center justify-center gap-2 shadow-lg shadow-indigo-600/20 cursor-pointer"
            >
              {isValidating ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin text-white" />
                  <span>Checking path bounds and malicious code patterns...</span>
                </>
              ) : (
                <>
                  <ShieldCheck className="w-4 h-4" />
                  <span>Run Safety & Syntactic Audit</span>
                </>
              )}
            </button>
          </form>

          {validationResult && (
            <div className="glass-card rounded-xl p-5 space-y-4 animate-in fade-in duration-300">
              <div className="flex items-center gap-2 text-sm font-bold">
                {validationResult.valid ? (
                  <span className="text-emerald-400 flex items-center gap-1.5">
                    <CheckCircle2 className="w-5 h-5" />
                    Passed All Security Rules
                  </span>
                ) : (
                  <span className="text-rose-400 flex items-center gap-1.5">
                    <XCircle className="w-5 h-5" />
                    Security Violations Found
                  </span>
                )}
              </div>

              <div className="grid grid-cols-3 gap-3 text-center">
                <div className="p-3 rounded-lg bg-slate-900 border border-slate-800">
                  <div className="text-lg font-bold text-white">{validationResult.filesChanged}</div>
                  <div className="text-[11px] text-slate-400 uppercase">Files Changed</div>
                </div>
                <div className="p-3 rounded-lg bg-slate-900 border border-slate-800">
                  <div className="text-lg font-bold text-emerald-400">+{validationResult.linesAdded}</div>
                  <div className="text-[11px] text-slate-400 uppercase">Lines Added</div>
                </div>
                <div className="p-3 rounded-lg bg-slate-900 border border-slate-800">
                  <div className="text-lg font-bold text-rose-400">-{validationResult.linesDeleted}</div>
                  <div className="text-[11px] text-slate-400 uppercase">Lines Deleted</div>
                </div>
              </div>

              {validationResult.errors.length > 0 && (
                <div className="p-4 rounded-lg bg-rose-500/10 border border-rose-500/20 space-y-1.5">
                  <div className="text-xs font-semibold text-rose-400 uppercase">Violations:</div>
                  <ul className="text-xs text-rose-300 list-disc list-inside space-y-1">
                    {validationResult.errors.map((err, i) => (
                      <li key={i}>{err}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* Tab 3: Dry-Run */}
      {tab === 'dry-run' && (
        <div className="space-y-6">
          <form onSubmit={handleDryRun} className="glass-card rounded-xl p-5 space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label htmlFor="dry-run-orig-input" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                  Original Source Content
                </label>
                <textarea
                  id="dry-run-orig-input"
                  rows={8}
                  required
                  value={dryRunOriginal}
                  onChange={(e) => setDryRunOriginal(e.target.value)}
                  className="w-full px-4 py-2.5 rounded-lg bg-slate-900 border border-slate-800 text-xs font-mono text-slate-200 focus:outline-none focus:border-indigo-500 resize-y"
                />
              </div>

              <div>
                <label htmlFor="dry-run-diff-input" className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-1.5">
                  Git Unified Diff
                </label>
                <textarea
                  id="dry-run-diff-input"
                  rows={8}
                  required
                  value={dryRunDiff}
                  onChange={(e) => setDryRunDiff(e.target.value)}
                  className="w-full px-4 py-2.5 rounded-lg bg-slate-900 border border-slate-800 text-xs font-mono text-slate-200 focus:outline-none focus:border-indigo-500 resize-y"
                />
              </div>
            </div>

            <button
              id="dry-run-btn"
              type="submit"
              disabled={isDryRunning}
              className="w-full py-2.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white font-medium text-sm transition-all flex items-center justify-center gap-2 shadow-lg shadow-indigo-600/20 cursor-pointer"
            >
              {isDryRunning ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin text-white" />
                  <span>Applying hunks in memory and checking alignment...</span>
                </>
              ) : (
                <>
                  <PlayCircle className="w-4 h-4" />
                  <span>Simulate In-Memory Patch Application</span>
                </>
              )}
            </button>
          </form>

          {dryRunResult && (
            <div className="glass-card rounded-xl p-5 space-y-4 animate-in fade-in duration-300">
              <div className="flex items-center gap-2 text-sm font-bold">
                {dryRunResult.success ? (
                  <span className="text-emerald-400 flex items-center gap-1.5">
                    <CheckCircle2 className="w-5 h-5" />
                    Patch Applied Cleanly with 0 Conflicts
                  </span>
                ) : (
                  <span className="text-rose-400 flex items-center gap-1.5">
                    <XCircle className="w-5 h-5" />
                    Application Failed Due to Line Mismatch
                  </span>
                )}
              </div>

              {dryRunResult.success && dryRunResult.patchedContent && (
                <div className="space-y-2">
                  <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                    Simulated Patched File Preview
                  </div>
                  <pre className="p-4 rounded-lg bg-slate-950 font-mono text-xs text-slate-200 overflow-x-auto leading-relaxed border border-slate-800">
                    {dryRunResult.patchedContent}
                  </pre>
                </div>
              )}

              {dryRunResult.errors.length > 0 && (
                <div className="p-4 rounded-lg bg-rose-500/10 border border-rose-500/20 space-y-1.5">
                  <div className="text-xs font-semibold text-rose-400 uppercase">Conflicts:</div>
                  <ul className="text-xs text-rose-300 list-disc list-inside space-y-1">
                    {dryRunResult.errors.map((err, i) => (
                      <li key={i}>{err}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

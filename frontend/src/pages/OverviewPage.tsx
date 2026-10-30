import { HealthStatus } from '@/components/HealthStatus';
import { useRepo } from '@/lib/repoContext';
import { Link } from 'react-router-dom';
import {
  FolderGit2,
  MessageSquareCode,
  Bug,
  GitPullRequest,
  Sparkles,
  Activity,
  ArrowRight,
  Database,
  Shield,
} from 'lucide-react';

const features = [
  {
    title: 'Connected Repositories',
    path: '/repos',
    icon: FolderGit2,
    badge: 'pgvector HNSW',
    color: 'text-indigo-400',
    desc: 'Sync GitHub repositories and index code files into pgvector embeddings with line-level chunk mapping.',
  },
  {
    title: 'Codebase RAG Chat',
    path: '/chat',
    icon: MessageSquareCode,
    badge: 'SSE Streaming',
    color: 'text-cyan-400',
    desc: 'Ask complex architectural questions with live vector retrieval, grounded prompt context, and citation chips.',
  },
  {
    title: 'Bug Investigator Agent',
    path: '/bugs',
    icon: Bug,
    badge: 'Autonomous',
    color: 'text-rose-400',
    desc: 'Analyzes stack traces, parses method frames, filters framework noise, and prescribes root-cause fixes.',
  },
  {
    title: 'PR Review & Quality Agent',
    path: '/pr-review',
    icon: GitPullRequest,
    badge: 'Unified Diff',
    color: 'text-amber-400',
    desc: 'Audits GitHub pull requests or raw diffs, categorizing comments by severity and security vulnerability.',
  },
  {
    title: 'Safe Patch Studio',
    path: '/patches',
    icon: Sparkles,
    badge: 'AST Validator',
    color: 'text-emerald-400',
    desc: 'Generates minimal Git diffs, enforces strict path and safety validation, and dry-runs patches in-memory.',
  },
  {
    title: 'Observability & Evaluation',
    path: '/evaluation',
    icon: Activity,
    badge: 'Micrometer',
    color: 'text-violet-400',
    desc: 'Benchmarks retrieval latencies, scores context relevance, and audits responses for hallucinated symbols.',
  },
];

export function OverviewPage() {
  const { activeRepo, repositories } = useRepo();

  return (
    <div className="max-w-6xl mx-auto space-y-8">
      {/* Hero Welcome Banner */}
      <div className="glass-card rounded-2xl p-8 relative overflow-hidden border-indigo-500/20 bg-gradient-to-br from-indigo-950/40 via-slate-900/60 to-slate-950/80">
        <div className="relative z-10 max-w-2xl space-y-3">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-semibold bg-indigo-500/10 text-indigo-300 border border-indigo-500/20">
            <Shield className="w-3.5 h-3.5" />
            <span>DevPilot System Status: All 12 Backend Modules Operational</span>
          </div>

          <h1 className="text-3xl sm:text-4xl font-extrabold tracking-tight text-white">
            AI-Powered Codebase Intelligence & Autonomous Pair Programming
          </h1>

          <p className="text-sm text-slate-300 leading-relaxed">
            DevPilot combines local pgvector similarity search, line-aware code chunking, grounded LLM reasoning,
            and safety-constrained patch validation into a unified developer assistant.
          </p>

          {activeRepo ? (
            <div className="pt-2 flex items-center gap-3 text-xs text-slate-300">
              <span className="text-slate-400">Current Target:</span>
              <span className="font-semibold text-white px-2.5 py-1 rounded bg-slate-800 border border-slate-700">
                {activeRepo.fullName}
              </span>
              <span className="text-indigo-400 font-mono">({activeRepo.chunkCount} vector chunks)</span>
            </div>
          ) : (
            <div className="pt-2">
              <Link
                to="/repos"
                className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold transition-all shadow-md shadow-indigo-600/20"
              >
                Connect a Repository
                <ArrowRight className="w-4 h-4" />
              </Link>
            </div>
          )}
        </div>

        {/* Decorative background glow */}
        <div className="absolute right-0 top-0 bottom-0 w-96 bg-gradient-to-l from-indigo-600/10 to-transparent pointer-events-none" />
      </div>

      {/* Feature Navigation Cards */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-white flex items-center gap-2">
            <Database className="w-5 h-5 text-indigo-400" />
            Intelligence Modules
          </h2>
          <span className="text-xs text-slate-400">{repositories.length} Repositories Connected</span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {features.map((feat) => {
            const Icon = feat.icon;
            return (
              <Link
                key={feat.title}
                to={feat.path}
                className="glass-card glass-card-hover rounded-xl p-5 flex flex-col justify-between group transition-all"
              >
                <div className="space-y-2.5">
                  <div className="flex items-center justify-between">
                    <div className="w-9 h-9 rounded-lg bg-slate-800/80 border border-slate-700/60 flex items-center justify-center">
                      <Icon className={`w-5 h-5 ${feat.color}`} />
                    </div>
                    <span className="px-2 py-0.5 rounded text-[10px] font-mono font-medium bg-slate-800 text-slate-300 border border-slate-700">
                      {feat.badge}
                    </span>
                  </div>

                  <h3 className="font-semibold text-white text-base group-hover:text-indigo-300 transition-colors">
                    {feat.title}
                  </h3>

                  <p className="text-xs text-slate-400 leading-relaxed">{feat.desc}</p>
                </div>

                <div className="mt-4 pt-3 border-t border-slate-800/60 flex items-center justify-between text-xs font-medium text-slate-400 group-hover:text-indigo-400 transition-colors">
                  <span>Open Module</span>
                  <ArrowRight className="w-3.5 h-3.5 transform group-hover:translate-x-1 transition-transform" />
                </div>
              </Link>
            );
          })}
        </div>
      </div>

      {/* Real-time Health Check Widget */}
      <div className="pt-2">
        <HealthStatus />
      </div>
    </div>
  );
}

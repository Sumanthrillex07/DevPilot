import { HealthStatus } from '@/components/HealthStatus';
import { 
  Database, 
  KeyRound, 
  FolderGit2, 
  FileCode2, 
  BrainCircuit, 
  Sparkles,
  ShieldCheck,
  Cpu
} from 'lucide-react';

const milestones = [
  {
    part: 'Part 1',
    title: 'Scaffolding & Health',
    status: 'ACTIVE',
    icon: Cpu,
    desc: 'Spring Boot 3 + Vite React scaffold, Health endpoint, Docker Compose.',
  },
  {
    part: 'Part 2',
    title: 'PostgreSQL & Flyway',
    status: 'NEXT',
    icon: Database,
    desc: 'Database migrations, schema versioning, JPA entities, Testcontainers.',
  },
  {
    part: 'Part 3',
    title: 'GitHub OAuth & Security',
    status: 'PLANNED',
    icon: KeyRound,
    desc: 'OAuth2 login, encrypted token storage, security filter chain, session cookies.',
  },
  {
    part: 'Part 4',
    title: 'Repository Sync',
    status: 'PLANNED',
    icon: FolderGit2,
    desc: 'GitHub API client, paginated repo sync, ownership isolation.',
  },
  {
    part: 'Part 5 & 6',
    title: 'Chunking & Vector Index',
    status: 'PLANNED',
    icon: FileCode2,
    desc: 'Smart file filtering, line-accurate chunking, pgvector embeddings.',
  },
  {
    part: 'Part 7',
    title: 'RAG Chat & Streaming',
    status: 'PLANNED',
    icon: BrainCircuit,
    desc: 'Vector similarity retrieval, context builder, SSE token streaming, citations.',
  },
];

export function OverviewPage() {
  return (
    <div className="max-w-6xl mx-auto space-y-8">
      {/* Hero section */}
      <div className="relative overflow-hidden rounded-3xl p-8 glass-card border border-white/5 bg-gradient-to-r from-surface-100 via-surface-50 to-[#0e121d]">
        <div className="relative z-10 max-w-2xl space-y-3">
          <div className="inline-flex items-center space-x-2 px-3 py-1 rounded-full bg-indigo-500/10 border border-indigo-500/20 text-indigo-300 text-xs font-medium">
            <Sparkles className="w-3.5 h-3.5 text-indigo-400" />
            <span>DevPilot System Online</span>
          </div>
          <h2 className="text-3xl font-bold tracking-tight text-white">
            AI-Powered Codebase Intelligence
          </h2>
          <p className="text-sm text-slate-300 leading-relaxed">
            DevPilot indexes your GitHub repositories using pgvector and Spring AI, providing context-grounded RAG chat, automated PR reviews, and AI bug investigation.
          </p>
        </div>

        <div className="absolute right-0 top-0 bottom-0 w-1/3 bg-gradient-to-l from-indigo-500/10 to-transparent pointer-events-none" />
      </div>

      {/* Backend Health Check Component */}
      <div>
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400">
            System Connectivity Status
          </h3>
          <span className="text-xs text-slate-500 font-mono">Live Heartbeat</span>
        </div>
        <HealthStatus />
      </div>

      {/* Architecture Roadmap */}
      <div>
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="text-base font-semibold text-slate-200">Engineering Roadmap</h3>
            <p className="text-xs text-slate-400">Step-by-step verified implementation plan</p>
          </div>
          <div className="flex items-center space-x-2 text-xs text-slate-400">
            <ShieldCheck className="w-4 h-4 text-emerald-400" />
            <span>Automated Tests With Each Stage</span>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {milestones.map((item) => {
            const Icon = item.icon;
            const isActive = item.status === 'ACTIVE';
            const isNext = item.status === 'NEXT';

            return (
              <div
                key={item.part}
                className={`p-5 rounded-2xl glass-card glass-card-hover border relative transition-all ${
                  isActive
                    ? 'border-indigo-500/40 bg-surface-100/70 shadow-glow-primary'
                    : isNext
                    ? 'border-cyan-500/30 bg-surface-50/40'
                    : 'border-white/5 opacity-80'
                }`}
              >
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center space-x-2">
                    <span className="text-xs font-mono font-medium text-slate-400">{item.part}</span>
                    {isActive ? (
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-500/15 text-emerald-300 border border-emerald-500/30 flex items-center space-x-1">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                        <span>Ready</span>
                      </span>
                    ) : isNext ? (
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-cyan-500/15 text-cyan-300 border border-cyan-500/30">
                        Up Next
                      </span>
                    ) : (
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-slate-800 text-slate-500">
                        Planned
                      </span>
                    )}
                  </div>
                  <div className={`p-2 rounded-xl ${isActive ? 'bg-indigo-500/20 text-indigo-300' : 'bg-surface-100 text-slate-400'}`}>
                    <Icon className="w-4 h-4" />
                  </div>
                </div>

                <h4 className="font-semibold text-slate-200 text-sm">{item.title}</h4>
                <p className="text-xs text-slate-400 mt-1.5 leading-relaxed">{item.desc}</p>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

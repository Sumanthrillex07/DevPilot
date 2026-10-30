import { NavLink } from 'react-router-dom';
import {
  Bot,
  FolderGit2,
  MessageSquareCode,
  Bug,
  GitPullRequest,
  Sparkles,
  Activity,
  Layers,
} from 'lucide-react';

const navigation = [
  { name: 'Overview', href: '/', icon: Layers, badge: 'Home' },
  { name: 'Repositories', href: '/repos', icon: FolderGit2, badge: 'pgvector' },
  { name: 'Codebase Chat', href: '/chat', icon: MessageSquareCode, badge: 'RAG' },
  { name: 'Bug Investigator', href: '/bugs', icon: Bug, badge: 'Agent' },
  { name: 'PR Reviewer', href: '/pr-review', icon: GitPullRequest, badge: 'Quality' },
  { name: 'Patch Studio', href: '/patches', icon: Sparkles, badge: 'Sandbox' },
  { name: 'Evaluation', href: '/evaluation', icon: Activity, badge: 'Metrics' },
];

export function Sidebar() {
  return (
    <aside className="w-64 border-r border-white/5 bg-[#0e121d]/80 backdrop-blur-xl flex flex-col justify-between h-screen shrink-0 select-none">
      <div>
        {/* Brand Header */}
        <div className="h-16 flex items-center px-6 border-b border-white/5 space-x-3">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-indigo-500 to-cyan-500 p-0.5 shadow-glow-primary">
            <div className="w-full h-full bg-[#0e121d] rounded-[10px] flex items-center justify-center">
              <Bot className="w-5 h-5 text-indigo-400" />
            </div>
          </div>
          <div>
            <h1 className="font-bold text-sm tracking-tight text-white flex items-center space-x-1.5">
              <span>DevPilot</span>
              <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-indigo-500/20 text-indigo-300 font-normal">
                AI
              </span>
            </h1>
            <p className="text-[11px] text-slate-400">Codebase Intelligence</p>
          </div>
        </div>

        {/* Navigation list */}
        <nav className="p-3 space-y-1">
          <div className="px-3 py-2 text-[11px] font-semibold uppercase tracking-wider text-slate-500">
            Workspaces
          </div>
          {navigation.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.name}
                to={item.href}
                className={({ isActive }) =>
                  `group flex items-center justify-between px-3 py-2.5 rounded-xl text-xs font-medium transition-all ${
                    isActive
                      ? 'bg-indigo-600/15 text-indigo-300 border border-indigo-500/30 shadow-sm'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-white/[0.03]'
                  }`
                }
              >
                <div className="flex items-center space-x-3">
                  <Icon className="w-4 h-4 transition-transform group-hover:scale-110" />
                  <span>{item.name}</span>
                </div>
                {item.badge && (
                  <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-white/[0.04] text-slate-400 border border-white/5">
                    {item.badge}
                  </span>
                )}
              </NavLink>
            );
          })}
        </nav>
      </div>

      {/* Footer Profile / Status */}
      <div className="p-3 border-t border-white/5 space-y-2">
        <div className="px-3 py-2 rounded-xl bg-surface-50/50 border border-white/5 flex items-center justify-between">
          <div className="flex items-center space-x-2.5">
            <div className="w-2 h-2 rounded-full bg-emerald-400 shadow-glow-emerald" />
            <span className="text-[11px] text-slate-300 font-mono">DevPilot Engine</span>
          </div>
          <span className="text-[10px] font-mono text-emerald-400 font-semibold">Online</span>
        </div>
      </div>
    </aside>
  );
}

import { ExternalLink, Terminal, FolderGit2 } from 'lucide-react';
import { useRepo } from '@/lib/repoContext';

export function Navbar() {
  const { activeRepo, repositories, setActiveRepo } = useRepo();

  return (
    <header className="h-16 border-b border-white/5 bg-[#0a0d14]/70 backdrop-blur-md px-8 flex items-center justify-between shrink-0">
      <div className="flex items-center space-x-4">
        <div className="flex items-center space-x-2 text-xs text-slate-400">
          <Terminal className="w-4 h-4 text-indigo-400" />
          <span>DevPilot AI</span>
          <span className="text-slate-600">/</span>
          {activeRepo ? (
            <div className="flex items-center gap-1.5 text-white font-medium">
              <FolderGit2 className="w-3.5 h-3.5 text-indigo-400" />
              <select
                id="navbar-repo-selector"
                value={activeRepo.id}
                onChange={(e) => {
                  const selected = repositories.find((r) => r.id === e.target.value);
                  if (selected) setActiveRepo(selected);
                }}
                className="bg-transparent border-none text-xs font-semibold text-slate-200 focus:outline-none cursor-pointer"
              >
                {repositories.map((r) => (
                  <option key={r.id} value={r.id} className="bg-slate-900 text-white">
                    {r.fullName}
                  </option>
                ))}
              </select>
            </div>
          ) : (
            <span className="text-slate-400 font-medium">No Repository Selected</span>
          )}
        </div>
      </div>

      <div className="flex items-center space-x-3">
        <a
          href="http://localhost:8081/actuator/health"
          target="_blank"
          rel="noreferrer"
          className="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-surface-50 hover:bg-surface-100 border border-white/5 text-xs text-slate-300 hover:text-white transition-colors"
          title="Open Spring Boot Actuator"
        >
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
          <span>Actuator Health</span>
          <ExternalLink className="w-3 h-3 text-slate-400" />
        </a>

        <div className="h-4 w-px bg-white/10" />

        <a
          href="/repos"
          className="flex items-center space-x-2 px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-medium transition-all shadow-sm"
        >
          <FolderGit2 className="w-3.5 h-3.5" />
          <span>Manage Repos</span>
        </a>
      </div>
    </header>
  );
}

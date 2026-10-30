import { useState, useEffect } from 'react';
import { ExternalLink, Terminal, FolderGit2, User } from 'lucide-react';
import { useRepo } from '@/lib/repoContext';
import { fetchCurrentUser } from '@/api/client';
import type { UserProfile } from '@/api/types';

export function Navbar() {
  const { activeRepo, repositories, setActiveRepo } = useRepo();
  const [user, setUser] = useState<UserProfile | null>(null);

  useEffect(() => {
    fetchCurrentUser()
      .then(setUser)
      .catch(() => setUser(null));
  }, []);

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

        {user ? (
          <div className="flex items-center space-x-2 px-3 py-1.5 rounded-lg bg-slate-900 border border-slate-800 text-xs text-white">
            {user.avatarUrl ? (
              <img src={user.avatarUrl} alt={user.githubUsername} className="w-4 h-4 rounded-full" />
            ) : (
              <User className="w-3.5 h-3.5 text-indigo-400" />
            )}
            <span className="font-semibold">{user.githubUsername}</span>
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
          </div>
        ) : (
          <a
            id="connect-github-nav-btn"
            href="http://localhost:8081/oauth2/authorization/github"
            className="flex items-center space-x-2 px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-medium transition-all shadow-sm"
          >
            <svg className="w-3.5 h-3.5 fill-current" viewBox="0 0 24 24">
              <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z" />
            </svg>
            <span>Connect GitHub</span>
          </a>
        )}
      </div>
    </header>
  );
}

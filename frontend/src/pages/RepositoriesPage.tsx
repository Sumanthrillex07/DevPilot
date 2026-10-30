import { useState } from 'react';
import { useRepo } from '@/lib/repoContext';
import { indexRepository } from '@/api/client';
import type { ConnectedRepository } from '@/api/types';
import {
  FolderGit2,
  RefreshCw,
  Search,
  Lock,
  Globe,
  Database,
  CheckCircle2,
  AlertCircle,
  Loader2,
  ArrowRight,
  GitBranch,
} from 'lucide-react';

export function RepositoriesPage() {
  const { repositories, activeRepo, setActiveRepo, isLoading, refreshRepos } = useRepo();
  const [search, setSearch] = useState('');
  const [indexingMap, setIndexingMap] = useState<Record<string, boolean>>({});
  const [syncing, setSyncing] = useState(false);

  const filteredRepos = repositories.filter(
    (r) =>
      r.fullName.toLowerCase().includes(search.toLowerCase()) ||
      (r.language && r.language.toLowerCase().includes(search.toLowerCase()))
  );

  const handleSync = async () => {
    setSyncing(true);
    try {
      await refreshRepos();
    } finally {
      setSyncing(false);
    }
  };

  const handleIndex = async (repo: ConnectedRepository) => {
    setIndexingMap((prev) => ({ ...prev, [repo.id]: true }));
    try {
      await indexRepository(repo.id);
      await refreshRepos();
    } catch (err) {
      console.error('Failed to trigger indexing:', err);
    } finally {
      setIndexingMap((prev) => ({ ...prev, [repo.id]: false }));
    }
  };

  const getStatusBadge = (status: ConnectedRepository['indexStatus']) => {
    switch (status) {
      case 'INDEXED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
            <CheckCircle2 className="w-3.5 h-3.5" />
            Indexed
          </span>
        );
      case 'IN_PROGRESS':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 animate-pulse">
            <Loader2 className="w-3.5 h-3.5 animate-spin" />
            Indexing...
          </span>
        );
      case 'FAILED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-rose-500/10 text-rose-400 border border-rose-500/20">
            <AlertCircle className="w-3.5 h-3.5" />
            Failed
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-amber-500/10 text-amber-400 border border-amber-500/20">
            Pending
          </span>
        );
    }
  };

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2.5">
            <FolderGit2 className="w-7 h-7 text-indigo-400" />
            Connected Repositories
          </h1>
          <p className="text-sm text-slate-400 mt-1">
            Synchronize, explore, and index GitHub codebases into DevPilot's pgvector store.
          </p>
        </div>

        <button
          id="sync-repos-btn"
          onClick={handleSync}
          disabled={syncing || isLoading}
          className="inline-flex items-center justify-center gap-2 px-4 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white font-medium text-sm transition-all shadow-lg shadow-indigo-600/20 cursor-pointer"
        >
          <RefreshCw className={`w-4 h-4 ${syncing ? 'animate-spin' : ''}`} />
          {syncing ? 'Syncing...' : 'Sync with GitHub'}
        </button>
      </div>

      {/* Search Bar */}
      <div className="relative">
        <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
        <input
          id="repo-search-input"
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Filter repositories by name or language..."
          className="w-full pl-10 pr-4 py-2.5 rounded-lg bg-slate-900/60 border border-slate-800 text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition-all"
        />
      </div>

      {/* Repositories Grid */}
      {isLoading ? (
        <div className="flex flex-col items-center justify-center py-20 text-slate-400">
          <Loader2 className="w-8 h-8 animate-spin text-indigo-400 mb-3" />
          <p className="text-sm">Loading repositories...</p>
        </div>
      ) : filteredRepos.length === 0 ? (
        <div className="glass-card rounded-xl p-12 text-center border border-dashed border-slate-800">
          <FolderGit2 className="w-12 h-12 text-slate-600 mx-auto mb-3" />
          <h3 className="text-base font-semibold text-white">No repositories found</h3>
          <p className="text-sm text-slate-400 max-w-sm mx-auto mt-1 mb-4">
            {search ? 'No repositories matched your filter query.' : 'Sync with your GitHub account to connect repositories.'}
          </p>
          <button
            onClick={handleSync}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-sm font-medium text-slate-200"
          >
            <RefreshCw className="w-4 h-4" />
            Refresh Repositories
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {filteredRepos.map((repo) => {
            const isActive = activeRepo?.id === repo.id;
            const isIndexing = indexingMap[repo.id] || repo.indexStatus === 'IN_PROGRESS';

            return (
              <div
                key={repo.id}
                id={`repo-card-${repo.id}`}
                className={`glass-card rounded-xl p-5 flex flex-col justify-between transition-all duration-200 ${
                  isActive
                    ? 'border-indigo-500/60 ring-1 ring-indigo-500/40 bg-indigo-950/20'
                    : 'hover:border-slate-700 hover:bg-slate-900/50'
                }`}
              >
                <div>
                  <div className="flex items-start justify-between gap-3 mb-2.5">
                    <div className="flex items-center gap-2">
                      {repo.isPrivate ? (
                        <span title="Private Repository">
                          <Lock className="w-4 h-4 text-amber-400 shrink-0" />
                        </span>
                      ) : (
                        <span title="Public Repository">
                          <Globe className="w-4 h-4 text-slate-400 shrink-0" />
                        </span>
                      )}
                      <h2 className="font-semibold text-white truncate text-base" title={repo.fullName}>
                        {repo.name}
                      </h2>
                    </div>
                    {getStatusBadge(repo.indexStatus)}
                  </div>

                  <p className="text-xs text-slate-400 line-clamp-2 min-h-[32px] mb-4">
                    {repo.description || 'No description provided.'}
                  </p>

                  <div className="flex items-center gap-4 text-xs text-slate-400 mb-4 pb-4 border-b border-slate-800">
                    <div className="flex items-center gap-1.5">
                      <GitBranch className="w-3.5 h-3.5 text-slate-500" />
                      <span>{repo.defaultBranch}</span>
                    </div>
                    {repo.language && (
                      <span className="px-2 py-0.5 rounded bg-slate-800 text-slate-300 font-mono text-[11px]">
                        {repo.language}
                      </span>
                    )}
                    <div className="flex items-center gap-1.5 ml-auto text-slate-500">
                      <Database className="w-3.5 h-3.5" />
                      <span>{repo.chunkCount} chunks</span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-2 pt-2">
                  <button
                    id={`select-repo-${repo.id}`}
                    onClick={() => setActiveRepo(repo)}
                    className={`flex-1 py-1.5 px-3 rounded-lg text-xs font-medium transition-colors flex items-center justify-center gap-1.5 ${
                      isActive
                        ? 'bg-indigo-600 text-white font-semibold'
                        : 'bg-slate-800 hover:bg-slate-700 text-slate-300'
                    }`}
                  >
                    {isActive ? 'Active Target' : 'Select Repo'}
                    {isActive && <ArrowRight className="w-3.5 h-3.5" />}
                  </button>

                  <button
                    id={`index-repo-${repo.id}`}
                    onClick={() => handleIndex(repo)}
                    disabled={isIndexing}
                    title="Index code chunks into pgvector"
                    className="py-1.5 px-3 rounded-lg text-xs font-medium bg-slate-800/80 hover:bg-slate-700 text-slate-300 border border-slate-700 disabled:opacity-50 transition-colors flex items-center gap-1.5"
                  >
                    {isIndexing ? (
                      <>
                        <Loader2 className="w-3.5 h-3.5 animate-spin text-indigo-400" />
                        Indexing
                      </>
                    ) : (
                      <>
                        <Database className="w-3.5 h-3.5 text-indigo-400" />
                        Re-Index
                      </>
                    )}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

import React, { createContext, useContext, useState, useEffect } from 'react';
import type { ConnectedRepository } from '@/api/types';
import { fetchRepositories } from '@/api/client';

interface RepoContextType {
  repositories: ConnectedRepository[];
  activeRepo: ConnectedRepository | null;
  setActiveRepo: (repo: ConnectedRepository | null) => void;
  isLoading: boolean;
  refreshRepos: () => Promise<void>;
}

const RepoContext = createContext<RepoContextType | undefined>(undefined);

export function RepoProvider({ children }: { children: React.ReactNode }) {
  const [repositories, setRepositories] = useState<ConnectedRepository[]>([]);
  const [activeRepo, setActiveRepo] = useState<ConnectedRepository | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const loadRepos = async (forceRefresh: boolean = false) => {
    setIsLoading(true);
    try {
      const data = await fetchRepositories(forceRefresh);
      setRepositories(data);

      const savedId = localStorage.getItem('devpilot_active_repo_id');
      if (savedId) {
        const found = data.find((r) => r.id === savedId);
        if (found) {
          setActiveRepo(found);
          return;
        }
      }

      if (data.length > 0 && !activeRepo) {
        setActiveRepo(data[0]);
        localStorage.setItem('devpilot_active_repo_id', data[0].id);
      }
    } catch {
      // In offline / unauthenticated mode, keep empty state gracefully
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadRepos(false);
  }, []);

  const handleSetActiveRepo = (repo: ConnectedRepository | null) => {
    setActiveRepo(repo);
    if (repo) {
      localStorage.setItem('devpilot_active_repo_id', repo.id);
    } else {
      localStorage.removeItem('devpilot_active_repo_id');
    }
  };

  return (
    <RepoContext.Provider
      value={{
        repositories,
        activeRepo,
        setActiveRepo: handleSetActiveRepo,
        isLoading,
        refreshRepos: () => loadRepos(true),
      }}
    >
      {children}
    </RepoContext.Provider>
  );
}

export function useRepo() {
  const context = useContext(RepoContext);
  if (!context) {
    throw new Error('useRepo must be used within a RepoProvider');
  }
  return context;
}

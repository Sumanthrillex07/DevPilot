import { useQuery } from '@tanstack/react-query';
import { fetchHealthStatus } from '@/api/client';
import { Activity, CheckCircle2, XCircle, RefreshCw, Server, Clock, GitBranch } from 'lucide-react';

export function HealthStatus() {
  const { data, error, isLoading, isFetching, refetch } = useQuery({
    queryKey: ['health'],
    queryFn: fetchHealthStatus,
    refetchInterval: 30000,
  });

  const isHealthy = data?.status === 'UP';

  return (
    <div id="health-card" className="glass-card rounded-2xl p-6 relative overflow-hidden transition-all duration-300">
      {/* Background ambient glow */}
      <div 
        className={`absolute -right-16 -top-16 w-48 h-48 rounded-full blur-3xl opacity-20 pointer-events-none transition-colors duration-500 ${
          isLoading ? 'bg-amber-400' : isHealthy ? 'bg-emerald-400' : 'bg-rose-400'
        }`} 
      />

      {/* Header */}
      <div className="flex items-center justify-between pb-5 border-b border-white/5">
        <div className="flex items-center space-x-3">
          <div className="w-10 h-10 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400">
            <Server className="w-5 h-5" />
          </div>
          <div>
            <h3 className="font-semibold text-slate-100 flex items-center space-x-2">
              <span>Backend Core Service</span>
              <span className="text-xs px-2 py-0.5 rounded-full bg-slate-800 text-slate-400 border border-white/5">
                v1
              </span>
            </h3>
            <p className="text-xs text-slate-400 font-mono mt-0.5">/api/v1/health</p>
          </div>
        </div>

        <button
          id="health-refresh-btn"
          onClick={() => refetch()}
          disabled={isFetching}
          className="flex items-center space-x-1.5 px-3 py-1.5 rounded-lg bg-surface-100 hover:bg-surface-200 border border-white/5 text-xs text-slate-300 hover:text-white transition-all disabled:opacity-50"
          title="Refresh health check"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${isFetching ? 'animate-spin text-indigo-400' : ''}`} />
          <span>{isFetching ? 'Checking...' : 'Ping'}</span>
        </button>
      </div>

      {/* Status details */}
      <div className="mt-5 grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* Status indicator */}
        <div className="p-4 rounded-xl bg-surface-50/50 border border-white/5 flex items-center space-x-3">
          {isLoading ? (
            <div className="w-9 h-9 rounded-lg bg-amber-500/10 flex items-center justify-center text-amber-400">
              <Activity className="w-5 h-5 animate-pulse" />
            </div>
          ) : isHealthy ? (
            <div className="w-9 h-9 rounded-lg bg-emerald-500/10 flex items-center justify-center text-emerald-400">
              <CheckCircle2 className="w-5 h-5" />
            </div>
          ) : (
            <div className="w-9 h-9 rounded-lg bg-rose-500/10 flex items-center justify-center text-rose-400">
              <XCircle className="w-5 h-5" />
            </div>
          )}
          <div>
            <div className="text-[11px] text-slate-400 uppercase tracking-wider font-medium">Status</div>
            <div id="health-status-badge" className="font-semibold text-sm flex items-center space-x-1.5 mt-0.5">
              <span
                className={`w-2 h-2 rounded-full ${
                  isLoading
                    ? 'bg-amber-400 animate-ping'
                    : isHealthy
                    ? 'bg-emerald-400 shadow-glow-emerald'
                    : 'bg-rose-400'
                }`}
              />
              <span className={isLoading ? 'text-amber-400' : isHealthy ? 'text-emerald-400' : 'text-rose-400'}>
                {isLoading ? 'Connecting...' : isHealthy ? 'Online & Healthy' : 'Service Offline'}
              </span>
            </div>
          </div>
        </div>

        {/* Application Name */}
        <div className="p-4 rounded-xl bg-surface-50/50 border border-white/5 flex items-center space-x-3">
          <div className="w-9 h-9 rounded-lg bg-indigo-500/10 flex items-center justify-center text-indigo-400">
            <GitBranch className="w-5 h-5" />
          </div>
          <div>
            <div className="text-[11px] text-slate-400 uppercase tracking-wider font-medium">Service Name</div>
            <div id="health-app-name" className="font-medium text-sm text-slate-200 mt-0.5">
              {data?.app || (isLoading ? 'Detecting...' : 'devpilot-backend')}
            </div>
          </div>
        </div>

        {/* Timestamp / Heartbeat */}
        <div className="p-4 rounded-xl bg-surface-50/50 border border-white/5 flex items-center space-x-3">
          <div className="w-9 h-9 rounded-lg bg-cyan-500/10 flex items-center justify-center text-cyan-400">
            <Clock className="w-5 h-5" />
          </div>
          <div>
            <div className="text-[11px] text-slate-400 uppercase tracking-wider font-medium">Last Verified</div>
            <div id="health-timestamp" className="text-xs font-mono text-slate-300 mt-0.5 truncate max-w-[170px]">
              {data?.timestamp ? new Date(data.timestamp).toLocaleTimeString() : isLoading ? '...' : 'Unavailable'}
            </div>
          </div>
        </div>
      </div>

      {/* Error message banner if failing */}
      {error && (
        <div id="health-error-alert" className="mt-4 p-3 rounded-xl bg-rose-500/10 border border-rose-500/20 text-xs text-rose-300 flex items-start space-x-2">
          <XCircle className="w-4 h-4 shrink-0 mt-0.5 text-rose-400" />
          <div>
            <span className="font-semibold">Unable to connect to backend: </span>
            <span>{(error as Error).message}. Ensure backend is running at http://localhost:8081.</span>
          </div>
        </div>
      )}
    </div>
  );
}

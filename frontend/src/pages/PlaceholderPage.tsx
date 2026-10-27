import { Construction, ArrowLeft } from 'lucide-react';
import { Link } from 'react-router-dom';

interface PlaceholderPageProps {
  title: string;
  milestone: string;
  description: string;
}

export function PlaceholderPage({ title, milestone, description }: PlaceholderPageProps) {
  return (
    <div className="max-w-2xl mx-auto py-16 text-center space-y-4">
      <div className="w-14 h-14 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 mx-auto flex items-center justify-center">
        <Construction className="w-7 h-7" />
      </div>
      <div className="inline-block px-3 py-1 rounded-full bg-slate-800 text-slate-300 text-xs font-mono">
        Scheduled for {milestone}
      </div>
      <h2 className="text-2xl font-bold text-white">{title}</h2>
      <p className="text-sm text-slate-400 max-w-md mx-auto">{description}</p>

      <div className="pt-4">
        <Link
          to="/"
          className="inline-flex items-center space-x-2 px-4 py-2 rounded-xl bg-surface-100 hover:bg-surface-200 border border-white/5 text-xs text-slate-200 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Back to Overview</span>
        </Link>
      </div>
    </div>
  );
}

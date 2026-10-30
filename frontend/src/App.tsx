import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { RepoProvider } from '@/lib/repoContext';
import { AppLayout } from '@/components/layout/AppLayout';
import { OverviewPage } from '@/pages/OverviewPage';
import { RepositoriesPage } from '@/pages/RepositoriesPage';
import { ChatPage } from '@/pages/ChatPage';
import { BugInvestigatorPage } from '@/pages/BugInvestigatorPage';
import { PrReviewPage } from '@/pages/PrReviewPage';
import { PatchStudioPage } from '@/pages/PatchStudioPage';
import { EvaluationPage } from '@/pages/EvaluationPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 15,
      refetchOnWindowFocus: false,
    },
  },
});

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <RepoProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/" element={<AppLayout />}>
              <Route index element={<OverviewPage />} />
              <Route path="repos" element={<RepositoriesPage />} />
              <Route path="chat" element={<ChatPage />} />
              <Route path="bugs" element={<BugInvestigatorPage />} />
              <Route path="pr-review" element={<PrReviewPage />} />
              <Route path="patches" element={<PatchStudioPage />} />
              <Route path="evaluation" element={<EvaluationPage />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </RepoProvider>
    </QueryClientProvider>
  );
}

export default App;

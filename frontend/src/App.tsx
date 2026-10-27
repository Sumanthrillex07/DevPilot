import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AppLayout } from '@/components/layout/AppLayout';
import { OverviewPage } from '@/pages/OverviewPage';
import { PlaceholderPage } from '@/pages/PlaceholderPage';

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
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<AppLayout />}>
            <Route index element={<OverviewPage />} />
            <Route
              path="repos"
              element={
                <PlaceholderPage
                  title="Connected Repositories"
                  milestone="Part 4"
                  description="Connect, sync, and inspect your GitHub repositories for embedding indexing."
                />
              }
            />
            <Route
              path="chat"
              element={
                <PlaceholderPage
                  title="Codebase RAG Chat"
                  milestone="Part 7"
                  description="Ask questions about your codebase with live vector similarity retrieval and streaming responses."
                />
              }
            />
            <Route
              path="bugs"
              element={
                <PlaceholderPage
                  title="Bug Investigator Agent"
                  milestone="Part 9"
                  description="Autonomous agent diagnosing error traces, stack traces, and suggesting root-cause fixes."
                />
              }
            />
            <Route
              path="pr-review"
              element={
                <PlaceholderPage
                  title="Pull Request Reviewer"
                  milestone="Part 10"
                  description="Automated code reviews analyzing diffs for bugs, anti-patterns, and security concerns."
                />
              }
            />
            <Route
              path="patches"
              element={
                <PlaceholderPage
                  title="Patch Studio"
                  milestone="Part 11"
                  description="Safely generate, test, and apply patches in isolated sandbox containers."
                />
              }
            />
            <Route
              path="settings"
              element={
                <PlaceholderPage
                  title="System & Security Settings"
                  milestone="Part 3"
                  description="Configure GitHub OAuth, API keys, token encryption, and LLM providers."
                />
              }
            />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}

export default App;

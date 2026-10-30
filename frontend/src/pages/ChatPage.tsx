import { useState, useEffect, useRef } from 'react';
import { useRepo } from '@/lib/repoContext';
import { fetchChatSessions, createChatSession, fetchChatMessages } from '@/api/client';
import type { ChatSession, ChatMessage, CitationDto } from '@/api/types';
import {
  MessageSquare,
  Plus,
  Send,
  Bot,
  User,
  Sparkles,
  FileCode,
  Check,
  Copy,
  FolderGit2,
  Loader2,
} from 'lucide-react';

export function ChatPage() {
  const { activeRepo, repositories, setActiveRepo } = useRepo();
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [activeSession, setActiveSession] = useState<ChatSession | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [streamBuffer, setStreamBuffer] = useState('');
  const [copiedCitation, setCopiedCitation] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (activeRepo) {
      loadSessions(activeRepo.id);
    }
  }, [activeRepo?.id]);

  useEffect(() => {
    if (activeSession) {
      loadMessages(activeSession.id);
    } else {
      setMessages([]);
    }
  }, [activeSession?.id]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, streamBuffer]);

  const loadSessions = async (repoId: string) => {
    try {
      const data = await fetchChatSessions(repoId);
      setSessions(data);
      if (data.length > 0 && !activeSession) {
        setActiveSession(data[0]);
      }
    } catch {
      // Gracefully handle unauthenticated/empty
      setSessions([]);
    }
  };

  const loadMessages = async (sessionId: string) => {
    try {
      const data = await fetchChatMessages(sessionId);
      setMessages(data);
    } catch {
      setMessages([]);
    }
  };

  const handleNewSession = async () => {
    if (!activeRepo) return;
    try {
      const newSession = await createChatSession(activeRepo.id, `Chat ${sessions.length + 1}`);
      setSessions((prev) => [newSession, ...prev]);
      setActiveSession(newSession);
      setMessages([]);
    } catch (err) {
      console.error('Failed to create session:', err);
    }
  };

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || !activeRepo || isStreaming) return;

    let session = activeSession;
    if (!session) {
      try {
        session = await createChatSession(activeRepo.id, input.slice(0, 30));
        setSessions((prev) => [session!, ...prev]);
        setActiveSession(session);
      } catch {
        return;
      }
    }

    const userMessageText = input.trim();
    setInput('');

    const optimisticUserMsg: ChatMessage = {
      id: `user-${Date.now()}`,
      role: 'user',
      content: userMessageText,
      createdAt: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, optimisticUserMsg]);

    setIsStreaming(true);
    setStreamBuffer('');

    try {
      const streamUrl = `/api/v1/repos/${activeRepo.id}/chat/sessions/${session.id}/stream?message=${encodeURIComponent(userMessageText)}`;
      const eventSource = new EventSource(streamUrl, { withCredentials: true });

      let accumulated = '';
      const streamCitations: CitationDto[] = [];

      eventSource.addEventListener('token', (e: MessageEvent) => {
        try {
          const payload = JSON.parse(e.data);
          accumulated += payload.token || '';
          setStreamBuffer(accumulated);
        } catch {
          accumulated += e.data || '';
          setStreamBuffer(accumulated);
        }
      });

      eventSource.addEventListener('done', () => {
        eventSource.close();
        const assistantMsg: ChatMessage = {
          id: `asst-${Date.now()}`,
          role: 'assistant',
          content: accumulated,
          citations: streamCitations,
          createdAt: new Date().toISOString(),
        };
        setMessages((prev) => [...prev, assistantMsg]);
        setStreamBuffer('');
        setIsStreaming(false);
      });

      eventSource.onerror = () => {
        eventSource.close();
        if (accumulated) {
          const assistantMsg: ChatMessage = {
            id: `asst-${Date.now()}`,
            role: 'assistant',
            content: accumulated,
            citations: streamCitations,
            createdAt: new Date().toISOString(),
          };
          setMessages((prev) => [...prev, assistantMsg]);
        }
        setStreamBuffer('');
        setIsStreaming(false);
      };
    } catch {
      setIsStreaming(false);
    }
  };

  const copyCitation = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedCitation(text);
    setTimeout(() => setCopiedCitation(null), 2000);
  };

  if (!activeRepo) {
    return (
      <div className="glass-card rounded-xl p-12 text-center max-w-lg mx-auto mt-12">
        <FolderGit2 className="w-12 h-12 text-indigo-400 mx-auto mb-3" />
        <h2 className="text-lg font-bold text-white">Select a Repository First</h2>
        <p className="text-sm text-slate-400 mt-1 mb-6">
          To chat with grounded code intelligence, select or index a connected repository.
        </p>
        {repositories.length > 0 ? (
          <div className="flex flex-col gap-2">
            {repositories.map((r) => (
              <button
                key={r.id}
                onClick={() => setActiveRepo(r)}
                className="py-2 px-4 rounded-lg bg-slate-800 hover:bg-slate-700 text-sm text-slate-200 text-left transition-colors flex items-center justify-between"
              >
                <span className="font-medium truncate">{r.fullName}</span>
                <span className="text-xs text-indigo-400 font-mono">{r.chunkCount} chunks</span>
              </button>
            ))}
          </div>
        ) : (
          <a
            href="/repos"
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-indigo-600 text-white font-medium text-sm"
          >
            Go to Repositories
          </a>
        )}
      </div>
    );
  }

  return (
    <div className="h-[calc(100vh-8.5rem)] flex gap-4">
      {/* Session Drawer */}
      <div className="w-64 glass-card rounded-xl flex flex-col p-3 shrink-0 hidden md:flex">
        <button
          id="new-chat-btn"
          onClick={handleNewSession}
          className="w-full py-2 px-3 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold flex items-center justify-center gap-2 transition-colors mb-3 shadow-md shadow-indigo-600/20 cursor-pointer"
        >
          <Plus className="w-4 h-4" />
          New Chat Session
        </button>

        <div className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider px-2 mb-2">
          History
        </div>

        <div className="flex-1 overflow-y-auto space-y-1">
          {sessions.length === 0 ? (
            <div className="text-xs text-slate-500 text-center py-6">No previous chats</div>
          ) : (
            sessions.map((session) => (
              <button
                key={session.id}
                id={`session-${session.id}`}
                onClick={() => setActiveSession(session)}
                className={`w-full py-2 px-2.5 rounded-lg text-xs text-left truncate flex items-center gap-2 transition-colors ${
                  activeSession?.id === session.id
                    ? 'bg-slate-800 text-white font-medium'
                    : 'text-slate-400 hover:bg-slate-900/60 hover:text-slate-200'
                }`}
              >
                <MessageSquare className="w-3.5 h-3.5 shrink-0 text-slate-500" />
                <span className="truncate">{session.title}</span>
              </button>
            ))
          )}
        </div>
      </div>

      {/* Main Chat Area */}
      <div className="flex-1 glass-card rounded-xl flex flex-col overflow-hidden">
        {/* Chat Header */}
        <div className="px-5 py-3 border-b border-slate-800 flex items-center justify-between bg-slate-950/40">
          <div className="flex items-center gap-2.5">
            <Sparkles className="w-4 h-4 text-indigo-400" />
            <div>
              <h2 className="text-sm font-semibold text-white flex items-center gap-2">
                {activeRepo.fullName}
                <span className="text-[11px] font-normal px-2 py-0.5 rounded-full bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                  RAG Grounded
                </span>
              </h2>
            </div>
          </div>
          <div className="text-xs text-slate-400 hidden sm:block">
            {activeRepo.chunkCount} vector chunks indexed
          </div>
        </div>

        {/* Messages List */}
        <div className="flex-1 overflow-y-auto p-5 space-y-5">
          {messages.length === 0 && !isStreaming ? (
            <div className="h-full flex flex-col items-center justify-center text-center p-6 text-slate-400">
              <Bot className="w-12 h-12 text-indigo-400 mb-3" />
              <h3 className="text-base font-semibold text-white">Ask DevPilot Anything</h3>
              <p className="text-xs text-slate-400 max-w-md mt-1 mb-6">
                DevPilot queries the pgvector store to find exact code files and symbols, then streams grounded answers.
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 max-w-md w-full">
                {[
                  'Explain authentication & token flow',
                  'Where is the database schema defined?',
                  'How are background tasks processed?',
                  'Show all REST API controllers',
                ].map((suggestion) => (
                  <button
                    key={suggestion}
                    onClick={() => setInput(suggestion)}
                    className="p-2.5 rounded-lg bg-slate-900/60 hover:bg-slate-800 text-xs text-slate-300 border border-slate-800 text-left transition-colors"
                  >
                    {suggestion}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <>
              {messages.map((msg) => (
                <div
                  key={msg.id}
                  className={`flex gap-3.5 max-w-3xl ${
                    msg.role === 'user' ? 'ml-auto flex-row-reverse' : ''
                  }`}
                >
                  <div
                    className={`w-7 h-7 rounded-lg flex items-center justify-center shrink-0 ${
                      msg.role === 'user' ? 'bg-indigo-600 text-white' : 'bg-slate-800 text-indigo-400'
                    }`}
                  >
                    {msg.role === 'user' ? <User className="w-4 h-4" /> : <Bot className="w-4 h-4" />}
                  </div>

                  <div
                    className={`rounded-xl p-4 text-sm leading-relaxed ${
                      msg.role === 'user'
                        ? 'bg-indigo-600 text-white'
                        : 'bg-slate-900/80 text-slate-200 border border-slate-800'
                    }`}
                  >
                    <div className="whitespace-pre-wrap font-sans">{msg.content}</div>

                    {msg.citations && msg.citations.length > 0 && (
                      <div className="mt-3 pt-3 border-t border-slate-800/80 flex flex-wrap gap-1.5 items-center">
                        <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider mr-1">
                          Citations:
                        </span>
                        {msg.citations.map((c, i) => {
                          const label = `${c.filePath}${c.startLine ? `:${c.startLine}-${c.endLine}` : ''}`;
                          const isCopied = copiedCitation === label;
                          return (
                            <button
                              key={i}
                              onClick={() => copyCitation(label)}
                              title="Click to copy citation file:lines"
                              className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-slate-800/90 hover:bg-slate-700 text-[11px] font-mono text-indigo-300 border border-slate-700 transition-colors"
                            >
                              <FileCode className="w-3 h-3 text-indigo-400" />
                              <span>{label}</span>
                              {isCopied ? (
                                <Check className="w-3 h-3 text-emerald-400" />
                              ) : (
                                <Copy className="w-3 h-3 text-slate-500" />
                              )}
                            </button>
                          );
                        })}
                      </div>
                    )}
                  </div>
                </div>
              ))}

              {isStreaming && streamBuffer && (
                <div className="flex gap-3.5 max-w-3xl">
                  <div className="w-7 h-7 rounded-lg bg-slate-800 text-indigo-400 flex items-center justify-center shrink-0">
                    <Bot className="w-4 h-4" />
                  </div>
                  <div className="rounded-xl p-4 text-sm bg-slate-900/80 text-slate-200 border border-slate-800 leading-relaxed">
                    <div className="whitespace-pre-wrap font-sans">{streamBuffer}</div>
                    <div className="inline-block w-1.5 h-3.5 ml-1 bg-indigo-400 animate-pulse align-middle" />
                  </div>
                </div>
              )}

              {isStreaming && !streamBuffer && (
                <div className="flex gap-3.5 items-center text-xs text-slate-400">
                  <Loader2 className="w-4 h-4 animate-spin text-indigo-400" />
                  <span>Searching vector embeddings and reasoning...</span>
                </div>
              )}
            </>
          )}
          <div ref={messagesEndRef} />
        </div>

        {/* Input Bar */}
        <form onSubmit={handleSendMessage} className="p-3 bg-slate-950/60 border-t border-slate-800 flex gap-2">
          <input
            id="chat-message-input"
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            disabled={isStreaming}
            placeholder={`Ask about ${activeRepo.name} (e.g. how does indexing work?)...`}
            className="flex-1 px-4 py-2.5 rounded-lg bg-slate-900 border border-slate-800 text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition-all"
          />
          <button
            id="send-chat-btn"
            type="submit"
            disabled={!input.trim() || isStreaming}
            className="px-4 py-2.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40 text-white font-medium text-sm transition-all flex items-center gap-1.5 cursor-pointer shadow-md shadow-indigo-600/20"
          >
            <Send className="w-4 h-4" />
            <span className="hidden sm:inline">Send</span>
          </button>
        </form>
      </div>
    </div>
  );
}

"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  ArrowRight,
  Bot,
  Brain,
  Check,
  Compass,
  Copy,
  Database,
  FileText,
  FolderKanban,
  History,
  ListChecks,
  Loader2,
  MessageSquare,
  Plus,
  RotateCcw,
  Route,
  Search,
  Send,
  Sparkles,
  Target,
  Trash2,
  TriangleAlert,
  UserRound,
  Zap,
} from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Breadcrumb } from "@/components/breadcrumb";
import api from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { cn } from "cn";

const SUGGESTION_CARDS = [
  {
    query: "What skills am I missing for my target career?",
    title: "Am I ready?",
    desc: "See what's missing for your dream role",
    icon: Target,
  },
  {
    query: "What should I learn next?",
    title: "What to study next",
    desc: "Your most important next step",
    icon: Route,
  },
  {
    query: "Explain my assessment result.",
    title: "My test score",
    desc: "Understand your result in simple words",
    icon: ListChecks,
  },
  {
    query: "Which roadmap step should I focus on?",
    title: "Where to focus",
    desc: "The one thing to do this week",
    icon: Compass,
  },
  {
    query: "How can I improve my CV?",
    title: "Fix my CV",
    desc: "3 quick fixes to get shortlisted",
    icon: FileText,
  },
  {
    query: "What projects should I complete?",
    title: "Which projects?",
    desc: "Simple projects recruiters love",
    icon: FolderKanban,
  },
];

const FOLLOW_UPS = [
  "Give me a 30-day plan",
  "Explain my assessment result",
  "What should I learn next?",
];

/** User-friendly mapping of backend/transport failures. Never shows raw internals. */
function friendlyError(err) {
  if (!err) return "Could not get a reply. Please try again.";
  if (err.code === "ECONNABORTED" || /timeout/i.test(err.message || "")) {
    return "The AI took too long to respond. Please try again with a shorter question.";
  }
  if (!err.status) {
    return "Unable to reach the server. Check your connection and try again.";
  }
  if (err.status === 503 || err.errorCode === "AI_NOT_CONFIGURED") {
    return "The AI assistant is temporarily unavailable. Please try again in a moment.";
  }
  if (err.status === 429 || err.errorCode === "RATE_LIMITED") {
    return "You're sending messages too quickly. Please wait a moment and try again.";
  }
  if (err.status === 401) return "Your session expired. Please log in again.";
  if (err.status === 403) return "You don't have access to the AI assistant.";
  if (err.status === 404) return "This conversation no longer exists. Start a new one.";
  if (err.status === 502 || err.errorCode === "AI_ERROR") {
    return err.message || "AI service is temporarily unavailable. Please try again.";
  }
  return err.message || "Could not get a reply. Please try again.";
}

function ModeBadge({ offline, mode }) {
  const isOffline = offline || mode === "offline-smart";
  return (
    <Badge
      variant="secondary"
      className={cn(
        "gap-1 text-[10px] font-semibold",
        isOffline
          ? "bg-emerald-500/15 text-emerald-700 dark:text-emerald-400"
          : "bg-brand-600/10 text-brand-700 dark:text-brand-300",
      )}
      title={
        isOffline
          ? "Answered by built-in Smart Guidance from your verified CareerOS data — no API key needed"
          : "Answered by the connected language model using your CareerOS data"
      }
    >
      {isOffline ? <Database className="size-3" aria-hidden="true" /> : <Zap className="size-3" aria-hidden="true" />}
      {isOffline ? "Smart Guidance" : "LLM-enhanced"}
    </Badge>
  );
}

function MarkdownAnswer({ content }) {
  return (
    <div className="markdown-answer text-sm leading-relaxed">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          h1: ({ children }) => <p className="mt-3 mb-1 text-sm font-bold first:mt-0">{children}</p>,
          h2: ({ children }) => <p className="mt-3 mb-1 text-sm font-bold first:mt-0">{children}</p>,
          h3: ({ children }) => <p className="mt-3 mb-1 text-sm font-bold first:mt-0">{children}</p>,
          h4: ({ children }) => <p className="mt-2 mb-1 text-sm font-bold first:mt-0">{children}</p>,
          p: ({ children }) => <p className="my-1.5 first:mt-0 last:mb-0">{children}</p>,
          ul: ({ children }) => <ul className="my-1.5 list-disc space-y-1 pl-5">{children}</ul>,
          ol: ({ children }) => <ol className="my-1.5 list-decimal space-y-1 pl-5">{children}</ol>,
          li: ({ children }) => <li className="leading-relaxed">{children}</li>,
          strong: ({ children }) => <strong className="font-semibold">{children}</strong>,
          code: ({ children }) => (
            <code className="rounded-md bg-brand-600/10 px-1.5 py-0.5 font-mono text-[12px] text-brand-800 dark:text-brand-200">
              {children}
            </code>
          ),
          pre: ({ children }) => (
            <pre className="my-2 overflow-x-auto rounded-xl border border-border/60 bg-pine-950 p-3 font-mono text-[12px] text-emerald-50">
              {children}
            </pre>
          ),
          blockquote: ({ children }) => (
            <blockquote className="my-2 rounded-r-xl border-l-2 border-brand-500 bg-brand-500/5 py-1 pl-3 text-muted-foreground">
              {children}
            </blockquote>
          ),
          a: ({ href, children }) => (
            <a href={href} target="_blank" rel="noreferrer" className="font-medium text-brand-700 underline dark:text-brand-300">
              {children}
            </a>
          ),
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}

function TypingIndicator() {
  return (
    <div className="flex gap-3" role="status" aria-live="polite" aria-label="Assistant is thinking">
      <span className="relative flex size-8 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-brand-600 to-pine-800 text-white shadow-md shadow-brand-900/20">
        <Bot className="size-4" aria-hidden="true" />
        <span className="absolute inset-0 animate-ping rounded-full bg-brand-500 opacity-20" aria-hidden="true" />
      </span>
      <div className="rounded-2xl rounded-tl-md border border-primary/20 bg-gradient-to-br from-primary/[0.06] to-card px-4 py-3.5 shadow-sm">
        <span className="flex items-center gap-1.5">
          <span className="size-2 animate-bounce rounded-full bg-gradient-to-br from-brand-500 to-pine-700 [animation-delay:0ms]" />
          <span className="size-2 animate-bounce rounded-full bg-gradient-to-br from-brand-500 to-pine-700 [animation-delay:150ms]" />
          <span className="size-2 animate-bounce rounded-full bg-gradient-to-br from-brand-500 to-pine-700 [animation-delay:300ms]" />
          <span className="ml-1 text-xs font-medium text-muted-foreground">Thinking…</span>
        </span>
      </div>
    </div>
  );
}

function MessageBubble({ message, isLastAssistant, toolsNote, offline, mode, onCopy, copied, onFollowUp, sending }) {
  const isUser = message.role === "USER";
  return (
    <div className={cn("anim-hero-rise flex gap-3", isUser && "flex-row-reverse")}>
      <span
        className={cn(
          "flex size-8 shrink-0 items-center justify-center rounded-full shadow-sm ring-1",
          isUser
            ? "bg-gradient-to-br from-pine-800 to-brand-700 text-white ring-white/20"
            : "bg-gradient-to-br from-brand-600 to-pine-800 text-white shadow-brand-900/20 ring-brand-500/20",
        )}
        aria-hidden="true"
      >
        {isUser ? <UserRound className="size-4" /> : <Bot className="size-4" />}
      </span>
      <div className={cn("min-w-0 max-w-[88%] sm:max-w-[78%]", isUser && "text-right")}>
        <div
          className={cn(
            "px-4 py-3 text-sm leading-relaxed transition-shadow",
            isUser
              ? "rounded-2xl rounded-tr-md bg-gradient-to-br from-pine-800 via-brand-800 to-brand-600 break-words whitespace-pre-wrap text-white shadow-md shadow-brand-900/25"
              : "rounded-2xl rounded-tl-md border border-border/60 bg-card shadow-card hover:shadow-card-hover",
          )}
        >
          {isUser ? message.content : <MarkdownAnswer content={message.content} />}
        </div>
        <div className="mt-1 flex flex-wrap items-center gap-1.5 text-[11px] text-muted-foreground">
          <span>{formatDateTime(message.createdAt)}</span>
          {!isUser && toolsNote && (
            <span className="inline-flex items-center gap-1 rounded-full bg-brand-600/10 px-2 py-0.5 font-semibold text-brand-700 dark:text-brand-300">
              <Database className="size-3" aria-hidden="true" /> From your CareerOS data
            </span>
          )}
          {!isUser && (offline !== undefined || mode) && (
            <ModeBadge offline={offline} mode={mode} />
          )}
          {!isUser && (
            <button
              type="button"
              onClick={onCopy}
              aria-label={copied ? "Copied" : "Copy answer"}
              className="inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 font-medium transition hover:bg-muted hover:text-foreground"
            >
              {copied ? <Check className="size-3 text-emerald-600" aria-hidden="true" /> : <Copy className="size-3" aria-hidden="true" />}
              {copied ? "Copied" : "Copy"}
            </button>
          )}
        </div>
        {!isUser && isLastAssistant && !sending && (
          <div className="mt-2 flex flex-wrap gap-1.5">
            {FOLLOW_UPS.map((tip) => (
              <button
                key={tip}
                type="button"
                onClick={() => onFollowUp?.(tip)}
                className="rounded-full border border-dashed border-brand-500/40 bg-brand-500/5 px-2.5 py-1 text-[11px] font-medium text-brand-700 transition hover:border-brand-500 hover:bg-brand-500/10 dark:text-brand-300"
              >
                {tip} →
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

export default function AiAssistantPage() {
  const [sessions, setSessions] = useState([]);
  const [activeId, setActiveId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");
  const [loadingSessions, setLoadingSessions] = useState(true);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");
  const [lastFailed, setLastFailed] = useState(null);
  const [aiStatus, setAiStatus] = useState(null);
  const [lastMeta, setLastMeta] = useState({ toolsUsed: [], offline: undefined, mode: undefined });
  const [copiedId, setCopiedId] = useState(null);
  const [search, setSearch] = useState("");
  const scrollRef = useRef(null);
  const sendingRef = useRef(false);
  const activeIdRef = useRef(null);
  const textareaRef = useRef(null);

  useEffect(() => {
    activeIdRef.current = activeId;
  }, [activeId]);

  const loadSessions = useCallback(async (selectId) => {
    try {
      const data = await api.get("/ai/chat/sessions");
      const list = Array.isArray(data) ? data : [];
      setSessions(list);
      if (selectId) {
        setActiveId(selectId);
      } else if (list.length > 0) {
        setActiveId((current) => current ?? list[0].id);
      }
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setLoadingSessions(false);
    }
  }, []);

  const loadHistory = useCallback(async (sessionId, { silent = false } = {}) => {
    if (!sessionId) {
      setMessages([]);
      return;
    }
    if (!silent) setLoadingHistory(true);
    setError("");
    try {
      const data = await api.get(`/ai/chat/sessions/${sessionId}`);
      setMessages(Array.isArray(data) ? data : []);
      setLastMeta({ toolsUsed: [], offline: undefined, mode: undefined });
      if (!silent) setLastFailed(null);
    } catch (err) {
      if (!silent) setError(friendlyError(err));
    } finally {
      if (!silent) setLoadingHistory(false);
    }
  }, []);

  useEffect(() => {
    api
      .get("/ai/status")
      .then((data) => setAiStatus(data))
      .catch(() => setAiStatus(null));
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial data load; state updates happen in promise callbacks
    loadSessions();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- initial load only
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- history loader is async; state updates happen in promise callbacks
    loadHistory(activeId);
  }, [activeId, loadHistory]);

  useEffect(() => {
    const el = scrollRef.current;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  }, [messages, sending]);

  const filteredSessions = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return sessions;
    return sessions.filter((s) => (s.title || "").toLowerCase().includes(q));
  }, [sessions, search]);

  async function startNewConversation() {
    if (sendingRef.current) return;
    setError("");
    setLastFailed(null);
    try {
      const session = await api.post("/ai/chat/sessions", {});
      await loadSessions(session.id);
      setMessages([]);
      setLastMeta({ toolsUsed: [], offline: undefined, mode: undefined });
      textareaRef.current?.focus();
      toast.success("New conversation started");
    } catch (err) {
      setError(friendlyError(err));
    }
  }

  async function deleteSession(sessionId) {
    if (sendingRef.current) return;
    try {
      await api.delete(`/ai/chat/sessions/${sessionId}`);
      toast.success("Conversation deleted");
      const remaining = sessions.filter((session) => session.id !== sessionId);
      setSessions(remaining);
      if (activeIdRef.current === sessionId) {
        const nextId = remaining.length > 0 ? remaining[0].id : null;
        setActiveId(nextId);
        if (!nextId) setMessages([]);
      }
    } catch (err) {
      toast.error(friendlyError(err));
    }
  }

  async function copyMessage(id, content) {
    try {
      await navigator.clipboard.writeText(content);
      setCopiedId(id);
      setTimeout(() => setCopiedId((current) => (current === id ? null : current)), 1600);
    } catch {
      toast.error("Could not copy to clipboard.");
    }
  }

  async function sendMessage(text) {
    const content = (text ?? input).trim();
    // Duplicate-submit guard uses a ref (state may lag between rapid clicks).
    if (!content || sendingRef.current) return;
    if (content.length > 4000) {
      setError("Please keep messages under 4000 characters.");
      return;
    }
    sendingRef.current = true;
    setSending(true);
    setError("");
    setLastFailed(null);
    const pendingSessionId = activeIdRef.current;
    let optimisticId = null;
    try {
      let sessionId = activeIdRef.current;
      if (!sessionId) {
        const session = await api.post("/ai/chat/sessions", {});
        sessionId = session.id;
        await loadSessions(sessionId);
      }
      // Optimistic user bubble; replaced by the authoritative history below.
      optimisticId = `local-${Date.now()}`;
      setMessages((list) => [
        ...list,
        { id: optimisticId, role: "USER", content, createdAt: new Date().toISOString() },
      ]);
      setInput("");
      if (textareaRef.current) textareaRef.current.style.height = "auto";
      const reply = await api.post(
        `/ai/chat/sessions/${sessionId}/messages`,
        { message: content },
        { timeout: 150000 },
      );
      setLastMeta({
        toolsUsed: Array.isArray(reply?.toolsUsed) ? reply.toolsUsed : [],
        offline: reply?.offline,
        mode: reply?.mode,
      });
      await loadHistory(sessionId, { silent: true });
      await loadSessions(sessionId);
    } catch (err) {
      // Backend persists both turns only after a successful reply, so the
      // optimistic bubble is the ONLY local trace — drop it on failure to
      // avoid a phantom user message with no assistant answer.
      setMessages((list) => list.filter((m) => m.id !== optimisticId));
      setLastFailed(content);
      setError(friendlyError(err));
      if (!err?.status) {
        // Network blip — history re-sync keeps the UI honest.
      } else if (pendingSessionId) {
        // Re-sync with the server so the UI never shows a stuck state.
        loadHistory(pendingSessionId, { silent: true });
      }
    } finally {
      sendingRef.current = false;
      setSending(false);
      textareaRef.current?.focus();
    }
  }

  function retryLastFailed() {
    if (lastFailed) sendMessage(lastFailed);
  }

  function handleSubmit(e) {
    e.preventDefault();
    sendMessage();
  }

  function handleInputKeyDown(e) {
    // Enter sends; Shift+Enter inserts a newline (chat convention).
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  }

  function handleInputChange(e) {
    setInput(e.target.value);
    const el = e.target;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, 160)}px`;
  }

  const activeMessages = messages;
  const activeSession = sessions.find((s) => s.id === activeId) || null;
  const lastAssistantIndex = activeMessages.map((m) => m.role).lastIndexOf("ASSISTANT");
  const showOfflineBanner =
    aiStatus && !aiStatus.configured && aiStatus.offlineFallback !== false;
  const showStrictBanner = aiStatus && !aiStatus.configured && aiStatus.offlineFallback === false;
  const toolsNote = lastMeta.toolsUsed.length > 0;
  const isSmartGuidance = aiStatus?.mode === "offline-smart" || (aiStatus && !aiStatus.configured);

  return (
    <div className="space-y-5 pb-4">
      <Breadcrumb items={[{ label: "AI Assistant" }]} />

      {/* ── Hero header ── */}
      <div className="relative overflow-hidden rounded-3xl border border-primary/25 bg-gradient-to-br from-primary/[0.12] via-card to-card shadow-card">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-0"
          style={{
            backgroundImage: "radial-gradient(var(--color-primary) 1px, transparent 1px)",
            backgroundSize: "22px 22px",
            WebkitMaskImage: "linear-gradient(115deg, black 0%, transparent 55%)",
            maskImage: "linear-gradient(115deg, black 0%, transparent 55%)",
            opacity: 0.12,
          }}
        />
        <Bot aria-hidden="true" className="pointer-events-none absolute -right-5 -bottom-7 size-36 rotate-[-10deg] text-primary/[0.08]" />
        <div className="relative flex flex-col gap-3 p-5 sm:flex-row sm:items-center sm:justify-between sm:p-6">
          <div className="flex min-w-0 items-center gap-3.5">
            <span className="relative flex size-12 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-600 to-pine-800 text-white shadow-md shadow-brand-900/25">
              <Sparkles className="size-5" aria-hidden="true" />
              <span className="absolute -right-0.5 -bottom-0.5 size-3 rounded-full border-2 border-card bg-emerald-500" aria-hidden="true" />
            </span>
            <div className="min-w-0">
              <h1 className="text-xl font-extrabold tracking-tight sm:text-2xl">AI Career Assistant</h1>
              <p className="mt-0.5 max-w-2xl truncate text-sm text-muted-foreground sm:whitespace-normal">
                Ask anything — I answer using your own profile, CV and progress.
              </p>
            </div>
          </div>
          <div className="flex shrink-0 flex-wrap items-center gap-2">
            {isSmartGuidance ? (
              <Badge variant="secondary" className="gap-1 bg-emerald-500/15 text-emerald-700 dark:text-emerald-400">
                <Database className="size-3" aria-hidden="true" /> Ready to use
              </Badge>
            ) : null}
            <Button size="sm" onClick={startNewConversation} className="btn-polish shadow-md shadow-brand-600/25">
              <Plus className="size-4" aria-hidden="true" />
              New chat
            </Button>
          </div>
        </div>
      </div>

      {showOfflineBanner && (
        <div className="flex items-center gap-3 rounded-2xl border border-emerald-500/25 bg-emerald-500/5 px-4 py-2.5">
          <span className="flex size-7 shrink-0 items-center justify-center rounded-xl bg-emerald-500/15 text-emerald-700 dark:text-emerald-400">
            <Sparkles className="size-3.5" aria-hidden="true" />
          </span>
          <p className="text-xs leading-relaxed text-muted-foreground">
            <strong className="font-bold text-emerald-800 dark:text-emerald-300">Ready — no setup needed.</strong>{" "}
            I answer from your saved data: profile, tests, roadmap, CV and LinkedIn.
          </p>
        </div>
      )}

      {showStrictBanner && (
        <Alert variant="destructive" className="rounded-2xl">
          <TriangleAlert className="size-4" aria-hidden="true" />
          <AlertTitle>AI provider not configured</AlertTitle>
          <AlertDescription>
            Chat history works, but replies need a language model. Backend environment required:
            <span className="mt-1 block font-mono text-xs">
              AI_API_KEY=sk-... (required) · AI_PROVIDER=openai|gemini · AI_MODEL=gpt-4o-mini ·
              AI_BASE_URL=https://api.openai.com/v1 · GEMINI_API_KEY=AIza-... (for gemini)
            </span>
          </AlertDescription>
        </Alert>
      )}

      {error && (
        <Alert variant="destructive" className="rounded-2xl shadow-card">
          <TriangleAlert className="size-4" aria-hidden="true" />
          <AlertDescription className="flex flex-wrap items-center gap-2">
            <span className="min-w-0 flex-1">{error}</span>
            {lastFailed && !sending && (
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={retryLastFailed}
                className="btn-polish shrink-0"
              >
                <RotateCcw className="size-3.5" aria-hidden="true" />
                Retry
              </Button>
            )}
          </AlertDescription>
        </Alert>
      )}

      <div className="grid items-start gap-4 lg:grid-cols-[300px_1fr]">
        {/* ── Conversations sidebar (sticky below the 64px navbar while the chat scrolls) ── */}
        <Card className="overflow-hidden shadow-card lg:sticky lg:top-20">
          <CardContent className="p-0">
            <div className="flex items-center gap-2 border-b border-border/60 bg-muted/40 px-4 py-3">
              <span className="flex size-8 items-center justify-center rounded-lg bg-brand-600/10 text-brand-700 dark:text-brand-300">
                <History className="size-4" aria-hidden="true" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-bold leading-none">Your chats</p>
                <p className="mt-1 text-[11px] text-muted-foreground">
                  {sessions.length === 0 ? "Your questions save here" : `${sessions.length} saved chats`}
                </p>
              </div>
              <Button size="sm" variant="outline" onClick={startNewConversation} aria-label="Start new conversation" className="btn-polish size-8 p-0">
                <Plus className="size-4" aria-hidden="true" />
              </Button>
            </div>
            <div className="p-3">
              <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
                <input
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Search conversations…"
                  aria-label="Search conversations"
                  className="w-full rounded-xl border border-input bg-background py-2 pl-9 pr-3 text-xs outline-none transition placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/30"
                />
              </div>
            </div>
            <div className="max-h-72 overflow-y-auto px-2 pb-3 lg:max-h-[540px]">
              {loadingSessions ? (
                <div className="space-y-2 px-1">
                  <Skeleton className="h-14 w-full rounded-xl" />
                  <Skeleton className="h-14 w-full rounded-xl" />
                  <Skeleton className="h-14 w-full rounded-xl" />
                </div>
              ) : filteredSessions.length === 0 ? (
                <div className="mx-1 rounded-2xl border border-dashed border-border px-3 py-6 text-center">
                  <MessageSquare className="mx-auto size-6 text-muted-foreground/60" aria-hidden="true" />
                  <p className="mt-2 text-xs font-semibold">
                    {sessions.length === 0 ? "No conversations yet" : "No matches found"}
                  </p>
                  <p className="mx-auto mt-1 max-w-[200px] text-[11px] leading-relaxed text-muted-foreground">
                    {sessions.length === 0
                      ? "Send your first message and it will appear here."
                      : "Try a different search term."}
                  </p>
                </div>
              ) : (
                <ul className="space-y-1">
                  {filteredSessions.map((session) => {
                    const active = session.id === activeId;
                    return (
                      <li key={session.id}>
                        <div
                          className={cn(
                            "group flex items-center gap-1 rounded-xl border px-2 py-2 transition",
                            active
                              ? "border-brand-500/40 bg-gradient-to-r from-brand-500/12 to-transparent shadow-sm"
                              : "border-transparent hover:border-border/60 hover:bg-muted/60",
                          )}
                        >
                          <button
                            type="button"
                            onClick={() => setActiveId(session.id)}
                            className="min-w-0 flex-1 text-left"
                          >
                            <p className="flex items-center gap-1.5 truncate text-sm font-semibold">
                              {active && <span className="size-1.5 shrink-0 rounded-full bg-brand-500" aria-hidden="true" />}
                              <span className="truncate">{session.title}</span>
                            </p>
                            <p className="mt-0.5 pl-3 text-[11px] text-muted-foreground">
                              {formatDateTime(session.updatedAt)}
                            </p>
                          </button>
                          <button
                            type="button"
                            onClick={() => deleteSession(session.id)}
                            aria-label={`Delete ${session.title}`}
                            className="rounded-lg p-1.5 text-muted-foreground transition hover:bg-destructive/10 hover:text-destructive sm:opacity-0 sm:group-hover:opacity-100 sm:focus-visible:opacity-100"
                          >
                            <Trash2 className="size-3.5" aria-hidden="true" />
                          </button>
                        </div>
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>
          </CardContent>
        </Card>

        {/* ── Chat panel ── */}
        <Card className="flex min-h-[540px] flex-col overflow-hidden border-primary/20 shadow-card">
          {/* Chat header */}
          <div className="flex flex-wrap items-center gap-3 border-b border-border/60 bg-gradient-to-r from-primary/[0.08] via-muted/40 to-transparent px-4 py-3 sm:px-5">
            <span className="relative flex size-10 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-brand-600 to-pine-800 text-white shadow-md shadow-brand-900/25 ring-1 ring-white/20">
              <Bot className="size-5" aria-hidden="true" />
              <span className="absolute -bottom-0.5 -right-0.5 size-3 rounded-full border-2 border-card bg-emerald-500" aria-hidden="true" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-bold">
                {activeSession ? activeSession.title : "AI Career Assistant"}
              </p>
              <p className="flex flex-wrap items-center gap-1.5 text-[11px] text-muted-foreground">
                <span className="inline-flex items-center gap-1">
                  <Brain className="size-3" aria-hidden="true" />
                  Grounded in your CareerOS data
                </span>
                {(lastMeta.offline !== undefined || lastMeta.mode) && (
                  <ModeBadge offline={lastMeta.offline} mode={lastMeta.mode} />
                )}
              </p>
            </div>
            <Badge variant="secondary" className="hidden sm:inline-flex">
              {messages.length} messages
            </Badge>
          </div>

          {/* Messages */}
          <div ref={scrollRef} className="flex-1 space-y-5 overflow-y-auto bg-gradient-to-b from-muted/40 via-background to-background p-4 sm:p-6">
            {loadingHistory ? (
              <div className="space-y-4">
                <Skeleton className="h-16 w-3/4 rounded-2xl" />
                <Skeleton className="ml-auto h-12 w-2/3 rounded-2xl" />
                <Skeleton className="h-20 w-3/4 rounded-2xl" />
              </div>
            ) : activeMessages.length === 0 && !sending ? (
              <div className="mx-auto max-w-2xl py-2 text-center">
                <span className="relative mx-auto flex size-14 items-center justify-center rounded-3xl bg-gradient-to-br from-brand-600 to-pine-800 text-white shadow-lg shadow-brand-900/25">
                  <Sparkles className="size-7" aria-hidden="true" />
                  <span className="absolute -right-1 -bottom-1 size-4 rounded-full border-[2.5px] border-background bg-emerald-500" aria-hidden="true" />
                </span>
                <h2 className="mt-4 text-lg font-extrabold tracking-tight">How can I help you today?</h2>
                <p className="mx-auto mt-1.5 max-w-md text-sm leading-relaxed text-muted-foreground">
                  I know your progress already. Just tap one — no need to type.
                </p>
                <div className="mt-5 grid gap-2.5 text-left sm:grid-cols-2">
                  {SUGGESTION_CARDS.map((card) => {
                    const Icon = card.icon;
                    return (
                      <button
                        key={card.query}
                        type="button"
                        disabled={sending}
                        onClick={() => sendMessage(card.query)}
                        className="group rounded-2xl border border-border/60 bg-card p-3.5 text-left shadow-card transition hover:-translate-y-1 hover:border-primary/50 hover:shadow-card-hover disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        <span className="flex items-center gap-2.5">
                          <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-primary/15 to-primary/5 text-primary ring-1 ring-primary/20 transition group-hover:from-primary group-hover:to-brand-700 group-hover:text-primary-foreground">
                            <Icon className="size-4" aria-hidden="true" />
                          </span>
                          <span className="text-sm font-bold">{card.title}</span>
                          <ArrowRight className="ml-auto size-3.5 shrink-0 text-muted-foreground/50 transition group-hover:translate-x-0.5 group-hover:text-primary" aria-hidden="true" />
                        </span>
                        <span className="mt-1.5 block text-xs leading-relaxed text-muted-foreground">{card.desc}</span>
                      </button>
                    );
                  })}
                </div>
              </div>
            ) : (
              activeMessages.map((message, index) => (
                <MessageBubble
                  key={message.id ?? index}
                  message={message}
                  isLastAssistant={index === lastAssistantIndex}
                  toolsNote={toolsNote && index === lastAssistantIndex}
                  offline={index === lastAssistantIndex ? lastMeta.offline : undefined}
                  mode={index === lastAssistantIndex ? lastMeta.mode : undefined}
                  copied={copiedId === (message.id ?? index)}
                  onCopy={() => copyMessage(message.id ?? index, message.content)}
                  onFollowUp={(tip) => sendMessage(tip)}
                  sending={sending}
                />
              ))
            )}
            {sending && <TypingIndicator />}
          </div>

          {/* Composer */}
          <form onSubmit={handleSubmit} className="border-t border-border/60 bg-gradient-to-t from-primary/[0.05] to-card p-3 sm:p-4">
            <div className="rounded-2xl border border-border/70 bg-background p-2 shadow-card transition focus-within:border-brand-500/60 focus-within:shadow-card-hover focus-within:ring-2 focus-within:ring-brand-500/20">
              <textarea
                ref={textareaRef}
                value={input}
                onChange={handleInputChange}
                onKeyDown={handleInputKeyDown}
                placeholder="Type your question… e.g. Am I ready for backend jobs?"
                aria-label="Message the AI Career Assistant"
                maxLength={4000}
                rows={2}
                disabled={sending}
                className="max-h-40 min-h-11 w-full resize-none bg-transparent px-3 py-2 text-sm outline-none placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50"
              />
              <div className="flex items-center justify-between gap-2 px-1 pb-1">
                <p className="text-[11px] text-muted-foreground">
                  Press <span className="font-semibold text-foreground">Enter</span> to send
                </p>
                <div className="flex items-center gap-2">
                  {lastFailed && !sending && (
                    <Button type="button" size="sm" variant="outline" onClick={retryLastFailed} className="btn-polish">
                      <RotateCcw className="size-3.5" aria-hidden="true" />
                      Retry
                    </Button>
                  )}
                  <Button
                    type="submit"
                    disabled={sending || !input.trim()}
                    aria-label="Send message"
                    className="btn-polish bg-gradient-to-r from-brand-700 via-brand-600 to-brand-500 shadow-md shadow-brand-600/25"
                  >
                    {sending ? (
                      <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                    ) : (
                      <Send className="size-4" aria-hidden="true" />
                    )}
                    <span className="hidden sm:inline">{sending ? "Thinking…" : "Send"}</span>
                  </Button>
                </div>
              </div>
            </div>
            <p className="mt-2 text-center text-[11px] text-muted-foreground">
              Your chats are private — only you can see them.
            </p>
          </form>
        </Card>
      </div>
    </div>
  );
}

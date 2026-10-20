# AI Agent Tools — Quick Notes

## 1. What Are AI Agents?

Claude Code, OpenCode, Hermes, and OpenClaw are **agent applications**, not fine-tuned models. They provide a predefined **agent harness** around an LLM.

**AI Agent = LLM + Instructions + Tools + Memory + Execution Loop**

- **LLM:** Qwen, Devstral, Claude, GPT, etc.
- **Agent harness:** Manages reasoning steps, tool calls, and execution.
- **Tools:** File access, terminal, APIs, browsers, MCP.
- **Memory:** Maintains context or persistent information.
- **Execution loop:** Repeatedly decides what action to perform.

Ollama supplies the LLM; the agent application supplies the behavior.

## 2. Popular AI Agent Applications

### Coding Agents

| Application | Main purpose |
|---|---|
| Claude Code | Autonomous coding, debugging, and refactoring |
| OpenCode | Open-source coding agent supporting local models |
| OpenAI Codex | Coding, testing, and repository modifications |
| Aider | Terminal-based AI pair programming |
| Cline | VS Code coding agent with tool execution |
| Roo Code | VS Code agent with specialized modes |
| Continue | IDE assistant with configurable models |
| OpenHands | Autonomous software development |

### General-Purpose Agents

| Application | Main purpose |
|---|---|
| Hermes Agent | Persistent memory, skills, tools, and autonomous tasks |
| OpenClaw | Personal assistant with messaging and scheduled automation |
| Goose | Open-source agent for development and automation |

## 3. Frameworks vs. Applications

| Type | Examples | Purpose |
|---|---|---|
| Agent applications | OpenCode, Hermes, OpenClaw, Codex | Ready-to-use agents |
| Agent frameworks | LangGraph, CrewAI, AutoGen, Spring AI | Build custom agents |
| Workflow automation | n8n, Flowise, Dify | Visual workflows and AI applications |
| Tool and communication protocols | MCP, A2A | Connect applications to tools and agents |
| LLM runtimes | Ollama, LM Studio, vLLM | Execute AI models |

## 4. Ollama Commands

### Coding Agents

```bash
ollama launch claude --model qwen3.5:9b
ollama launch opencode --model qwen3.5:9b
ollama launch codex --model qwen3.5:9b
```

### General-Purpose Agents

```bash
ollama launch hermes --model qwen3.5:9b
ollama launch openclaw --model qwen3.5:9b
```

> **Note:** Performance and tool compatibility depend on the model. Small local models may struggle with complex agent tasks.

## 5. Recommendations for My Projects

| Goal | Tool |
|---|---|
| Java / React / Apache Camel development | **OpenCode** |
| Learn agent architecture | **Hermes Agent** |
| Personal AI assistant | OpenClaw |
| Develop custom ETL agents | **Spring AI / LangGraph** |
| Visual automation | n8n |
| Local model inference | Ollama |

## 6. Important Concepts

- **Fine-tuning:** Changes a model's learned parameters through additional training.
- **Agent harness:** Provides an LLM with instructions, tools, memory, execution loops, and error handling without modifying the model itself.
- **Agent framework:** A development library for building and orchestrating custom agents.
- **MCP (Model Context Protocol):** Standard protocol connecting AI applications to tools, services, and data sources.
- **A2A (Agent2Agent Protocol):** Enables independent AI agents to communicate and collaborate.
- **RAG (Retrieval-Augmented Generation):** Retrieves external information and supplies it as context to an LLM.
- **Multi-agent system:** Multiple agents with different responsibilities working together; they may share the same LLM.

## 7. Key Takeaways

| Term | Meaning |
|---|---|
| **LLM** | Intelligence / model |
| **Ollama** | Model execution |
| **Agent harness** | Tools + memory + execution loop |
| **Agent framework** | Build custom agents |
| **MCP** | Connect agents to tools |
| **A2A** | Connect agents to other agents |
| **n8n** | Workflow automation |
| **LangGraph** | Stateful agent orchestration |
| **Apache Camel** | Enterprise integration and data routing |

# JADX-AI-MCP Documentation

Welcome to the comprehensive documentation for **JADX-AI-MCP**, an AI-powered Android reverse engineering toolkit that bridges JADX decompiler with Large Language Models through the Model Context Protocol.

## 🎯 What is JADX-AI-MCP?

JADX-AI-MCP is a sophisticated reverse engineering solution consisting of two tightly integrated components:

1. **JADX-AI-MCP Plugin (Java)** - A JADX-GUI plugin that exposes the decompiler's functionality through a local HTTP API
2. **JADX-MCP-Server (Python)** - An MCP server that translates HTTP endpoints into MCP tools consumable by AI assistants

Together, these components enable real-time, AI-assisted Android application analysis, vulnerability detection, and code understanding.

## 🌟 Key Features

### Advanced Analysis Capabilities
- **Real-time Code Review**: AI analyzes decompiled code as you navigate
- **Cross-Reference Analysis**: Track method/class/field usage across entire codebases
- **Resource Extraction**: Access manifests, strings, layouts, and resources
- **Smali Analysis**: Low-level bytecode inspection for deep analysis

### Security & Reverse Engineering
- **Vulnerability Detection**: Automated SAST (Static Application Security Testing)
- **Malware Analysis**: Behavioral pattern detection and C2 identification
- **Obfuscation Analysis**: Systematic deobfuscation workflows
- **Permission Auditing**: Identify overprivileged and unused permissions

### Development Assistance
- **Intelligent Refactoring**: AI-assisted renaming of obfuscated code
- **Debug Integration**: Runtime variable inspection and stack trace analysis
- **Pagination Support**: Efficient handling of large APKs (10,000+ classes)
- **Multi-Client Support**: Works with Claude, Cherry Studio, LM Studio

## 📊 Architecture Overview

```mermaid
graph TB
    subgraph "User Layer"
        A[User] -->|Natural Language| B[LLM Client]
    end

    subgraph "MCP Layer"
        B -->|MCP Protocol| C[MCP Server Python]
        C -->|Tool Invocation| D[FastMCP Framework]
    end

    subgraph "Integration Layer"
        D -->|HTTP Request| E[JADX Plugin Server]
        E -->|Javalin Routes| F[Route Handlers]
    end

    subgraph "Analysis Layer"
        F -->|JADX API| G[JADX Wrapper]
        G -->|Query| H[Decompiled Code]
        G -->|Query| I[Resources]
        G -->|Query| J[Debug Info]
    end

    H -->|Response| F
    I -->|Response| F
    J -->|Response| F
```

## 🚀 Quick Start

### Prerequisites

| Component | Version Required | Purpose |
|-----------|-----------------|---------|
| Java | 11+ | JADX Plugin runtime |
| Python | 3.10+ | MCP Server runtime |
| JADX | 1.5.1+ (r2333+) | Decompilation engine |
| UV | Latest | Python package manager |

### Installation (5 minutes)

```bash
# Step 1: Install JADX Plugin
jadx plugins --install "github:zinja-coder:jadx-ai-mcp"

# Step 2: Download MCP Server
wget https://github.com/zinja-coder/jadx-ai-mcp/releases/latest/download/jadx-mcp-server.zip
unzip jadx-mcp-server.zip

# Step 3: Install UV
curl -LsSf https://astral.sh/uv/install.sh | sh

# Step 4: Run MCP Server
cd jadx-mcp-server
uv run jadx_mcp_server.py

# Step 5: Configure Claude Desktop
# Edit: ~/.config/Claude/claude_desktop_config.json
```

See [Installation Guide](installation.md) for detailed instructions.

## 📚 Documentation Structure

### For Users
- **[Installation Guide](installation.md)** - Complete setup for all platforms
- **[User Guide](user-guide.md)** - Workflows, prompts, and best practices
- **[Examples](examples.md)** - Real-world analysis scenarios

### For Developers
- **[API Reference](api-reference.md)** - Complete tool documentation with examples
- **[Architecture](architecture.md)** - System design and component interaction
- **[Python Module Reference](python-api.md)** - Server-side code documentation
- **[Java Plugin Reference](java-api.md)** - Plugin internals and extension points

### Support
- **[Troubleshooting](troubleshooting.md)** - Common issues and solutions
- **[FAQ](faq.md)** - Frequently asked questions
- **[Contributing](contributing.md)** - Development guidelines

## 🎓 Learning Path

### Beginner (Day 1)
1. Complete installation
2. Load sample APK
3. Try basic prompts: "Fetch current class and explain it"
4. Explore manifest and resources

### Intermediate (Week 1)
1. Learn search and xref tools
2. Practice systematic analysis workflows
3. Understand pagination for large APKs
4. Try refactoring obfuscated code

### Advanced (Month 1)
1. Integrate with debugger
2. Build custom analysis workflows
3. Automate vulnerability scanning
4. Contribute new tools/features

## 🔧 Tool Categories

### Class Analysis Tools (10 tools)
Tools for analyzing decompiled Java classes, methods, and fields.

→ [View Class Tools](api-reference.md#class-analysis-tools)

### Search Tools (3 tools)
Full-text search across classes, methods, and code.

→ [View Search Tools](api-reference.md#search-tools)

### Resource Tools (4 tools)
Access to AndroidManifest, strings, layouts, and resources.

→ [View Resource Tools](api-reference.md#resource-tools)

### Cross-Reference Tools (3 tools)
Track usage of classes, methods, and fields across codebase.

→ [View Xref Tools](api-reference.md#cross-reference-tools)

### Refactoring Tools (4 tools)
Rename classes, methods, fields, and packages.

→ [View Refactor Tools](api-reference.md#refactoring-tools)

### Debug Tools (3 tools)
Runtime analysis during JADX debugging sessions.

→ [View Debug Tools](api-reference.md#debug-tools)

## 🔐 Security & Privacy

- **Local-Only Communication**: All traffic stays on localhost (127.0.0.1)
- **No External Connections**: Plugin and server communicate internally
- **No Data Collection**: No telemetry or usage tracking
- **Open Source**: Fully auditable codebase

## 📈 Performance Characteristics

| Operation | Time (Small APK) | Time (Large APK) | Notes |
|-----------|-----------------|------------------|-------|
| Get class source | <100ms | <500ms | Cached by JADX |
| Search classes | 1-2s | 5-10s | Depends on term |
| Get all classes | <1s | 2-5s | Use pagination |
| Xref analysis | <500ms | 2-3s | Pagination recommended |

## 🌐 Supported Platforms

| Platform | Plugin Support | Server Support | Status |
|----------|---------------|----------------|--------|
| Linux | ✅ Full | ✅ Full | Tested |
| macOS | ✅ Full | ✅ Full | Tested |
| Windows | ✅ Full | ✅ Full | Tested |
| WSL2 | ✅ Full | ✅ Full | Community tested |

## 🤝 Community & Support

- **GitHub Issues**: [Report bugs](https://github.com/zinja-coder/jadx-ai-mcp/issues)
- **Discussions**: [Ask questions](https://github.com/zinja-coder/jadx-ai-mcp/discussions)
- **Discord**: [Join community](https://discord.gg/jadx-ai-mcp)
- **Email**: support@jadx-ai-mcp.dev

## 📄 License

Apache License 2.0 - See [LICENSE](license.md)

## 🙏 Acknowledgments

- **JADX Project**: [@skylot](https://github.com/skylot) - Amazing decompiler
- **Anthropic**: Model Context Protocol development
- **FastMCP**: [@jlowin](https://github.com/jlowin) - Python MCP framework

## 🎯 Next Steps

1. **New Users**: Start with [Installation](installation.md)
2. **Quick Demo**: See [Examples](examples.md)
3. **API Exploration**: Browse [API Reference](api-reference.md)
4. **Contributing**: Read [Contributing Guide](contributing.md)

---

**Current Version**: 5.0.0 | **Last Updated**: December 2025

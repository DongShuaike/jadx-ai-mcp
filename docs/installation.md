# Installation

## 1) Install the JADX plugin

### Option A: Install via JADX CLI (recommended)

```bash
jadx plugins --install "github:zinja-coder:jadx-ai-mcp"
```

### Option B: Install via JADX-GUI

1. Download the latest `jadx-ai-mcp-<version>.jar` from GitHub releases.
2. In JADX-GUI: Plugins → Install plugin → select the jar.
3. Restart JADX-GUI.

## 2) Set up the MCP server (Python)

### Using uv (recommended)

```bash
# Install uv
curl -LsSf https://astral.sh/uv/install.sh | sh

# Run the server
cd /path/to/jadx-mcp-server
uv run jadx_mcp_server.py
```

### Using pip/venv (fallback)

```bash
cd /path/to/jadx-mcp-server
python3 -m venv .venv
source .venv/bin/activate
pip install httpx fastmcp
python3 jadx_mcp_server.py
```

## 3) Configure Claude Desktop (example)

Config file locations:
- Linux: `~/.config/Claude/claude_desktop_config.json`
- Windows: `%APPDATA%\Claude\claude_desktop_config.json`
- macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`

Example config:

```json
{
  "mcpServers": {
    "jadx-mcp-server": {
      "command": "/absolute/path/to/uv",
      "args": [
        "--directory",
        "/absolute/path/to/jadx-mcp-server",
        "run",
        "jadx_mcp_server.py"
      ]
    }
  }
}
```

or you can install the jadx_mcp_server directly as executable directly using below command:

```
uv tool install git+https://github.com/zinja-coder/jadx-mcp-server
```

and then you can just provide `jadx_mcp_server` in `command` section of mcp configuration.

## 4) Use Cherry Studio

If you want to configure the MCP tool in Cherry Studio, you can refer to the following configuration.
- Type: stdio
- command: uv
- argument:
```bash
--directory
path/to/jadx-mcp-server
run
jadx_mcp_server.py
```
- `path/to/jadx-mcp-server` with the absolute path to where you cloned this
repository

## 5) Using LMStudio

You can also use JADX AI MCP Server with LM Studio by configuring it's mcp.json file. Here's the video guide.

https://github.com/user-attachments/assets/b4a6b280-5aa9-4e76-ac72-a0abec73b809

## 6) Verify

1. Open JADX-GUI and load an APK.
2. Ensure the plugin server is running (Plugins → JADX-AI-MCP → Server Status).
3. In your LLM client, confirm MCP tools are visible.

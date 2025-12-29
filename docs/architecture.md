# Architecture

## Components

- **JADX plugin (Java)**: local HTTP API that reads from JADX GUI/model.
- **MCP server (Python)**: maps HTTP endpoints to MCP tools (FastMCP).
- **LLM client**: calls tools and displays results.

## Interfaces

- MCP (LLM client ↔ Python server)
- HTTP (Python server ↔ Java plugin)

## Notes

- Communication is intended to be local (127.0.0.1).
- Pagination is used to handle large APKs efficiently.

# Troubleshooting

## MCP tools not visible in LLM client

- Confirm your client supports MCP and is reading the correct config file.
- Run the configured command in a terminal to verify it starts.

## Plugin health check fails

Test:

```bash
curl http://127.0.0.1:8650/health
```

If it fails:
- Ensure JADX-GUI is running and an APK is loaded.
- Verify the plugin is installed and server started.
- Check for port conflicts and change the plugin port if needed.

## Port already in use

Change either:
- plugin port (inside JADX plugin menu), or
- Python server `--jadx-port` argument.

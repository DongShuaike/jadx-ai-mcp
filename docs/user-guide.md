# User guide

## Typical workflow

1. Load an APK in JADX-GUI.
2. Select a class/method in the decompiled view.
3. Ask your LLM client to call tools like `fetch_current_class()`.

## Useful prompts

- “Fetch the currently selected class and do a quick security review.”
- “Search for classes containing the keyword `WebView` and report risky settings.”
- “Find xrefs to `CryptoHelper.encrypt` and summarize the call sites.”

## Working with large apps

Prefer paginated tools:

- `get_all_classes(offset=0, count=200)`
- `search_classes_by_keyword(search_term="password", offset=0, count=50)`
- `get_xrefs_to_method(..., offset=0, count=50)`

## Debugger helpers

If JADX debugger is attached and paused:

- `debug_get_stack_frames()`
- `debug_get_threads()`
- `debug_get_variables()`

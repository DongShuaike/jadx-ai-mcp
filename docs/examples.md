# Examples

## 1) Quick SAST on current class

Prompt:

> Fetch currently selected class and perform quick SAST on it.

Typical follow-ups:
- Identify insecure crypto.
- Check WebView configuration.
- Look for hardcoded secrets.

## 2) Find all WebView usage

1. `search_classes_by_keyword("WebView", offset=0, count=50)`
2. For each result, `get_class_source(class_name)`
3. Flag risky settings (e.g., JavaScript enabled, file access, mixed content).

## 3) Trace a method’s call sites

1. `get_xrefs_to_method("com.example.Auth", "login", offset=0, count=100)`
2. Pull sources for callers; summarize the flow.

## 4) Refactor obfuscation

- Rename class/method/field/package based on behavior to improve readability.

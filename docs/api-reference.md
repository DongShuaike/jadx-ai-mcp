# API reference

This reference documents the MCP tools exposed by `jadx_mcp_server.py`.

## Class & code tools

- `fetch_current_class()` – Selected class + decompiled source.
- `get_selected_text()` – Current editor selection.
- `get_all_classes(offset=0, count=0)` – List classes (paginated).
- `get_class_source(class_name)` – Decompiled Java for class.
- `get_methods_of_class(class_name)` – Method names.
- `get_fields_of_class(class_name)` – Field names.
- `get_smali_of_class(class_name)` – Smali for class.
- `get_main_activity_class()` – Launcher activity.
- `get_main_application_classes_names()` – Main package classes.
- `get_main_application_classes_code(offset=0, count=0)` – Main package sources (paginated).

## Search tools

- `get_method_by_name(class_name, method_name)`
- `search_method_by_name(method_name)`
- `search_classes_by_keyword(search_term, offset=0, count=20)`

## Resource tools

- `get_android_manifest()`
- `get_strings(offset=0, count=0)`
- `get_all_resource_file_names(offset=0, count=0)`
- `get_resource_file(resource_name)`

## Xrefs tools

- `get_xrefs_to_class(class_name, offset=0, count=20)`
- `get_xrefs_to_method(class_name, method_name, offset=0, count=20)`
- `get_xrefs_to_field(class_name, field_name, offset=0, count=20)`

## Refactor tools

- `rename_class(class_name, new_name)`
- `rename_method(method_name, new_name)`
- `rename_field(class_name, field_name, new_name)`
- `rename_package(old_package_name, new_package_name)`

## Debug tools

- `debug_get_stack_frames()`
- `debug_get_threads()`
- `debug_get_variables()`

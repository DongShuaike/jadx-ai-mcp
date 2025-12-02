#!/usr/bin/env python3
"""
JADX AI MCP - Cross Reference (Xref) API Test Script

Test endpoints:
- /xrefs-to-class  - Find all references to a class
- /xrefs-to-method - Find all references to a method
- /xrefs-to-field  - Find all references to a field

Prerequisites:
1. JADX GUI is running with an APK loaded
2. JADX AI MCP plugin is running (default port 8650)
"""

import requests
import json

BASE_URL = "http://127.0.0.1:8650"


def xrefs_to_class(class_name: str, offset: int = 0, limit: int = 100) -> dict:
    """
    Find all references to a specified class
    
    Args:
        class_name: Full class name, e.g., "com.example.MyClass"
        offset: Pagination offset
        limit: Number of items per page
    
    Returns:
        Dictionary containing reference list
    """
    params = {
        "class": class_name,
        "offset": offset,
        "limit": limit
    }
    response = requests.get(f"{BASE_URL}/xrefs-to-class", params=params)
    return response.json()


def xrefs_to_method(class_name: str, method_name: str, offset: int = 0, limit: int = 100) -> dict:
    """
    Find all references to a specified method
    
    Args:
        class_name: Full class name where the method is located
        method_name: Method name (use the name shown after JADX decompilation)
        offset: Pagination offset
        limit: Number of items per page
    
    Returns:
        Dictionary containing reference list
    """
    params = {
        "class": class_name,
        "method": method_name,
        "offset": offset,
        "limit": limit
    }
    response = requests.get(f"{BASE_URL}/xrefs-to-method", params=params)
    return response.json()


def xrefs_to_field(class_name: str, field_name: str, offset: int = 0, limit: int = 100) -> dict:
    """
    Find all references to a specified field
    
    Args:
        class_name: Full class name where the field is located
        field_name: Field name (use the name shown after JADX decompilation)
        offset: Pagination offset
        limit: Number of items per page
    
    Returns:
        Dictionary containing reference list
    """
    params = {
        "class": class_name,
        "field": field_name,
        "offset": offset,
        "limit": limit
    }
    response = requests.get(f"{BASE_URL}/xrefs-to-field", params=params)
    return response.json()


def print_result(title: str, result: dict):
    """Format and print result"""
    print(f"\n{'='*60}")
    print(f"  {title}")
    print('='*60)
    print(json.dumps(result, indent=2, ensure_ascii=False))


def test_xrefs_to_class():
    """Test /xrefs-to-class endpoint"""
    
    # Test case 1: Find references to a utility class
    print("\n[Test 1] Find class references")
    
    # Replace with an actual class name from your APK
    test_class = "okhttp3.Authenticator"
    
    try:
        result = xrefs_to_class(test_class)
        print_result(f"References to class {test_class}", result)
        
        if "references" in result:
            print(f"\nFound {len(result['references'])} references")
            for ref in result["references"][:5]:  # Show first 5 only
                if ref.get("method"):
                    print(f"  - {ref['class']}.{ref['method']}()")
                else:
                    print(f"  - {ref['class']} (class-level reference)")
    except Exception as e:
        print(f"Error: {e}")

    # Test case 2: Paginated query
    print("\n[Test 2] Paginated query")
    
    try:
        result = xrefs_to_class(test_class, offset=0, limit=5)
        print_result(f"Paginated query (offset=0, limit=5)", result)
        
        if result.get("pagination", {}).get("has_more"):
            print(f"\nMore results available, next offset: {result['pagination']['next_offset']}")
    except Exception as e:
        print(f"Error: {e}")

    # Test case 3: Find non-existent class
    print("\n[Test 3] Find non-existent class")
    
    try:
        result = xrefs_to_class("com.not.exist.FakeClass")
        print_result("Non-existent class", result)
    except Exception as e:
        print(f"Error: {e}")


def test_xrefs_to_method():
    """Test /xrefs-to-method endpoint"""
    
    # Test case 1: Find references to a normal method
    print("\n[Test 1] Find method references")
    
    # Replace with actual class and method names from your APK
    test_class = "okhttp3.HttpUrl"
    test_method = "addEncodedQueryParameter"  # Use the method name shown after JADX decompilation
    
    try:
        result = xrefs_to_method(test_class, test_method)
        print_result(f"References to method {test_class}.{test_method}()", result)
        
        if "references" in result:
            print(f"\nFound {len(result['references'])} references")
            for ref in result["references"][:5]:  # Show first 5 only
                print(f"  - {ref['class']}.{ref['method']}()")
    except Exception as e:
        print(f"Error: {e}")

    # Test case 2: Find constructor references
    print("\n[Test 2] Find constructor references")
    
    # Constructor uses simple class name as method name
    simple_class_name = test_class.split(".")[-1]  # AirlineLogoUtil
    
    try:
        result = xrefs_to_method(test_class, simple_class_name)
        print_result(f"References to constructor {test_class}.{simple_class_name}()", result)
        
        if "references" in result:
            print(f"\nFound {len(result['references'])} references (new {simple_class_name}() calls)")
    except Exception as e:
        print(f"Error: {e}")

    # Test case 3: Paginated query
    print("\n[Test 3] Paginated query for method references")
    
    try:
        result = xrefs_to_method(test_class, test_method, offset=0, limit=3)
        print_result(f"Paginated query (offset=0, limit=3)", result)
    except Exception as e:
        print(f"Error: {e}")

    # Test case 4: Find non-existent method
    print("\n[Test 4] Find non-existent method")
    
    try:
        result = xrefs_to_method(test_class, "nonExistentMethod")
        print_result("Non-existent method", result)
    except Exception as e:
        print(f"Error: {e}")


def test_xrefs_to_field():
    """Test /xrefs-to-field endpoint"""
    
    # Test case 1: Find field references
    print("\n[Test 1] Find field references")
    
    # Replace with actual class and field names from your APK
    test_class = "okhttp3.OkHttpClient"
    test_field = "dispatcher"  # Use the field name shown after JADX decompilation
    
    try:
        result = xrefs_to_field(test_class, test_field)
        print_result(f"References to field {test_class}.{test_field}", result)
        
        if "references" in result:
            print(f"\nFound {len(result['references'])} references")
            for ref in result["references"][:5]:  # Show first 5 only
                print(f"  - {ref['class']}.{ref['method']}()")
    except Exception as e:
        print(f"Error: {e}")

    # Test case 2: Paginated query
    print("\n[Test 2] Paginated query for field references")
    
    try:
        result = xrefs_to_field(test_class, test_field, offset=0, limit=3)
        print_result(f"Paginated query (offset=0, limit=3)", result)
        
        if result.get("pagination", {}).get("has_more"):
            print(f"\nMore results available, next offset: {result['pagination']['next_offset']}")
    except Exception as e:
        print(f"Error: {e}")

    # Test case 3: Find non-existent field
    print("\n[Test 3] Find non-existent field")
    
    try:
        result = xrefs_to_field(test_class, "nonExistentField")
        print_result("Non-existent field", result)
    except Exception as e:
        print(f"Error: {e}")

    # Test case 4: Find static field references
    print("\n[Test 4] Find static field references")
    
    # Replace with an actual static field from your APK
    static_class = "okhttp3.OkHttpClient"
    static_field = "DEFAULT_PROTOCOLS"  # Example static field name
    
    try:
        result = xrefs_to_field(static_class, static_field)
        print_result(f"References to static field {static_class}.{static_field}", result)
        
        if "references" in result:
            print(f"\nFound {len(result['references'])} references")
    except Exception as e:
        print(f"Error: {e}")


def test_health():
    """Test if the service is running properly"""
    try:
        response = requests.get(f"{BASE_URL}/health", timeout=5)
        result = response.json()
        print(f"Service status: {result.get('status', 'unknown')}")
        print(f"Service URL: {result.get('url', 'unknown')}")
        return result.get('status') == 'Running'
    except requests.exceptions.ConnectionError:
        print("Error: Unable to connect to JADX AI MCP service")
        print(f"Please ensure JADX GUI is running and plugin is active on {BASE_URL}")
        return False
    except Exception as e:
        print(f"Error: {e}")
        return False


if __name__ == "__main__":
    print("=" * 60)
    print("  JADX AI MCP - Xref API Test")
    print("=" * 60)
    
    # Check service status first
    print("\n[Check Service Status]")
    if not test_health():
        print("\nService is not running. Please start JADX GUI and load an APK first.")
        exit(1)
    
    print("\n" + "=" * 60)
    print("  Test /xrefs-to-class endpoint")
    print("=" * 60)
    test_xrefs_to_class()
    
    print("\n" + "=" * 60)
    print("  Test /xrefs-to-method endpoint")
    print("=" * 60)
    test_xrefs_to_method()

    print("\n" + "=" * 60)
    print("  Test /xrefs-to-field endpoint")
    print("=" * 60)
    test_xrefs_to_field()
    
    print("\n" + "=" * 60)
    print("  Test completed")
    print("=" * 60)

#!/usr/bin/env python3
"""
Cleanup script for Android string resources.

This script:
1. Cleans up unused string keys from all strings.xml files in values* folders
2. Fixes multiple substitution format issues (converts %d to %1$d, %2$d, etc.)
3. Reports missing keys in each localized strings.xml compared to the base values/strings.xml
"""

import os
import re
import sys
from pathlib import Path
from xml.etree import ElementTree as ET
from typing import Set, Dict, List, Tuple


# =============================================================================
# Terminal Colors (ANSI escape codes)
# =============================================================================

class Colors:
    """ANSI color codes for terminal output."""
    # Reset
    RESET = '\033[0m'
    
    # Regular colors
    BLACK = '\033[30m'
    RED = '\033[31m'
    GREEN = '\033[32m'
    YELLOW = '\033[33m'
    BLUE = '\033[34m'
    MAGENTA = '\033[35m'
    CYAN = '\033[36m'
    WHITE = '\033[37m'
    
    # Bold/Bright colors
    BOLD = '\033[1m'
    DIM = '\033[2m'
    BOLD_RED = '\033[1;31m'
    BOLD_GREEN = '\033[1;32m'
    BOLD_YELLOW = '\033[1;33m'
    BOLD_BLUE = '\033[1;34m'
    BOLD_MAGENTA = '\033[1;35m'
    BOLD_CYAN = '\033[1;36m'
    BOLD_WHITE = '\033[1;37m'
    
    # Background colors
    BG_RED = '\033[41m'
    BG_GREEN = '\033[42m'
    BG_YELLOW = '\033[43m'
    BG_BLUE = '\033[44m'


def enable_windows_ansi():
    """Enable ANSI escape codes on Windows."""
    if sys.platform == 'win32':
        try:
            import ctypes
            kernel32 = ctypes.windll.kernel32
            # Enable VIRTUAL_TERMINAL_PROCESSING
            kernel32.SetConsoleMode(kernel32.GetStdHandle(-11), 7)
        except Exception:
            pass  # Fall back to no colors if it fails


def c(text: str, color: str) -> str:
    """Wrap text with color codes."""
    return f"{color}{text}{Colors.RESET}"


def header(text: str) -> str:
    """Format a header line."""
    return c(text, Colors.BOLD_CYAN)


def success(text: str) -> str:
    """Format success message."""
    return c(text, Colors.BOLD_GREEN)


def warning(text: str) -> str:
    """Format warning message."""
    return c(text, Colors.BOLD_YELLOW)


def error(text: str) -> str:
    """Format error message."""
    return c(text, Colors.BOLD_RED)


def info(text: str) -> str:
    """Format info message."""
    return c(text, Colors.CYAN)


def dim(text: str) -> str:
    """Format dimmed/secondary text."""
    return c(text, Colors.DIM)


def number(text: str) -> str:
    """Format numbers."""
    return c(text, Colors.BOLD_MAGENTA)


# Enable ANSI on Windows
enable_windows_ansi()


def get_project_root() -> Path:
    """Get the project root directory (parent of scripts folder)."""
    return Path(__file__).parent.parent


def get_base_strings_path() -> Path:
    """Get the path to the base strings.xml file."""
    return get_project_root() / "app_pojavlauncher" / "src" / "main" / "res" / "values" / "strings.xml"


def get_values_folders() -> List[Path]:
    """Get all values* folders containing strings.xml files."""
    res_path = get_project_root() / "app_pojavlauncher" / "src" / "main" / "res"
    folders = []
    for folder in res_path.iterdir():
        if folder.is_dir() and folder.name.startswith("values"):
            strings_file = folder / "strings.xml"
            if strings_file.exists():
                folders.append(folder)
    return sorted(folders, key=lambda x: x.name)


def parse_string_keys(strings_file: Path) -> Dict[str, Tuple[str, bool]]:
    """
    Parse a strings.xml file and return a dict of {key: (value, is_translatable)}.
    
    The is_translatable flag is False if the string has translatable="false".
    """
    keys = {}
    try:
        tree = ET.parse(strings_file)
        root = tree.getroot()
        for string_elem in root.findall(".//string"):
            name = string_elem.get("name")
            if name:
                translatable = string_elem.get("translatable", "true").lower() != "false"
                # Get the text content (including CDATA)
                if string_elem.text:
                    value = string_elem.text
                else:
                    value = ET.tostring(string_elem, encoding="unicode", method="text")
                keys[name] = (value, translatable)
    except ET.ParseError as e:
        print(f"  Warning: Failed to parse {strings_file}: {e}")
    return keys


# Keys that should NEVER be deleted, even if not found in code search.
# These are essential strings referenced in AndroidManifest.xml, build configs,
# or used by the Android system in ways that can't be easily detected.
PROTECTED_KEYS = {
    # App identity
    'app_name',              # Application label in AndroidManifest.xml
    'app_short_name',        # Short app name
    
    # Activity labels from AndroidManifest.xml
    'import_control_label',  # Import controls activity label
    
    # Notification channels
    'notif_channel_id',      # Notification channel ID
    'notif_channel_name',    # Notification channel name
    
    # URLs and external references (translatable="false" but still needed)
    'discord_invite',
    'github_url',
    
    # Format strings that might not be detected
    'percent_format',
    'color_default_hex',
}


def find_used_string_keys(project_root: Path) -> Set[str]:
    """
    Find all string keys used in the project.
    
    Searches for:
    - R.string.xxx in Java/Kotlin files
    - @string/xxx in XML files
    
    Also includes PROTECTED_KEYS that should never be deleted.
    """
    # Start with protected keys that must never be deleted
    used_keys = PROTECTED_KEYS.copy()
    
    # Patterns to find string references
    java_pattern = re.compile(r'R\.string\.(\w+)')
    xml_pattern = re.compile(r'@string/(\w+)')
    
    # Search directories
    search_dirs = [
        project_root / "app_pojavlauncher" / "src" / "main" / "java",
        project_root / "app_pojavlauncher" / "src" / "main" / "res",
        project_root / "app_pojavlauncher" / "src" / "main" / "jni",  # For SDL subdirectory AndroidManifest
    ]
    
    # Also search specifically for AndroidManifest.xml files
    manifest_files = [
        project_root / "app_pojavlauncher" / "src" / "main" / "AndroidManifest.xml",
    ]
    
    # File extensions to search
    extensions = {'.java', '.kt', '.xml'}
    
    for search_dir in search_dirs:
        if not search_dir.exists():
            continue
        for file_path in search_dir.rglob("*"):
            if file_path.suffix not in extensions:
                continue
            # Skip strings.xml files themselves
            if file_path.name == "strings.xml":
                continue
            try:
                content = file_path.read_text(encoding='utf-8', errors='ignore')
                # Find Java/Kotlin references
                used_keys.update(java_pattern.findall(content))
                # Find XML references
                used_keys.update(xml_pattern.findall(content))
            except Exception as e:
                print(f"  Warning: Failed to read {file_path}: {e}")
    
    # Search AndroidManifest.xml files specifically
    for manifest_file in manifest_files:
        if not manifest_file.exists():
            continue
        try:
            content = manifest_file.read_text(encoding='utf-8', errors='ignore')
            used_keys.update(xml_pattern.findall(content))
        except Exception as e:
            print(f"  Warning: Failed to read {manifest_file}: {e}")
    
    return used_keys


def fix_positional_format(value: str) -> str:
    """
    Fix multiple substitution format issues in Android strings.
    
    Converts non-positional format specifiers to positional ones.
    For example: "Value %d and %d" becomes "Value %1$d and %2$d"
    
    Only fixes if there are 2 or more non-positional specifiers.
    """
    # Pattern to match non-positional format specifiers (e.g., %d, %s, %f)
    # but NOT already positional ones (e.g., %1$d, %2$s)
    # Also handle %% (escaped percent) - don't count these
    
    # Find all format specifiers
    # Non-positional: %d, %s, %f, etc.
    # Positional: %1$d, %2$s, etc.
    non_positional_pattern = re.compile(r'(?<!%)%(?!\d+\$)([dsfoxXeEgGaAc])')
    
    matches = list(non_positional_pattern.finditer(value))
    
    # Only fix if there are 2 or more non-positional specifiers
    if len(matches) < 2:
        return value
    
    # Replace from end to start to preserve positions
    result = value
    for i, match in enumerate(reversed(matches), 1):
        pos = len(matches) - i + 1
        specifier = match.group(1)
        start, end = match.span()
        result = result[:start] + f'%{pos}${specifier}' + result[end:]
    
    return result


def remove_unused_keys_from_file(strings_file: Path, used_keys: Set[str], base_keys: Dict[str, Tuple[str, bool]]) -> Tuple[int, int, List[str]]:
    """
    Remove unused keys from a strings.xml file.
    Also removes XML comments, empty lines, sorts keys alphabetically,
    and fixes multiple substitution format issues.
    
    Returns a tuple of (number of removed keys, number of format fixes, list of removed key names).
    """
    removed_keys = []
    format_fixes = 0
    
    try:
        # Read the file content as text
        content = strings_file.read_text(encoding='utf-8')
        
        # Parse to find string names and identify unused keys
        tree = ET.parse(strings_file)
        root = tree.getroot()
        
        for string_elem in root.findall(".//string"):
            name = string_elem.get("name")
            if name and name not in used_keys:
                # Check if it's a non-translatable string in base - these are internal and should be kept
                if name in base_keys and not base_keys[name][1]:
                    continue  # Skip non-translatable strings
                removed_keys.append(name)
        
        # Extract all string elements with their full text representation
        # Use regex to capture complete <string>...</string> elements
        string_pattern = re.compile(
            r'<string\s+name="([^"]+)"([^>]*)>(.*?)</string>',
            re.DOTALL
        )
        
        # Find all string elements
        strings_data = []
        for match in string_pattern.finditer(content):
            name = match.group(1)
            attrs = match.group(2)  # Additional attributes like translatable="false"
            value = match.group(3)
            
            # Skip unused keys
            if name in removed_keys:
                continue
            
            # Fix positional format issues
            fixed_value = fix_positional_format(value)
            if fixed_value != value:
                format_fixes += 1
            
            strings_data.append((name, attrs, fixed_value))
        
        # Sort by name alphabetically (case-insensitive)
        strings_data.sort(key=lambda x: x[0].lower())
        
        # Get the namespace declaration from the original root
        namespaces = ''
        ns_match = re.search(r'<resources([^>]*)>', content)
        if ns_match:
            namespaces = ns_match.group(1)
        
        # Rebuild the XML content
        new_lines = [f'<?xml version="1.0" encoding="utf-8"?>']
        new_lines.append(f'<resources{namespaces}>')
        
        for name, attrs, value in strings_data:
            # Reconstruct the string element
            new_lines.append(f'    <string name="{name}"{attrs}>{value}</string>')
        
        new_lines.append('</resources>')
        
        new_content = '\n'.join(new_lines) + '\n'
        
        # Write the new content
        strings_file.write_text(new_content, encoding='utf-8')
        
        return len(removed_keys), format_fixes, removed_keys
        
    except Exception as e:
        print(f"  Error processing {strings_file}: {e}")
        return 0, 0, []


def find_missing_keys(base_keys: Dict[str, Tuple[str, bool]], variant_keys: Dict[str, Tuple[str, bool]]) -> List[str]:
    """
    Find keys that are in base but missing from variant.
    
    Only considers translatable keys.
    """
    missing = []
    for key, (value, translatable) in base_keys.items():
        if translatable and key not in variant_keys:
            missing.append(key)
    return sorted(missing)


def main():
    # Title banner
    print()
    print(header("=" * 60))
    print(header("  🧹 Android String Resources Cleanup Script"))
    print(header("=" * 60))
    print()
    
    project_root = get_project_root()
    print(f"📂 Project root: {info(str(project_root))}")
    print()
    
    # Step 1: Parse base strings.xml
    base_strings_path = get_base_strings_path()
    if not base_strings_path.exists():
        print(error(f"❌ Error: Base strings.xml not found at {base_strings_path}"))
        sys.exit(1)
    
    print(f"📖 Parsing base strings.xml...")
    base_keys = parse_string_keys(base_strings_path)
    print(f"   Found {number(str(len(base_keys)))} string keys in base")
    print()
    
    # Step 2: Find all used string keys
    print(f"🔍 Scanning project for string key usage...")
    used_keys = find_used_string_keys(project_root)
    print(f"   Found {number(str(len(used_keys)))} unique string keys in use")
    print(f"   {dim(f'(includes {len(PROTECTED_KEYS)} protected keys that are never deleted)')}")
    print()
    
    # Step 3: Find unused keys
    unused_in_base = set()
    for key, (value, translatable) in base_keys.items():
        if key not in used_keys:
            # Keep non-translatable strings (internal use)
            if translatable:
                unused_in_base.add(key)
    
    if unused_in_base:
        print(warning(f"⚠️  Found {len(unused_in_base)} unused translatable keys in base:"))
        for key in sorted(unused_in_base):
            print(f"    {dim('•')} {warning(key)}")
    else:
        print(success(f"✅ No unused translatable keys in base!"))
    print()
    
    # Step 4: Process all values folders
    values_folders = get_values_folders()
    print(f"📁 Found {number(str(len(values_folders)))} values folders with strings.xml")
    print()
    
    print(header("=" * 60))
    print(header("  🔧 CLEANING UP UNUSED KEYS AND FIXING FORMAT ISSUES"))
    print(header("=" * 60))
    print()
    
    total_removed = 0
    total_format_fixes = 0
    
    for folder in values_folders:
        strings_file = folder / "strings.xml"
        folder_name = folder.name
        
        removed_count, format_fix_count, removed_keys = remove_unused_keys_from_file(strings_file, used_keys, base_keys)
        total_removed += removed_count
        total_format_fixes += format_fix_count
        
        # Color-code based on changes made
        if removed_count > 0 or format_fix_count > 0:
            removed_text = success(f"Removed {removed_count}") if removed_count > 0 else dim(f"Removed {removed_count}")
            fixed_text = success(f"Fixed {format_fix_count}") if format_fix_count > 0 else dim(f"Fixed {format_fix_count}")
            print(f"  {c(folder_name, Colors.BOLD_WHITE)}: {removed_text} keys, {fixed_text} format issues")
        else:
            print(f"  {dim(folder_name)}: {dim('No changes')}")
    
    print()
    if total_removed > 0 or total_format_fixes > 0:
        print(success(f"✅ Total: Removed {total_removed} keys, Fixed {total_format_fixes} format issues across all files"))
    else:
        print(info(f"ℹ️  No changes needed - all files are clean!"))
    print()
    
    # Step 5: Find commonly missing keys (missing from ALL variant files)
    print(header("=" * 60))
    print(header("  📋 COMMONLY MISSING KEYS REPORT"))
    print(header("=" * 60))
    print(f"Keys present in base {c('values/strings.xml', Colors.BOLD)} but missing from ALL variants:")
    print()
    
    # Get translatable keys from base
    translatable_base_keys = {k for k, (v, t) in base_keys.items() if t}
    
    # Start with all translatable keys as potentially missing from all
    commonly_missing = translatable_base_keys.copy()
    
    variant_count = 0
    for folder in values_folders:
        if folder.name == "values":
            continue  # Skip base folder
        
        variant_count += 1
        strings_file = folder / "strings.xml"
        variant_keys = parse_string_keys(strings_file)
        
        # Remove keys that exist in this variant from the commonly missing set
        commonly_missing -= set(variant_keys.keys())
    
    if commonly_missing:
        print(warning(f"⚠️  Found {len(commonly_missing)} keys missing from all {variant_count} variant files:"))
        for key in sorted(commonly_missing):
            print(f"    {dim('•')} {warning(key)}")
    else:
        print(success(f"✅ No keys are commonly missing from all {variant_count} variants!"))
    
    print()
    print(header("=" * 60))
    print(success("  ✨ Done!"))
    print(header("=" * 60))
    print()


if __name__ == "__main__":
    main()

#!/bin/bash

# ProjectOS Validation Script
# Run this to ensure all cross-references and file structures are valid.

echo "Running ProjectOS Validation..."
FAILED=0

# 1. Check for Stub Files (Excluding _meta and READMEs)
echo "1. Checking for stub files (< 15 lines)..."
STUBS=$(find . -type f -name "*.md" ! -path "*/_meta/*" ! -name "README.md" ! -name "INDEX.md" ! -name "pitfalls.md" ! -name "best-practices.md" -exec sh -c 'if [ $(wc -l < "{}") -lt 15 ]; then echo "{}"; fi' \;)

if [ -n "$STUBS" ]; then
  echo "❌ Error: Found stub files that need to be expanded:"
  echo "$STUBS"
  FAILED=1
else
  echo "✅ No stubs found."
fi

# 2. Check internal cross-references
echo "2. Checking internal cross-references..."
# Find all markdown links to .engineering files or local references
for file in $(find . -type f -name "*.md" ! -path "*/_meta/*"); do
  # Extract links looking like [text](path) or just raw file paths in backticks like `standards/api.md`
  refs=$(grep -oE '\b(standards|reviews|workflows|plugins|runtime|config)/[a-zA-Z0-9_-]+\.(md|yaml|yml)\b' "$file" || true)
  for ref in $refs; do
    if [ ! -f "$ref" ]; then
      echo "❌ Error: Broken reference '$ref' in $file"
      FAILED=1
    fi
  done
done

if [ $FAILED -eq 0 ]; then
  echo "✅ All references valid."
  echo "🚀 ProjectOS Validation Passed!"
  exit 0
else
  echo "💥 ProjectOS Validation Failed."
  exit 1
fi

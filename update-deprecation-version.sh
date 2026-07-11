#!/usr/bin/env bash
set -e

if [ -z "$NEW_VERSION" ]; then
  echo "Error: NEW_VERSION environment variable is not set."
  exit 1
fi

PLACEHOLDER="{DEPRECATION_VERSION}"
BASE_DIR="${PROJECT_PATH:-.}"

# Identify files containing the placeholder within the project path
# Exclude the script itself, .git, and target directories
FILES=$(grep -lR "$PLACEHOLDER" "$BASE_DIR" --exclude=update-deprecation-version.sh --exclude-dir=.git --exclude-dir=target || true)

if [ -z "$FILES" ]; then
  echo "No files containing placeholder $PLACEHOLDER found."
  exit 0
fi

if [ "$DRY_RUN" = "true" ]
then
  for FILE in $FILES; do
    echo "Dry run: replacement in $FILE"
    sed "s/$PLACEHOLDER/$NEW_VERSION/g" "$FILE"
  done
else
  for FILE in $FILES; do
    echo "Processing $FILE..."
    sed -i "s/$PLACEHOLDER/$NEW_VERSION/g" "$FILE"
    if [ "$GIT_STAGING" = "true" ]
    then
      git add "$FILE"
    fi
  done
  echo "Successfully updated deprecation version to $NEW_VERSION"
fi

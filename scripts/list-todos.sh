#!/bin/bash

# TODO List Generator for idp-server
# Usage: ./scripts/list-todos.sh [options]
#
# Options:
#   -f, --format FORMAT    Output format: text, json, csv, markdown (default: text)
#   -o, --output FILE      Output to file instead of stdout
#   -p, --priority         Include priority classification
#   -c, --count            Show count summary
#   -h, --help            Show this help message

set -euo pipefail

# Default values
FORMAT="text"
OUTPUT=""
SHOW_PRIORITY=false
SHOW_COUNT=false
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Colors for output
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

# Help function
show_help() {
    cat << EOF
TODO List Generator for idp-server

USAGE:
    $0 [OPTIONS]

OPTIONS:
    -f, --format FORMAT    Output format: text, json, csv, markdown (default: text)
    -o, --output FILE      Output to file instead of stdout
    -p, --priority         Include priority classification
    -c, --count            Show count summary
    -h, --help            Show this help message

EXAMPLES:
    $0                                    # Basic text output
    $0 -f markdown -o todos.md           # Markdown format to file
    $0 -p -c                            # With priority and count
    $0 -f json -o todos.json            # JSON format for CI/CD

PRIORITY LEVELS:
    A - Critical (Security, Bad Code)
    B - Important (Architecture, Logic)
    C - Medium (Configuration, Features)
    D - Low (Cleanup, Tests)
EOF
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -f|--format)
            FORMAT="$2"
            shift 2
            ;;
        -o|--output)
            OUTPUT="$2"
            shift 2
            ;;
        -p|--priority)
            SHOW_PRIORITY=true
            shift
            ;;
        -c|--count)
            SHOW_COUNT=true
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            show_help
            exit 1
            ;;
    esac
done

# Validate format
case $FORMAT in
    text|json|csv|markdown) ;;
    *)
        echo "Error: Invalid format '$FORMAT'. Use: text, json, csv, markdown"
        exit 1
        ;;
esac

# Function to classify priority
classify_priority() {
    local file="$1"
    local line="$2"
    local comment="$3"

    # Priority A: Critical (Security, Bad Code)
    if [[ "$comment" =~ (JWT|authentication|security|userinfo|"bad code") ]] || \
       [[ "$file" =~ (ClientAuthentication|UserinfoVerifier|ClientNotificationService|OAuthRequestResponse) ]]; then
        echo "A"
        return
    fi

    # Priority B: Important (Architecture, Logic)
    if [[ "$comment" =~ (refactor|reconsider|logic|"more correct") ]] || \
       [[ "$file" =~ (ModelConverter|JsonWebSignature|MfaCondition) ]]; then
        echo "B"
        return
    fi

    # Priority C: Medium (Configuration, Features)
    if [[ "$comment" =~ (config|dynamic|lifecycle|readable) ]] || \
       [[ "$file" =~ (CorsFilter|CibaIssue|IdentityVerification) ]]; then
        echo "C"
        return
    fi

    # Priority D: Low (Cleanup, Tests)
    echo "D"
}

# Function to get TODO description
get_description() {
    local comment="$1"
    # Extract meaningful description from comment - simple approach
    echo "$comment" | sed 's|^[[:space:]]*//[[:space:]]*TODO[[:space:]]*||' | \
                      sed 's|^[[:space:]]*//[[:space:]]*FIXME[[:space:]]*||' | \
                      sed 's|^[[:space:]]*//[[:space:]]*HACK[[:space:]]*||' | \
                      sed 's/^[[:space:]]*//' | sed 's/[[:space:]]*$//'
}

# Function to find all TODOs
find_todos() {
    local todos=()

    # Find in Java files
    while IFS= read -r line; do
        todos+=("$line")
    done < <(find "$PROJECT_ROOT" -type f -name "*.java" \
             -not -path "*/target/*" -not -path "*/build/*" -not -path "*/node_modules/*" \
             -exec grep -Hn "TODO\|FIXME\|HACK" {} \; 2>/dev/null || true)

    # Find in JavaScript files
    while IFS= read -r line; do
        todos+=("$line")
    done < <(find "$PROJECT_ROOT" -type f -name "*.js" \
             -not -path "*/node_modules/*" -not -path "*/target/*" -not -path "*/build/*" \
             -exec grep -Hn "TODO\|FIXME\|HACK" {} \; 2>/dev/null || true)

    # Find in TypeScript files
    while IFS= read -r line; do
        todos+=("$line")
    done < <(find "$PROJECT_ROOT" -type f -name "*.ts" \
             -not -path "*/node_modules/*" -not -path "*/target/*" -not -path "*/build/*" \
             -exec grep -Hn "TODO\|FIXME\|HACK" {} \; 2>/dev/null || true)

    # Find in Markdown files (excluding generated docs and dependencies)
    while IFS= read -r line; do
        todos+=("$line")
    done < <(find "$PROJECT_ROOT" -type f -name "*.md" \
             -not -path "*/target/*" -not -path "*/build/*" -not -path "*/node_modules/*" \
             -not -path "*/verifiable-credentials/*" \
             -exec grep -Hn "TODO\|FIXME\|HACK" {} \; 2>/dev/null || true)

    printf '%s\n' "${todos[@]}"
}

# Function to output in text format
output_text() {
    local count_a=0 count_b=0 count_c=0 count_d=0 total=0

    if [[ "$SHOW_COUNT" == true ]]; then
        echo -e "${BLUE}=== TODO撲滅作戦 - 調査結果 ===${NC}"
        echo
    fi

    while IFS=: read -r file line comment; do
        [[ -n "$file" ]] || continue

        total=$((total + 1))
        local relative_file="${file#$PROJECT_ROOT/}"
        local description=$(get_description "$comment")
        local priority=""

        if [[ "$SHOW_PRIORITY" == true ]]; then
            priority=$(classify_priority "$file" "$line" "$comment")
            case $priority in
                A) count_a=$((count_a + 1)); priority_color="$RED" ;;
                B) count_b=$((count_b + 1)); priority_color="$YELLOW" ;;
                C) count_c=$((count_c + 1)); priority_color="$GREEN" ;;
                D) count_d=$((count_d + 1)); priority_color="$BLUE" ;;
            esac
            printf "${priority_color}[%s]${NC} " "$priority"
        fi

        echo -e "${PURPLE}${relative_file}:${line}${NC} - ${description:-$comment}"

    done < <(find_todos)

    if [[ "$SHOW_COUNT" == true ]]; then
        echo
        echo -e "${BLUE}=== 統計情報 ===${NC}"
        echo -e "総計: $total 件"
        if [[ "$SHOW_PRIORITY" == true ]]; then
            echo -e "${RED}優先度A (Critical):${NC} $count_a 件"
            echo -e "${YELLOW}優先度B (Important):${NC} $count_b 件"
            echo -e "${GREEN}優先度C (Medium):${NC} $count_c 件"
            echo -e "${BLUE}優先度D (Low):${NC} $count_d 件"
        fi
    fi
}

# Function to output in JSON format
output_json() {
    echo "{"
    echo '  "generated_at": "'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'",'
    echo '  "project_root": "'$PROJECT_ROOT'",'
    echo '  "todos": ['

    local first=true
    while IFS=: read -r file line comment; do
        [[ -n "$file" ]] || continue

        if [[ "$first" == true ]]; then
            first=false
        else
            echo ","
        fi

        local relative_file="${file#$PROJECT_ROOT/}"
        local description=$(get_description "$comment")
        local priority=""

        if [[ "$SHOW_PRIORITY" == true ]]; then
            priority=$(classify_priority "$file" "$line" "$comment")
        fi

        echo -n '    {'
        echo -n '"file": "'$relative_file'", '
        echo -n '"line": '$line', '
        echo -n '"comment": "'$(echo "$comment" | sed 's/"/\\"/g')'", '
        echo -n '"description": "'$(echo "$description" | sed 's/"/\\"/g')'"'
        if [[ "$SHOW_PRIORITY" == true ]]; then
            echo -n ', "priority": "'$priority'"'
        fi
        echo -n '}'

    done < <(find_todos)

    echo
    echo '  ]'
    echo "}"
}

# Function to output in CSV format
output_csv() {
    if [[ "$SHOW_PRIORITY" == true ]]; then
        echo "file,line,comment,description,priority"
    else
        echo "file,line,comment,description"
    fi

    while IFS=: read -r file line comment; do
        [[ -n "$file" ]] || continue

        local relative_file="${file#$PROJECT_ROOT/}"
        local description=$(get_description "$comment")
        local priority=""

        if [[ "$SHOW_PRIORITY" == true ]]; then
            priority=$(classify_priority "$file" "$line" "$comment")
        fi

        # Escape CSV fields
        relative_file=$(echo "$relative_file" | sed 's/"/\"\"/g')
        comment=$(echo "$comment" | sed 's/"/\"\"/g')
        description=$(echo "$description" | sed 's/"/\"\"/g')

        if [[ "$SHOW_PRIORITY" == true ]]; then
            echo "\"$relative_file\",$line,\"$comment\",\"$description\",\"$priority\""
        else
            echo "\"$relative_file\",$line,\"$comment\",\"$description\""
        fi

    done < <(find_todos)
}

# Function to output in Markdown format
output_markdown() {
    echo "# TODO List - idp-server"
    echo
    echo "Generated: $(date)"
    echo

    if [[ "$SHOW_PRIORITY" == true ]]; then
        echo "## Priority Legend"
        echo "- 🔴 **A (Critical)**: Security, Bad Code - Immediate action required"
        echo "- 🟡 **B (Important)**: Architecture, Logic - Should be addressed soon"
        echo "- 🟢 **C (Medium)**: Configuration, Features - Can be planned"
        echo "- 🔵 **D (Low)**: Cleanup, Tests - Backlog items"
        echo

        # Group by priority
        for priority in A B C D; do
            local priority_name priority_emoji
            case $priority in
                A) priority_name="Critical"; priority_emoji="🔴" ;;
                B) priority_name="Important"; priority_emoji="🟡" ;;
                C) priority_name="Medium"; priority_emoji="🟢" ;;
                D) priority_name="Low"; priority_emoji="🔵" ;;
            esac

            echo "## ${priority_emoji} Priority $priority ($priority_name)"
            echo

            local found_items=false
            while IFS=: read -r file line comment; do
                [[ -n "$file" ]] || continue

                local item_priority=$(classify_priority "$file" "$line" "$comment")
                if [[ "$item_priority" == "$priority" ]]; then
                    if [[ "$found_items" == false ]]; then
                        found_items=true
                    fi

                    local relative_file="${file#$PROJECT_ROOT/}"
                    local description=$(get_description "$comment")

                    echo "- **$relative_file:$line** - ${description:-$comment}"
                fi

            done < <(find_todos)

            if [[ "$found_items" == false ]]; then
                echo "*(No items)*"
            fi
            echo
        done
    else
        echo "## All TODOs"
        echo
        echo "| File | Line | Description |"
        echo "|------|------|-------------|"

        while IFS=: read -r file line comment; do
            [[ -n "$file" ]] || continue

            local relative_file="${file#$PROJECT_ROOT/}"
            local description=$(get_description "$comment")

            echo "| $relative_file | $line | ${description:-$comment} |"

        done < <(find_todos)
    fi
}

# Main execution
main() {
    local output_content

    case $FORMAT in
        text)
            output_content=$(output_text)
            ;;
        json)
            output_content=$(output_json)
            ;;
        csv)
            output_content=$(output_csv)
            ;;
        markdown)
            output_content=$(output_markdown)
            ;;
    esac

    if [[ -n "$OUTPUT" ]]; then
        echo "$output_content" > "$OUTPUT"
        echo "TODO list saved to: $OUTPUT"
    else
        echo "$output_content"
    fi
}

main
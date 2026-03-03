#!/bin/bash

# ADB Logger Script with Terminal UI
# Reads adb logcat output and saves it to a log file with timestamp

# Colors for terminal UI
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$SCRIPT_DIR"

# Function to print header
print_header() {
    clear
    echo -e "${CYAN}${BOLD}╔════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}${BOLD}║${NC}          ${GREEN}${BOLD}ADB Logcat Logger${NC}          ${CYAN}${BOLD}║${NC}"
    echo -e "${CYAN}${BOLD}╚════════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

# Function to print status
print_status() {
    local status=$1
    local message=$2
    case $status in
        "info")
            echo -e "${BLUE}[INFO]${NC} $message"
            ;;
        "success")
            echo -e "${GREEN}[✓]${NC} $message"
            ;;
        "warning")
            echo -e "${YELLOW}[!]${NC} $message"
            ;;
        "error")
            echo -e "${RED}[✗]${NC} $message"
            ;;
    esac
}

# Function to check if adb is available
check_adb() {
    if ! command -v adb &> /dev/null; then
        print_status "error" "ADB is not installed or not in PATH"
        echo -e "${YELLOW}Please install Android Debug Bridge (adb) first.${NC}"
        exit 1
    fi
    print_status "success" "ADB found: $(which adb)"
}

# Function to check if device is connected
check_device() {
    print_status "info" "Checking for connected devices..."
    local devices=$(adb devices | grep -v "List" | grep "device$" | wc -l)
    
    if [ "$devices" -eq 0 ]; then
        print_status "error" "No Android device connected"
        echo -e "${YELLOW}Please connect a device via USB or enable ADB over WiFi.${NC}"
        echo ""
        echo -e "${CYAN}Connected devices:${NC}"
        adb devices
        exit 1
    fi
    
    print_status "success" "Found $devices connected device(s)"
    echo -e "${CYAN}Connected devices:${NC}"
    adb devices | grep -v "List"
    echo ""
}

# Function to generate log filename
generate_log_filename() {
    local timestamp=$(date +"%Y-%m-%d_%H-%M-%S")
    echo "adb_logcat_${timestamp}.log"
}

# Function to cleanup old log files
cleanup_old_logs() {
    local log_count=$(find "$LOG_DIR" -maxdepth 1 -name "*.log" -type f 2>/dev/null | wc -l)
    
    if [ "$log_count" -gt 0 ]; then
        print_status "info" "Cleaning up old log files..."
        find "$LOG_DIR" -maxdepth 1 -name "*.log" -type f -delete 2>/dev/null
        print_status "success" "Deleted $log_count old log file(s)"
    else
        print_status "info" "No old log files found"
    fi
}

# Function to clear old logcat buffer
clear_buffer() {
    print_status "info" "Clearing logcat buffer..."
    adb logcat -c
    print_status "success" "Buffer cleared"
}

# Function to show filter options
show_filter_menu() {
    echo -e "${CYAN}${BOLD}Filter Options:${NC}"
    echo -e "  ${GREEN}1)${NC} All logs (default)"
    echo -e "  ${GREEN}2)${NC} Errors only"
    echo -e "  ${GREEN}3)${NC} Warnings and Errors"
    echo -e "  ${GREEN}4)${NC} Custom filter"
    echo -e "  ${GREEN}5)${NC} Filter by package name"
    echo ""
    echo -n -e "${YELLOW}Select filter [1-5] (default: 1): ${NC}"
    read -r filter_choice
    
    case $filter_choice in
        2)
            FILTER="*:E"
            print_status "info" "Filter: Errors only"
            ;;
        3)
            FILTER="*:W"
            print_status "info" "Filter: Warnings and Errors"
            ;;
        4)
            echo -n -e "${YELLOW}Enter custom filter (e.g., '*:S MyApp:D'): ${NC}"
            read -r FILTER
            print_status "info" "Filter: $FILTER"
            ;;
        5)
            echo -n -e "${YELLOW}Enter package name (e.g., com.ble1st.connectias): ${NC}"
            read -r package_name
            FILTER="*:S ${package_name}:*"
            print_status "info" "Filter: Package $package_name"
            ;;
        *)
            FILTER="*:V"
            print_status "info" "Filter: All logs (verbose)"
            ;;
    esac
    echo ""
}

# Main function
main() {
    print_header
    
    # Cleanup old log files first
    cleanup_old_logs
    echo ""
    
    # Check prerequisites
    check_adb
    echo ""
    check_device
    
    # Generate log filename
    LOG_FILE="$LOG_DIR/$(generate_log_filename)"
    
    # Show filter options
    show_filter_menu
    
    # Ask about clearing buffer
    echo -n -e "${YELLOW}Clear logcat buffer before starting? [y/N]: ${NC}"
    read -r clear_buf
    if [[ "$clear_buf" =~ ^[Yy]$ ]]; then
        clear_buffer
        echo ""
    fi
    
    # Display start information
    print_header
    print_status "success" "Starting logcat capture..."
    echo ""
    echo -e "${CYAN}Log file:${NC} ${BOLD}$LOG_FILE${NC}"
    echo -e "${CYAN}Filter:${NC} ${BOLD}$FILTER${NC}"
    echo ""
    echo -e "${YELLOW}Press ${BOLD}Ctrl+C${NC}${YELLOW} to stop logging${NC}"
    echo ""
    echo -e "${CYAN}${BOLD}════════════════════════════════════════════════════════════${NC}"
    echo ""
    
    # Start logging with timestamp
    {
        echo "=========================================="
        echo "ADB Logcat Capture Started"
        echo "Date: $(date '+%Y-%m-%d %H:%M:%S')"
        echo "Filter: $FILTER"
        echo "Device: $(adb devices | grep 'device$' | head -1 | awk '{print $1}')"
        echo "=========================================="
        echo ""
    } >> "$LOG_FILE"
    
    # Start adb logcat and save to file
    # Use unbuffered output for real-time logging
    adb logcat -v time $FILTER 2>&1 | tee -a "$LOG_FILE"
    
    # Handle Ctrl+C gracefully
    trap 'echo ""; print_status "info" "Stopping logger..."; echo ""; exit 0' INT
    
    # Add end marker to log file
    {
        echo ""
        echo "=========================================="
        echo "ADB Logcat Capture Ended"
        echo "Date: $(date '+%Y-%m-%d %H:%M:%S')"
        echo "=========================================="
    } >> "$LOG_FILE"
    
    echo ""
    print_status "success" "Logging stopped"
    print_status "info" "Log saved to: $LOG_FILE"
    echo ""
}

# Run main function
main

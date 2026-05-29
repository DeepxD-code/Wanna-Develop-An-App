// AI Architect — Tauri Desktop App Entry Point
// Tauri v2: main.rs is the binary entry, lib.rs exposes the library.

#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    ai_architect_lib::run();
}

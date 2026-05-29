// AI Architect — Tauri Library
// All app logic lives here. main.rs just calls run().

use std::process::{Child, Command};
use std::sync::Mutex;
use tauri::{AppHandle, Manager, State};

struct BackendProcesses {
    java:   Mutex<Option<Child>>,
    python: Mutex<Option<Child>>,
    redis:  Mutex<Option<Child>>,
}

#[tauri::command]
fn start_backends(
    app: AppHandle,
    state: State<'_, BackendProcesses>,
) -> Result<String, String> {
    let resource_dir = app.path().resource_dir().map_err(|e| e.to_string())?;
    let mut started  = vec![];

    // 1. Redis
    if start_redis(&state, &resource_dir) { started.push("redis"); }

    // Wait for redis
    std::thread::sleep(std::time::Duration::from_millis(1200));

    // 2. Java backend
    if start_java(&state, &resource_dir) { started.push("java"); }

    // 3. Python worker
    if start_python(&state, &resource_dir) { started.push("python"); }

    Ok(started.join(","))
}

#[tauri::command]
fn stop_backends(state: State<'_, BackendProcesses>) -> Result<(), String> {
    kill(&state.java);
    kill(&state.python);
    kill(&state.redis);
    Ok(())
}

#[tauri::command]
fn get_status(state: State<'_, BackendProcesses>) -> serde_json::Value {
    serde_json::json!({
        "java":   alive(&state.java),
        "python": alive(&state.python),
        "redis":  alive(&state.redis),
    })
}

fn start_redis(state: &State<BackendProcesses>, res: &std::path::Path) -> bool {
    let mut g = state.redis.lock().unwrap();
    if g.is_some() { return true; }

    // Try Docker first
    if let Ok(c) = Command::new("docker")
        .args(["compose", "-f"])
        .arg(res.join("backend/docker-compose.yml"))
        .args(["up", "-d", "redis"])
        .spawn() {
        *g = Some(c); return true;
    }

    // Try system redis-server
    if let Ok(c) = Command::new("redis-server").spawn() {
        *g = Some(c); return true;
    }

    false
}

fn start_java(state: &State<BackendProcesses>, res: &std::path::Path) -> bool {
    let mut g   = state.java.lock().unwrap();
    if g.is_some() { return true; }

    let jar = res.join("backend/ai-architect-backend.jar");
    if !jar.exists() { return false; }

    let env_file = res.join("backend/.env");
    let mut cmd  = Command::new("java");
    cmd.arg("-jar").arg(&jar).arg("--server.port=8080");

    if env_file.exists() {
        if let Ok(content) = std::fs::read_to_string(&env_file) {
            for line in content.lines() {
                let line = line.trim();
                if line.is_empty() || line.starts_with('#') { continue; }
                if let Some((k, v)) = line.split_once('=') {
                    cmd.env(k.trim(), v.trim());
                }
            }
        }
    }

    if let Ok(c) = cmd.spawn() { *g = Some(c); true } else { false }
}

fn start_python(state: &State<BackendProcesses>, res: &std::path::Path) -> bool {
    let mut g      = state.python.lock().unwrap();
    if g.is_some() { return true; }

    let worker_dir = res.join("python-worker");
    let main_py    = worker_dir.join("main.py");
    if !main_py.exists() { return false; }

    // Try bundled venv first, then system python
    let python_paths = if cfg!(windows) {
        vec![
            worker_dir.join(".venv/Scripts/python.exe"),
            std::path::PathBuf::from("python"),
        ]
    } else {
        vec![
            worker_dir.join(".venv/bin/python"),
            std::path::PathBuf::from("python3"),
            std::path::PathBuf::from("python"),
        ]
    };

    for python in python_paths {
        if let Ok(c) = Command::new(&python)
            .arg(&main_py)
            .current_dir(&worker_dir)
            .spawn()
        {
            *g = Some(c);
            return true;
        }
    }
    false
}

fn kill(guard: &Mutex<Option<Child>>) {
    if let Ok(mut g) = guard.lock() {
        if let Some(mut c) = g.take() { let _ = c.kill(); }
    }
}

fn alive(guard: &Mutex<Option<Child>>) -> bool {
    if let Ok(mut g) = guard.lock() {
        if let Some(c) = g.as_mut() {
            return c.try_wait().map(|s| s.is_none()).unwrap_or(false);
        }
    }
    false
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_fs::init())
        .plugin(tauri_plugin_process::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .manage(BackendProcesses {
            java:   Mutex::new(None),
            python: Mutex::new(None),
            redis:  Mutex::new(None),
        })
        .invoke_handler(tauri::generate_handler![
            start_backends,
            stop_backends,
            get_status,
        ])
        .setup(|app| {
            let handle = app.handle().clone();
            tauri::async_runtime::spawn(async move {
                tokio::time::sleep(tokio::time::Duration::from_millis(1500)).await;
                let state: State<BackendProcesses> = handle.state();
                if let Ok(res) = handle.path().resource_dir() {
                    start_redis(&state, &res);
                    std::thread::sleep(std::time::Duration::from_millis(1200));
                    start_java(&state, &res);
                    start_python(&state, &res);
                }
            });
            Ok(())
        })
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::CloseRequested { .. } = event {
                let state: State<BackendProcesses> = window.state();
                kill(&state.java);
                kill(&state.python);
                kill(&state.redis);
            }
        })
        .run(tauri::generate_context!())
        .expect("error running AI Architect");
}

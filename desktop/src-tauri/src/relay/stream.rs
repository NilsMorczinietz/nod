//! Live connection to `GET /v1/stream`. Runs for the whole app lifetime,
//! follows the credentials channel and reconnects with exponential backoff.
use std::time::Duration;

use futures_util::{SinkExt, StreamExt};
use tauri::{AppHandle, Emitter, Manager};
use tokio::sync::watch;
use tokio::time::{Instant, interval, sleep, timeout};
use tokio_tungstenite::tungstenite::client::IntoClientRequest;
use tokio_tungstenite::tungstenite::http::{HeaderValue, StatusCode, header::AUTHORIZATION};
use tokio_tungstenite::tungstenite::protocol::frame::coding::CloseCode;
use tokio_tungstenite::tungstenite::{Error as WsError, Message};

use super::client::Credentials;
use super::types::StreamMessage;
use crate::state::{AppState, ConnectionStatus, EVENT_MESSAGE, EVENT_STATUS};
use crate::{overlay, presence, session, tray};

const PING_EVERY: Duration = Duration::from_secs(30);
/// How often we check whether the user is at the PC (see `presence`).
const PRESENCE_EVERY: Duration = Duration::from_secs(15);
/// No traffic (not even a pong) for this long means the connection is dead.
const SILENCE_LIMIT: Duration = Duration::from_secs(75);
const CONNECT_TIMEOUT: Duration = Duration::from_secs(15);
const MAX_BACKOFF: Duration = Duration::from_secs(60);
/// Close code the relay uses when this device was revoked.
const CLOSE_REVOKED: u16 = 4001;

enum Ended {
    /// The device token is no longer valid; pairing must be removed.
    Revoked,
    /// Anything else: reconnect after a backoff.
    Dropped,
}

pub fn spawn(app: AppHandle, mut credentials: watch::Receiver<Option<Credentials>>) {
    tauri::async_runtime::spawn(async move {
        let mut attempt: u32 = 0;
        loop {
            let Some(creds) = credentials.borrow_and_update().clone() else {
                set_status(&app, ConnectionStatus::Unpaired);
                if credentials.changed().await.is_err() {
                    return;
                }
                continue;
            };

            set_status(&app, ConnectionStatus::Connecting);
            let ended = tokio::select! {
                ended = connect_and_run(&app, &creds, &mut attempt) => ended,
                changed = credentials.changed() => {
                    if changed.is_err() { return; }
                    attempt = 0;
                    continue;
                }
            };

            if let Ended::Revoked = ended {
                crate::logger::write("rust", "device token rejected by the relay; unpairing");
                let _ = session::forget(&app, false).await;
                continue;
            }
            set_status(&app, ConnectionStatus::Offline);
            let delay = backoff(attempt);
            attempt = attempt.saturating_add(1);
            tokio::select! {
                () = sleep(delay) => {}
                changed = credentials.changed() => {
                    if changed.is_err() { return; }
                    attempt = 0;
                }
            }
        }
    });
}

/// 1 s, 2 s, 4 s … capped at 60 s, with ±25 % jitter to avoid thundering herds.
fn backoff(attempt: u32) -> Duration {
    let base = Duration::from_secs(1u64 << attempt.min(6)).min(MAX_BACKOFF);
    base.mul_f64(0.75 + fastrand::f64() * 0.5)
}

async fn connect_and_run(app: &AppHandle, creds: &Credentials, attempt: &mut u32) -> Ended {
    let mut url = creds.relay.join("/v1/stream").expect("static path");
    let scheme = if url.scheme() == "https" { "wss" } else { "ws" };
    url.set_scheme(scheme).expect("ws scheme");

    let Ok(mut request) = url.as_str().into_client_request() else {
        return Ended::Dropped;
    };
    let Ok(auth) = HeaderValue::from_str(&format!("Bearer {}", creds.token)) else {
        return Ended::Revoked;
    };
    request.headers_mut().insert(AUTHORIZATION, auth);

    let socket = match timeout(CONNECT_TIMEOUT, tokio_tungstenite::connect_async(request)).await {
        Ok(Ok((socket, _))) => socket,
        Ok(Err(WsError::Http(response)))
            if matches!(
                response.status(),
                StatusCode::UNAUTHORIZED | StatusCode::FORBIDDEN
            ) =>
        {
            return Ended::Revoked;
        }
        _ => return Ended::Dropped,
    };

    let (mut sink, mut stream) = socket.split();
    let mut ping = interval(PING_EVERY);
    let mut presence_check = interval(PRESENCE_EVERY);
    let mut last_traffic = Instant::now();
    // Report presence right after (re)connecting, then on every change.
    let mut reported_active: Option<bool> = None;

    loop {
        tokio::select! {
            _ = ping.tick() => {
                if last_traffic.elapsed() > SILENCE_LIMIT || sink.send(Message::text("ping")).await.is_err() {
                    return Ended::Dropped;
                }
            }
            _ = presence_check.tick() => {
                let active = presence::user_active();
                if reported_active != Some(active) {
                    let message = serde_json::json!({ "type": "presence", "active": active });
                    if sink.send(Message::text(message.to_string())).await.is_err() {
                        return Ended::Dropped;
                    }
                    reported_active = Some(active);
                }
            }
            frame = stream.next() => {
                last_traffic = Instant::now();
                match frame {
                    Some(Ok(Message::Text(text))) => {
                        if text.as_str() == "pong" { continue; }
                        if let Ok(message) = serde_json::from_str::<StreamMessage>(text.as_str()) {
                            if matches!(message, StreamMessage::Hello { .. }) {
                                *attempt = 0;
                            }
                            apply(app, message);
                        }
                    }
                    Some(Ok(Message::Close(frame))) => {
                        let revoked = frame.is_some_and(|f| f.code == CloseCode::from(CLOSE_REVOKED));
                        return if revoked { Ended::Revoked } else { Ended::Dropped };
                    }
                    Some(Ok(_)) => {}
                    Some(Err(_)) | None => return Ended::Dropped,
                }
            }
        }
    }
}

fn set_status(app: &AppHandle, status: ConnectionStatus) {
    let state = app.state::<AppState>();
    {
        let mut hub = state.hub.lock().expect("hub lock");
        if hub.status == status {
            return;
        }
        hub.status = status;
    }
    tray::refresh(app);
    let _ = app.emit(EVENT_STATUS, status);
}

/// Folds a stream message into the shared state, then notifies windows and tray.
fn apply(app: &AppHandle, message: StreamMessage) {
    let state = app.state::<AppState>();
    let reveal = {
        let mut hub = state.hub.lock().expect("hub lock");
        match &message {
            StreamMessage::Hello {
                pending, settings, ..
            } => {
                hub.status = ConnectionStatus::Online;
                hub.pending = pending.clone();
                hub.settings = *settings;
                !settings.paused && !pending.is_empty()
            }
            StreamMessage::Item { item } => {
                if item.is_pending() {
                    hub.pending.push(item.clone());
                }
                !hub.settings.paused
            }
            StreamMessage::Update { item } => {
                hub.pending.retain(|pending| pending.id != item.id);
                if item.is_pending() {
                    hub.pending.push(item.clone());
                }
                false
            }
            StreamMessage::Settings { settings } => {
                hub.settings = *settings;
                false
            }
        }
    };
    if matches!(message, StreamMessage::Hello { .. }) {
        let _ = app.emit(EVENT_STATUS, ConnectionStatus::Online);
    }
    tray::refresh(app);
    let _ = app.emit(EVENT_MESSAGE, &message);
    if reveal {
        overlay::reveal(app);
    }
}

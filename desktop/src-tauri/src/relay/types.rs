//! Serde mirror of `packages/protocol`. Items keep unknown fields in `rest`
//! so they reach the frontend unchanged.
use std::collections::HashMap;

use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};
use url::Url;

use crate::error::{Error, Result};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Item {
    pub id: String,
    pub kind: String,
    pub status: String,
    pub title: String,
    #[serde(flatten)]
    pub rest: Map<String, Value>,
}

impl Item {
    pub fn is_pending(&self) -> bool {
        self.status == "pending"
    }
}

/// Where requests go (see `RoutingMode` in packages/protocol).
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum RoutingMode {
    /// Desktop always; the phone only while nobody is at a desktop.
    #[default]
    Away,
    /// Desktop and phone.
    Always,
    /// Only informational items; requests stay in the terminal.
    Updates,
    /// Nothing is delivered (paused).
    Terminal,
}

#[derive(Debug, Clone, Copy, Default, Serialize, Deserialize)]
pub struct HubSettings {
    #[serde(default)]
    pub mode: RoutingMode,
    pub paused: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "lowercase")]
pub enum StreamMessage {
    Hello {
        protocol: u32,
        pending: Vec<Item>,
        settings: HubSettings,
    },
    Item {
        item: Item,
    },
    Update {
        item: Item,
    },
    Settings {
        settings: HubSettings,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ResolveInput {
    pub decision: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub always_allow: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub answers: Option<HashMap<String, Vec<String>>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub message: Option<String>,
}

#[derive(Debug, Deserialize)]
pub struct RegisteredDevice {
    pub id: String,
    pub name: String,
}

#[derive(Debug, Deserialize)]
pub struct RegisterDeviceResponse {
    pub device: RegisteredDevice,
    pub token: String,
}

/// Parsed `nod://pair?relay=…&code=…` link.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PairingLink {
    pub relay: Url,
    pub code: String,
}

impl PairingLink {
    /// Accepts a full pairing link, or a relay URL plus a separately typed code.
    pub fn parse(link_or_relay: &str, code: Option<&str>) -> Result<Self> {
        let input = link_or_relay.trim();
        let (relay, code) = match Url::parse(input) {
            Ok(url) if url.scheme() == "nod" => {
                let param = |key: &str| {
                    url.query_pairs()
                        .find(|(k, _)| k == key)
                        .map(|(_, v)| v.into_owned())
                };
                let relay = param("relay").ok_or_else(|| invalid("missing relay"))?;
                let code = code
                    .map(str::to_owned)
                    .or_else(|| param("code"))
                    .ok_or_else(|| invalid("missing code"))?;
                (relay, code)
            }
            _ => (
                input.to_owned(),
                code.map(str::to_owned)
                    .ok_or_else(|| invalid("missing code"))?,
            ),
        };
        let relay = validate_relay_url(&relay)?;
        let code = code.trim().to_owned();
        if code.is_empty() {
            return Err(invalid("missing code"));
        }
        Ok(Self { relay, code })
    }
}

fn invalid(reason: &str) -> Error {
    Error::InvalidLink(reason.to_owned())
}

/// Only HTTPS is allowed, except for a relay running on this machine.
pub fn validate_relay_url(raw: &str) -> Result<Url> {
    let mut url = Url::parse(raw.trim()).map_err(|_| invalid("relay is not a URL"))?;
    let local = matches!(url.host_str(), Some("localhost" | "127.0.0.1"));
    match url.scheme() {
        "https" => {}
        "http" if local => {}
        _ => return Err(invalid("relay must use https://")),
    }
    url.set_path("");
    url.set_query(None);
    url.set_fragment(None);
    Ok(url)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_pairing_link() {
        let link = PairingLink::parse(
            "nod://pair?relay=https%3A%2F%2Fnod.example.workers.dev&code=K7QM-4XPA",
            None,
        )
        .unwrap();
        assert_eq!(link.relay.as_str(), "https://nod.example.workers.dev/");
        assert_eq!(link.code, "K7QM-4XPA");
    }

    #[test]
    fn rejects_plain_http_for_remote_hosts() {
        assert!(PairingLink::parse("http://example.com", Some("AAAA-BBBB")).is_err());
        assert!(PairingLink::parse("http://127.0.0.1:8787", Some("AAAA-BBBB")).is_ok());
    }
}

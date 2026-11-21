//! HTTPS calls to the relay (everything except the live stream).
use std::time::Duration;

use reqwest::{Method, RequestBuilder, Response, StatusCode};
use serde::{Deserialize, Serialize};
use serde_json::json;
use url::Url;

use super::types::{
    HubSettings, Item, PairingLink, RegisterDeviceResponse, ResolveInput, RoutingMode,
};
use crate::error::{Error, Result};

/// Credentials of a paired device.
#[derive(Clone)]
pub struct Credentials {
    pub relay: Url,
    pub token: String,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ResolveOutcome {
    pub item: Item,
    /// True when another device (or the terminal) answered first.
    pub already_finished: bool,
}

#[derive(Deserialize)]
struct ErrorBody {
    error: Option<String>,
    message: Option<String>,
    item: Option<Item>,
}

#[derive(Clone)]
pub struct RelayClient {
    http: reqwest::Client,
}

impl RelayClient {
    pub fn new() -> Result<Self> {
        let http = reqwest::Client::builder()
            .connect_timeout(Duration::from_secs(10))
            .timeout(Duration::from_secs(20))
            .user_agent(concat!("nod-desktop/", env!("CARGO_PKG_VERSION")))
            .build()?;
        Ok(Self { http })
    }

    fn request(&self, creds: &Credentials, method: Method, path: &str) -> RequestBuilder {
        let url = creds.relay.join(path).expect("static API path");
        self.http.request(method, url).bearer_auth(&creds.token)
    }

    async fn error_from(response: Response) -> Error {
        let status = response.status().as_u16();
        let body: Option<ErrorBody> = response.json().await.ok();
        let (code, message) = body.map(|b| (b.error, b.message)).unwrap_or_default();
        Error::Relay {
            status,
            code: code.unwrap_or_else(|| "http_error".into()),
            message: message.unwrap_or_else(|| format!("Relay answered with HTTP {status}")),
        }
    }

    async fn json<T: for<'de> Deserialize<'de>>(response: Response) -> Result<T> {
        if response.status().is_success() {
            Ok(response.json().await?)
        } else {
            Err(Self::error_from(response).await)
        }
    }

    pub async fn register(&self, link: &PairingLink, name: &str) -> Result<RegisterDeviceResponse> {
        let url = link.relay.join("/v1/devices").expect("static API path");
        let response = self
            .http
            .post(url)
            .json(&json!({ "code": link.code, "name": name, "kind": "desktop" }))
            .send()
            .await?;
        Self::json(response).await
    }

    pub async fn resolve(
        &self,
        creds: &Credentials,
        id: &str,
        input: &ResolveInput,
    ) -> Result<ResolveOutcome> {
        let path = format!("/v1/items/{}/resolve", urlencode(id));
        let response = self
            .request(creds, Method::POST, &path)
            .json(input)
            .send()
            .await?;
        if response.status() == StatusCode::CONFLICT {
            let body: ErrorBody = response.json().await?;
            let item = body.item.ok_or_else(|| Error::Relay {
                status: 409,
                code: "already_finished".into(),
                message: "Already answered".into(),
            })?;
            return Ok(ResolveOutcome {
                item,
                already_finished: true,
            });
        }
        let item = Self::json(response).await?;
        Ok(ResolveOutcome {
            item,
            already_finished: false,
        })
    }

    pub async fn list_items(&self, creds: &Credentials, limit: u32) -> Result<Vec<Item>> {
        #[derive(Deserialize)]
        struct Items {
            items: Vec<Item>,
        }
        let path = format!("/v1/items?limit={}", limit.clamp(1, 100));
        let response = self.request(creds, Method::GET, &path).send().await?;
        Ok(Self::json::<Items>(response).await?.items)
    }

    pub async fn set_paused(&self, creds: &Credentials, paused: bool) -> Result<HubSettings> {
        let response = self
            .request(creds, Method::PATCH, "/v1/settings")
            .json(&json!({ "paused": paused }))
            .send()
            .await?;
        Self::json(response).await
    }

    pub async fn set_mode(&self, creds: &Credentials, mode: RoutingMode) -> Result<HubSettings> {
        let response = self
            .request(creds, Method::PATCH, "/v1/settings")
            .json(&json!({ "mode": mode }))
            .send()
            .await?;
        Self::json(response).await
    }

    /// Removes this device from the relay. A missing device counts as success.
    pub async fn unpair(&self, creds: &Credentials) -> Result<()> {
        let response = self.request(creds, Method::DELETE, "/v1/me").send().await?;
        match response.status() {
            s if s.is_success() || s == StatusCode::UNAUTHORIZED => Ok(()),
            _ => Err(Self::error_from(response).await),
        }
    }
}

/// Percent-encodes a path segment (ids are UUIDs, this is defence in depth).
fn urlencode(segment: &str) -> String {
    url::form_urlencoded::byte_serialize(segment.as_bytes()).collect()
}

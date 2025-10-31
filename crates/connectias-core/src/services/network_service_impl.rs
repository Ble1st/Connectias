use std::sync::Arc;
use std::collections::HashMap;
use connectias_api::{NetworkService, NetworkRequest, NetworkResponse, PluginError};
use connectias_security::network::NetworkSecurityFilter;
use connectias_security::sandbox::ResourceQuotaManager;
use reqwest::Client;

pub struct NetworkServiceImpl {
    plugin_id: String,
    http_client: Arc<Client>,
    security_filter: Arc<NetworkSecurityFilter>,
    quota_manager: Arc<ResourceQuotaManager>,
}

impl NetworkServiceImpl {
    pub fn new(
        plugin_id: String,
        http_client: Arc<Client>,
        security_filter: Arc<NetworkSecurityFilter>,
        quota_manager: Arc<ResourceQuotaManager>,
    ) -> Self {
        Self {
            plugin_id,
            http_client,
            security_filter,
            quota_manager,
        }
    }

    fn parse_method(&self, method: &str) -> reqwest::Method {
        match method.to_uppercase().as_str() {
            "GET" => reqwest::Method::GET,
            "POST" => reqwest::Method::POST,
            "PUT" => reqwest::Method::PUT,
            "DELETE" => reqwest::Method::DELETE,
            "PATCH" => reqwest::Method::PATCH,
            "HEAD" => reqwest::Method::HEAD,
            "OPTIONS" => reqwest::Method::OPTIONS,
            _ => reqwest::Method::GET,
        }
    }

    fn build_headers(&self, headers: &HashMap<String, String>) -> reqwest::header::HeaderMap {
        let mut header_map = reqwest::header::HeaderMap::new();
        let mut skipped_headers = 0;
        
        for (key, value) in headers {
            // Versuche Header-Name zu parsen
            let header_name_result = reqwest::header::HeaderName::from_bytes(key.as_bytes());
            let header_value_result = reqwest::header::HeaderValue::from_str(value);
            
            match (header_name_result, header_value_result) {
                (Ok(header_name), Ok(header_value)) => {
                    header_map.insert(header_name, header_value);
                }
                (Err(name_err), _) => {
                    log::warn!("Ungültiger Header-Name '{}': {}", key, name_err);
                    skipped_headers += 1;
                }
                (_, Err(value_err)) => {
                    log::warn!("Ungültiger Header-Wert für '{}': {}", key, value_err);
                    skipped_headers += 1;
                }
            }
        }
        
        if skipped_headers > 0 {
            log::warn!("{} ungültige Header wurden übersprungen", skipped_headers);
        }
        
        header_map
    }

    fn extract_headers(&self, response: &reqwest::Response) -> HashMap<String, String> {
        let mut headers = HashMap::new();
        for (key, value) in response.headers() {
            if let Ok(value_str) = value.to_str() {
                headers.insert(key.to_string(), value_str.to_string());
            }
        }
        headers
    }
}

impl NetworkService for NetworkServiceImpl {
    fn request(&self, req: NetworkRequest) -> Result<NetworkResponse, PluginError> {
        // 1. Security check
        self.security_filter.validate_url(&req.url)
            .map_err(|e| PluginError::ExecutionFailed(format!("Security validation failed: {}", e)))?;

        // 2. Quota check
        self.quota_manager.register_network_request(&self.plugin_id)
            .map_err(|e| PluginError::ExecutionFailed(format!("Quota exceeded: {}", e)))?;

        // 3. Execute request - FIX BUG: block_on() Deadlock-Schutz
        // Prüfe ob wir bereits in async Runtime sind - wenn ja, verwende block_in_place
        let http_client = self.http_client.clone();
        let method = self.parse_method(&req.method);
        let url = req.url.clone();
        let headers = self.build_headers(&req.headers);
        let body = req.body.unwrap_or_default();
        
        let (status_code, headers_map, body_bytes) = if let Ok(handle) = tokio::runtime::Handle::try_current() {
            // Wir sind in async Runtime - verwende block_in_place um Deadlock zu vermeiden
            tokio::task::block_in_place(|| {
                handle.block_on(async {
                    match http_client
                        .request(method, &url)
                        .headers(headers)
                        .body(body)
                        .send()
                        .await
                    {
                        Ok(response) => {
                            let status = response.status().as_u16();
                            let headers = response.headers().iter()
                                .map(|(k, v)| (k.to_string(), v.to_str().unwrap_or("").to_string()))
                                .collect();
                            match response.bytes().await {
                                Ok(bytes) => Ok((status, headers, bytes.to_vec())),
                                Err(e) => Err(PluginError::ExecutionFailed(format!("Failed to read response: {}", e))),
                            }
                        }
                        Err(e) => Err(PluginError::ExecutionFailed(format!("Request failed: {}", e))),
                    }
                })
            })
        } else {
            // Wir sind nicht in async Runtime - block_on ist sicher
            let rt = tokio::runtime::Handle::current();
            rt.block_on(async {
                match http_client
                    .request(method, &url)
                    .headers(headers)
                    .body(body)
                    .send()
                    .await
                {
                    Ok(response) => {
                        let status = response.status().as_u16();
                        let headers = response.headers().iter()
                            .map(|(k, v)| (k.to_string(), v.to_str().unwrap_or("").to_string()))
                            .collect();
                        match response.bytes().await {
                            Ok(bytes) => Ok((status, headers, bytes.to_vec())),
                            Err(e) => Err(PluginError::ExecutionFailed(format!("Failed to read response: {}", e))),
                        }
                    }
                    Err(e) => Err(PluginError::ExecutionFailed(format!("Request failed: {}", e))),
                }
            })
        }?;

        Ok(NetworkResponse {
            status_code,
            headers,
            body: body_bytes,
        })
    }
}

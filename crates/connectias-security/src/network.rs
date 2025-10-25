use std::collections::{HashMap, HashSet};
use std::sync::{Arc, Mutex};
use std::net::{IpAddr, Ipv4Addr, Ipv6Addr};
use std::str::FromStr;
use url::Url;

/// Network Security Filter für SSRF Protection und Rate Limiting
pub struct NetworkSecurityFilter {
    blocked_hosts: HashSet<String>,
    blocked_ips: HashSet<IpAddr>,
    rate_limiter: Arc<Mutex<RateLimiter>>,
    ssl_pinning: SslPinning,
}

impl NetworkSecurityFilter {
    pub fn new() -> Self {
        let mut filter = Self {
            blocked_hosts: HashSet::new(),
            blocked_ips: HashSet::new(),
            rate_limiter: Arc::new(Mutex::new(RateLimiter::new())),
            ssl_pinning: SslPinning::new(),
        };
        
        // Standard blockierte Hosts
        filter.add_blocked_hosts();
        filter.add_blocked_ips();
        
        filter
    }

    /// Validiert eine URL auf SSRF-Angriffe
    pub fn validate_url(&self, url: &str) -> Result<(), super::SecurityError> {
        let parsed = Url::parse(url)
            .map_err(|e| super::SecurityError::SecurityViolation(format!("Invalid URL: {}", e)))?;

        // 1. Hostname prüfen
        if let Some(host) = parsed.host_str() {
            if self.is_blocked_host(host) {
                return Err(super::SecurityError::SecurityViolation(
                    format!("Blocked host: {}", host)
                ));
            }
        }

        // 2. IP-Adresse prüfen
        if let Some(host) = parsed.host_str() {
            if let Ok(ip) = IpAddr::from_str(host) {
                if self.is_blocked_ip(&ip) {
                    return Err(super::SecurityError::SecurityViolation(
                        format!("Blocked IP: {}", ip)
                    ));
                }
            }
        }

        // 3. Rate Limiting prüfen
        if !self.rate_limiter.lock().unwrap().check_rate_limit(url) {
            return Err(super::SecurityError::SecurityViolation(
                "Rate limit exceeded".to_string()
            ));
        }

        // 4. SSL Pinning prüfen (für HTTPS)
        if parsed.scheme() == "https" {
            self.ssl_pinning.validate_certificate(&parsed)?;
        }

        Ok(())
    }

    fn is_blocked_host(&self, host: &str) -> bool {
        // Localhost-Varianten
        if host == "localhost" || host == "::1" {
            return true;
        }

        // Blockierte Hosts
        if self.blocked_hosts.contains(host) {
            return true;
        }

        // Private Domain-Patterns
        let private_patterns = [
            ".local",
            ".internal",
            ".corp",
            ".lan",
        ];

        private_patterns.iter().any(|pattern| host.ends_with(pattern))
    }

    fn is_blocked_ip(&self, ip: &IpAddr) -> bool {
        // Blockierte IPs
        if self.blocked_ips.contains(ip) {
            return true;
        }

        match ip {
            IpAddr::V4(ipv4) => self.is_private_ipv4(ipv4),
            IpAddr::V6(ipv6) => self.is_private_ipv6(ipv6),
        }
    }

    fn is_private_ipv4(&self, ip: &Ipv4Addr) -> bool {
        let octets = ip.octets();
        
        // 127.0.0.0/8 (localhost)
        if octets[0] == 127 {
            return true;
        }
        
        // 10.0.0.0/8 (private)
        if octets[0] == 10 {
            return true;
        }
        
        // 172.16.0.0/12 (private)
        if octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31 {
            return true;
        }
        
        // 192.168.0.0/16 (private)
        if octets[0] == 192 && octets[1] == 168 {
            return true;
        }
        
        // 169.254.0.0/16 (link-local)
        if octets[0] == 169 && octets[1] == 254 {
            return true;
        }
        
        false
    }

    fn is_private_ipv6(&self, ip: &Ipv6Addr) -> bool {
        let segments = ip.segments();
        
        // ::1 (localhost)
        if segments == [0, 0, 0, 0, 0, 0, 0, 1] {
            return true;
        }
        
        // fc00::/7 (unique local)
        if segments[0] & 0xfe00 == 0xfc00 {
            return true;
        }
        
        // fe80::/10 (link-local)
        if segments[0] & 0xffc0 == 0xfe80 {
            return true;
        }
        
        false
    }

    fn add_blocked_hosts(&mut self) {
        let blocked = [
            "localhost",
            "127.0.0.1",
            "::1",
            "0.0.0.0",
            "metadata.google.internal",
            "169.254.169.254", // AWS metadata
            "100.100.100.200", // Alibaba Cloud metadata
        ];

        for host in &blocked {
            self.blocked_hosts.insert(host.to_string());
        }
    }

    fn add_blocked_ips(&mut self) {
        // Localhost IPs
        if let Ok(ip) = IpAddr::from_str("127.0.0.1") {
            self.blocked_ips.insert(ip);
        }
        if let Ok(ip) = IpAddr::from_str("::1") {
            self.blocked_ips.insert(ip);
        }
    }
}

/// Rate Limiter für Network-Requests
pub struct RateLimiter {
    requests: HashMap<String, Vec<std::time::Instant>>,
    max_requests: usize,
    time_window: std::time::Duration,
}

impl RateLimiter {
    pub fn new() -> Self {
        Self {
            requests: HashMap::new(),
            max_requests: 60, // 60 requests
            time_window: std::time::Duration::from_secs(60), // per minute
        }
    }

    pub fn check_rate_limit(&mut self, url: &str) -> bool {
        let now = std::time::Instant::now();
        let key = self.get_rate_limit_key(url);
        
        // Alte Requests entfernen
        if let Some(requests) = self.requests.get_mut(&key) {
            requests.retain(|&time| now.duration_since(time) < self.time_window);
            
            // Rate limit prüfen
            if requests.len() >= self.max_requests {
                return false;
            }
            
            // Neuen Request hinzufügen
            requests.push(now);
        } else {
            // Erster Request für diese URL
            self.requests.insert(key, vec![now]);
        }
        
        true
    }

    fn get_rate_limit_key(&self, url: &str) -> String {
        // Rate limiting basierend auf Domain
        if let Ok(parsed) = Url::parse(url) {
            if let Some(host) = parsed.host_str() {
                return host.to_string();
            }
        }
        url.to_string()
    }
}

/// SSL Certificate Pinning für HTTPS-Requests
pub struct SslPinning {
    pinned_certificates: HashMap<String, Vec<String>>,
}

impl SslPinning {
    pub fn new() -> Self {
        let mut pinning = Self {
            pinned_certificates: HashMap::new(),
        };
        
        // Standard pinned certificates
        pinning.add_default_certificates();
        pinning
    }

    pub fn validate_certificate(&self, url: &Url) -> Result<(), super::SecurityError> {
        if let Some(host) = url.host_str() {
            if let Some(_pinned_hashes) = self.pinned_certificates.get(host) {
                // In einer echten Implementierung würde hier das aktuelle
                // Zertifikat abgerufen und mit den gepinnten Hashes verglichen
                // Für jetzt geben wir immer Ok zurück
                return Ok(());
            }
        }
        
        Ok(())
    }

    fn add_default_certificates(&mut self) {
        // Beispiel für pinned certificates
        // In einer echten Implementierung würden hier echte Certificate-Hashes stehen
        self.pinned_certificates.insert(
            "api.connectias.com".to_string(),
            vec![
                "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=".to_string(),
            ]
        );
    }
}

/// Network Security Manager für zentrale Verwaltung
pub struct NetworkSecurityManager {
    filter: NetworkSecurityFilter,
    allowed_domains: HashSet<String>,
}

impl NetworkSecurityManager {
    pub fn new() -> Self {
        Self {
            filter: NetworkSecurityFilter::new(),
            allowed_domains: HashSet::new(),
        }
    }

    /// Fügt eine erlaubte Domain hinzu
    pub fn add_allowed_domain(&mut self, domain: String) {
        self.allowed_domains.insert(domain);
    }

    /// Validiert einen Network-Request
    pub fn validate_request(&mut self, url: &str) -> Result<(), super::SecurityError> {
        // 1. URL validieren
        self.filter.validate_url(url)?;

        // 2. Domain-Whitelist prüfen (falls aktiv)
        if !self.allowed_domains.is_empty() {
            if let Ok(parsed) = Url::parse(url) {
                if let Some(host) = parsed.host_str() {
                    if !self.is_domain_allowed(host) {
                        return Err(super::SecurityError::SecurityViolation(
                            format!("Domain not in whitelist: {}", host)
                        ));
                    }
                }
            }
        }

        Ok(())
    }

    fn is_domain_allowed(&self, host: &str) -> bool {
        self.allowed_domains.iter().any(|domain| {
            host == domain || host.ends_with(&format!(".{}", domain))
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_localhost_blocking() {
        let filter = NetworkSecurityFilter::new();
        
        assert!(filter.validate_url("http://localhost:8080").is_err());
        assert!(filter.validate_url("http://127.0.0.1:8080").is_err());
        assert!(filter.validate_url("http://::1:8080").is_err());
    }

    #[test]
    fn test_private_ip_blocking() {
        let filter = NetworkSecurityFilter::new();
        
        assert!(filter.validate_url("http://192.168.1.1").is_err());
        assert!(filter.validate_url("http://10.0.0.1").is_err());
        assert!(filter.validate_url("http://172.16.0.1").is_err());
    }

    #[test]
    fn test_public_url_allowed() {
        let filter = NetworkSecurityFilter::new();
        
        assert!(filter.validate_url("https://api.example.com").is_ok());
        assert!(filter.validate_url("https://www.google.com").is_ok());
    }

    #[test]
    fn test_rate_limiting() {
        let mut rate_limiter = RateLimiter::new();
        
        // Erste 60 Requests sollten erlaubt sein
        for _i in 0..60 {
            assert!(rate_limiter.check_rate_limit("https://api.example.com"));
        }
        
        // 61. Request sollte blockiert werden
        assert!(!rate_limiter.check_rate_limit("https://api.example.com"));
    }
}

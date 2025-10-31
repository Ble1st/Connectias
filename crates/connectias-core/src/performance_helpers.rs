//! Performance-Helper-Makros und Utilities
//! 
//! Automatische Optimierungen für häufige Patterns

/// Makro für optimierte HashMap-Initialisierung mit geschätzter Capacity
#[macro_export]
macro_rules! hashmap_with_capacity {
    ($capacity:expr) => {
        {
            let mut map = ::std::collections::HashMap::new();
            map.reserve($capacity);
            map
        }
    };
    ($capacity:expr, $($key:expr => $val:expr),* $(,)?) => {
        {
            let mut map = ::std::collections::HashMap::with_capacity($capacity);
            $(map.insert($key, $val);)*
            map
        }
    };
}

/// Makro für optimierte Vec-Initialisierung mit Capacity
#[macro_export]
macro_rules! vec_with_capacity {
    ($capacity:expr) => {
        Vec::with_capacity($capacity)
    };
    ($capacity:expr, $($item:expr),* $(,)?) => {
        {
            let mut vec = Vec::with_capacity($capacity);
            $(vec.push($item);)*
            vec
        }
    };
}

/// Hot-Path-Tracking-Makro
#[macro_export]
macro_rules! track_hot_path {
    ($name:expr, $optimizer:expr, $code:block) => {
        {
            let start = ::std::time::Instant::now();
            let result = $code;
            let duration = start.elapsed();
            let optimizer_clone = $optimizer.clone();
            ::tokio::spawn(async move {
                optimizer_clone.track_hot_path($name, duration).await;
            });
            result
        }
    };
}


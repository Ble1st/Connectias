use connectias_api::{SystemInfo, OsInfo, CpuInfo, MemoryInfo, PluginError};

pub struct SystemInfoImpl;

impl SystemInfoImpl {
    pub fn new() -> Self {
        Self
    }
}

impl SystemInfo for SystemInfoImpl {
    fn get_os_info(&self) -> Result<OsInfo, PluginError> {
        Ok(OsInfo {
            name: std::env::consts::OS.to_string(),
            version: "Unknown".to_string(), // Aus /etc/os-release lesen
            arch: std::env::consts::ARCH.to_string(),
        })
    }
    
    fn get_cpu_info(&self) -> Result<CpuInfo, PluginError> {
        Ok(CpuInfo {
            cores: num_cpus::get() as u32,
            model: "Unknown".to_string(), // Aus /proc/cpuinfo lesen
            frequency: 0, // Aus sysfs lesen
        })
    }
    
    fn get_memory_info(&self) -> Result<MemoryInfo, PluginError> {
        // Vereinfachte Implementierung - in echtem System würde hier sys_info verwendet
        Ok(MemoryInfo {
            total: 8 * 1024 * 1024 * 1024, // 8GB
            available: 4 * 1024 * 1024 * 1024, // 4GB
            used: 4 * 1024 * 1024 * 1024, // 4GB
        })
    }
}

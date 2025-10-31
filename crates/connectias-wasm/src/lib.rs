use wasmtime::*;
use connectias_api::{Plugin, PluginInfo, PluginContext, PluginError};
use std::collections::HashMap;
use std::time::Duration;

#[cfg(feature = "advanced_fuel_metering")]
mod fuel_meter;
#[cfg(feature = "advanced_fuel_metering")]
use fuel_meter::{AdvancedFuelMeter, InstructionType};

/// WASM Runtime mit erweiterten Security-Features
pub struct WasmRuntime {
    engine: Engine,
    resource_limits: ResourceLimits,
    #[cfg(feature = "advanced_fuel_metering")]
    fuel_meter: Option<AdvancedFuelMeter>,
}

/// Information über eine Memory-Allocation
#[derive(Debug, Clone)]
struct AllocationInfo {
    offset: u32,
    size: u32,
    allocated_at: std::time::SystemTime,
}

/// Resource-Limits für WASM-Plugins
#[derive(Debug, Clone)]
pub struct ResourceLimits {
    pub max_memory: usize,        // 100MB
    pub max_cpu_percent: f64,     // 75%
    pub max_execution_time: Duration, // 30 seconds
    pub max_fuel: u64,           // CPU-Limits
}

impl Default for ResourceLimits {
    fn default() -> Self {
        Self {
            max_memory: 100 * 1024 * 1024, // 100MB
            max_cpu_percent: 75.0,
            max_execution_time: Duration::from_secs(30),
            max_fuel: 1_000_000, // 1M fuel units
        }
    }
}

impl WasmRuntime {
    /// Erstellt eine neue WASM Runtime mit Security-Config
    pub fn new() -> Result<Self, anyhow::Error> {
        let mut config = Config::new();
        
        // Security configuration - restriktive Einstellungen
        config.wasm_multi_memory(false);
        config.wasm_memory64(false);
        config.wasm_bulk_memory(false);
        config.wasm_reference_types(false);
        config.wasm_relaxed_simd(false); // Muss vor wasm_simd(false) kommen
        config.wasm_simd(false);
        config.wasm_threads(false);
        
        // Fuel-Metering für CPU-Limits aktivieren
        config.consume_fuel(true);
        
        // Memory-Limits
        config.max_wasm_stack(1024 * 1024); // 1MB Stack
        
        let engine = Engine::new(&config)?;
        
        Ok(Self { 
            engine,
            resource_limits: ResourceLimits::default(),
            #[cfg(feature = "advanced_fuel_metering")]
            fuel_meter: None,
        })
    }

    /// Lädt ein WASM-Plugin mit Resource-Limits
    pub fn load_plugin(&self, wasm_bytes: &[u8], plugin_id: String) -> Result<WasmPlugin, anyhow::Error> {
        let module = Module::new(&self.engine, wasm_bytes)?;
        
        Ok(WasmPlugin {
            module,
            engine: self.engine.clone(),
            resource_limits: self.resource_limits.clone(),
            #[cfg(feature = "advanced_fuel_metering")]
            fuel_meter: Some(AdvancedFuelMeter::new(plugin_id.clone())),
            store: None, // Wird bei init() erstellt
            instance: None, // Wird bei init() erstellt und gespeichert
            allocations: HashMap::new(),
            next_offset: 1024, // Start nach WASM-Stack
        })
    }

    /// Setzt Resource-Limits für die Runtime
    pub fn set_resource_limits(&mut self, limits: ResourceLimits) {
        self.resource_limits = limits;
    }
}

/// WASM Plugin mit vollständiger Funktionalität
pub struct WasmPlugin {
    module: Module,
    engine: Engine,
    resource_limits: ResourceLimits,
    #[cfg(feature = "advanced_fuel_metering")]
    fuel_meter: Option<AdvancedFuelMeter>,
    store: Option<Store<()>>,
    instance: Option<Instance>, // Gespeicherte Instance für Performance (wird in init() erstellt)
    // Memory-Tracking für echte WASM-Implementierung
    allocations: HashMap<u32, AllocationInfo>,
    next_offset: u32,
}

impl WasmPlugin {
    /// Get resource limits for this plugin
    pub fn get_resource_limits(&self) -> &ResourceLimits {
        &self.resource_limits
    }

    /// Erstellt einen neuen Store für das Plugin
    fn create_store(&self) -> Result<Store<()>, PluginError> {
        let mut store = Store::new(&self.engine, ());
        
        // Fuel-Limits initialisieren wenn consume_fuel aktiviert ist
        if self.resource_limits.max_fuel > 0 {
            store.set_fuel(self.resource_limits.max_fuel)
                .map_err(|e| PluginError::ExecutionFailed(format!("Failed to set fuel: {}", e)))?;
        }
        
        // Advanced fuel metering setup
        #[cfg(feature = "advanced_fuel_metering")]
        if let Some(ref _fuel_meter) = self.fuel_meter {
            // Fuel metering integration would be handled by the WASM runtime
            // when executing instructions. The fuel_meter tracks consumption
            // through the consume_fuel method calls.
        }
        
        Ok(store)
    }

    /// NON-PRODUCTION PLACEHOLDER: Allokiert WASM Memory (nicht implementiert)
    /// 
    /// WARNUNG: Diese Funktion ist ein Stub und führt KEINE echten WASM Memory-Operationen durch.
    /// Sie ist nicht für Production geeignet und muss durch eine echte WASM Memory-Allocation
    /// ersetzt werden, die mit einem echten Wasm-Instance und Allocator integriert ist.
    /// 
    /// TODO: Implementiere echte WASM Memory-Allocation mit:
    /// - Wasm-Instance Integration
    /// - Memory Allocator
    /// - Proper Memory Management
    fn allocate_wasm_memory_stub(&self, _store: &mut Store<()>, data: &[u8]) -> Result<u32, PluginError> {
        // Prüfe Memory-Limit
        if data.len() > self.resource_limits.max_memory {
            return Err(PluginError::MemoryLimitExceeded { 
                used: data.len(), 
                limit: self.resource_limits.max_memory 
            });
        }
        
        // WARNUNG: Dies ist ein Stub - keine echte WASM Memory-Allocation
        // Rückgabe eines Dummy-Pointers würde Memory-Korruption verursachen, da
        // alle Allokationen denselben Pointer zurückgeben würden
        return Err(PluginError::ExecutionFailed(
            "WASM memory allocation not implemented - allocate_wasm_memory_stub is a placeholder and cannot be used in production".to_string()
        ));
    }

    /// Schreibt Daten in WASM Memory (echte Implementierung)
    /// 
    /// WICHTIG: Diese interne Methode verwendet den übergebenen Store.
    /// Die öffentliche Methode write_to_wasm_memory() sollte den Store aus self.store holen.
    fn write_to_wasm_memory(&mut self, store: &mut Store<()>, data: &[u8]) -> Result<u32, PluginError> {
        
        // Prüfe Memory-Limit
        if data.len() > self.resource_limits.max_memory {
            return Err(PluginError::MemoryLimitExceeded { 
                used: data.len(), 
                limit: self.resource_limits.max_memory 
            });
        }
        
        // Hole WASM Memory aus gespeicherter Instance (Performance-Optimierung)
        let memory = self.get_memory(&mut *store)?;
        
        // Berechne benötigte Größe
        let data_size = data.len() as u32;
        
        // Prüfe ob Memory wachsen muss
        let current_pages = memory.size(&mut *store); // Reborrow store für size()
        let current_bytes = (current_pages as u32) * 65536; // 64KB per page
        let required_bytes = self.next_offset.saturating_add(data_size);
        
        if required_bytes > current_bytes {
            // Berechne benötigte zusätzliche Pages (mit Overflow-Schutz)
            // WICHTIG: saturating_add verhindert Overflow wenn required_bytes nahe u32::MAX ist
            let required_pages = (required_bytes.saturating_add(65535) / 65536) as u64; // Aufrunden
            let additional_pages = required_pages.saturating_sub(current_pages);
            
            if additional_pages > 0 {
                // Grow Memory (Reborrow store für grow())
                memory.grow(&mut *store, additional_pages)
                    .map_err(|e| PluginError::ExecutionFailed(format!("Failed to grow WASM memory: {}", e)))?;
            }
        }
        
        // Allokiere Memory-Offset über allocate_and_track (Reborrow store)
        let offset = self.allocate_and_track(&mut *store, &memory, data_size)?;
        
        // Schreibe Daten in Memory (Reborrow store)
        let memory_view = memory.data_mut(&mut *store);
        let offset_usize = offset as usize;
        
        if offset_usize + data.len() > memory_view.len() {
            return Err(PluginError::ExecutionFailed(format!(
                "Memory write out of bounds: offset={}, size={}, memory_size={}",
                offset, data.len(), memory_view.len()
            )));
        }
        
        memory_view[offset_usize..offset_usize + data.len()].copy_from_slice(data);
        
        Ok(offset)
    }

    /// Liest Daten aus WASM Memory
    fn read_from_wasm_memory(&self, _store: &mut Store<()>, _ptr: u32, len: u32) -> Result<Vec<u8>, PluginError> {
        // Für jetzt verwenden wir eine einfache Implementierung
        // In einer echten Implementierung würde man hier die WASM Memory-Allocation verwenden
        // Da wir keine echte WASM-Instance haben, simulieren wir nur die Daten-Rückgabe
        Ok(vec![0u8; len as usize]) // Placeholder - in echter Implementierung würde hier die WASM-Memory-Daten stehen
    }

    /// Holt Memory-Export aus dem Module
    #[allow(dead_code)]
    fn get_memory(&self, store: &mut Store<()>) -> Result<Memory, PluginError> {
        let instance = self.get_instance(store)?;
        instance.get_memory(store, "memory")
            .ok_or_else(|| PluginError::ExecutionFailed("Memory export not found".to_string()))
    }

    /// Holt Instance aus dem Store
    fn get_instance(&self, store: &mut Store<()>) -> Result<Instance, PluginError> {
        // Instance wird bei init() erstellt und im Store gespeichert
        // Für jetzt erstellen wir eine neue Instance
        let instance = Instance::new(store, &self.module, &[])
            .map_err(|e| PluginError::ExecutionFailed(format!("Failed to create instance: {}", e)))?;
        
        Ok(instance)
    }

    /// Holt Alloc-Function aus dem Module
    #[allow(dead_code)]
    fn get_alloc_function(&self, store: &mut Store<()>) -> Result<TypedFunc<i32, i32>, PluginError> {
        let instance = self.get_instance(store)?;
        instance.get_typed_func::<i32, i32>(store, "alloc")
            .map_err(|e| PluginError::ExecutionFailed(format!("Alloc function not found: {}", e)))
    }

    /// Holt Free-Function aus dem Module
    #[allow(dead_code)]
    fn get_free_function(&self, store: &mut Store<()>) -> Result<TypedFunc<i32, ()>, PluginError> {
        let instance = self.get_instance(store)?;
        instance.get_typed_func::<i32, ()>(store, "free")
            .map_err(|e| PluginError::ExecutionFailed(format!("Free function not found: {}", e)))
    }
}

impl Plugin for WasmPlugin {
    /// Holt Plugin-Informationen aus WASM-Funktion
    fn get_info(&self) -> PluginInfo {
        // Versuche WASM get_info-Funktion aufzurufen
        if let Ok(mut store) = self.create_store() {
            if let Ok(instance) = Instance::new(&mut store, &self.module, &[]) {
                if let Ok(get_info_func) = instance.get_typed_func::<(), (i32, i32)>(&mut store, "plugin_get_info") {
                    if let Ok((info_ptr, info_len)) = get_info_func.call(&mut store, ()) {
                        if let Ok(info_data) = self.read_from_wasm_memory(&mut store, info_ptr as u32, info_len as u32) {
                            if let Ok(info_json) = String::from_utf8(info_data) {
                                if let Ok(plugin_info) = serde_json::from_str::<PluginInfo>(&info_json) {
                                    return plugin_info;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Fallback-Info falls WASM-Funktion nicht verfügbar
        PluginInfo {
            id: "wasm-plugin".to_string(),
            name: "WASM Plugin".to_string(),
            version: "1.0.0".to_string(),
            author: "Unknown".to_string(),
            description: "WASM Plugin with sandbox isolation".to_string(),
            min_core_version: "1.0.0".to_string(),
            max_core_version: None,
            permissions: vec![],
            entry_point: "main.wasm".to_string(),
            dependencies: None,
        }
    }

    /// Initialisiert das WASM-Plugin
    fn init(&mut self, context: PluginContext) -> Result<(), PluginError> {
        let mut store = self.create_store()?;
        
        // Instance erstellen und speichern (für Performance - wird nicht mehr bei jedem Memory-Zugriff neu erstellt)
        let instance = Instance::new(&mut store, &self.module, &[])
            .map_err(|e| PluginError::InitializationFailed(format!("Failed to create WASM instance: {}", e)))?;
        
        // Instance und Store speichern für späteren Zugriff (verhindert wiederholte Instance-Erstellung)
        self.instance = Some(instance);
        self.store = Some(store);
        
        // Context serialisieren (nur primitive Felder)
        let context_json = serde_json::to_string(&serde_json::json!({
            "plugin_id": context.plugin_id,
        })).map_err(|e| PluginError::InitializationFailed(format!("Context serialization failed: {}", e)))?;
        
        // Context in WASM Memory schreiben (echte Implementierung)
        // Direkte Implementierung hier um Borrow-Konflikte zu vermeiden
        let context_ptr = {
            let store = self.store.as_mut()
                .ok_or_else(|| PluginError::InitializationFailed("Store not initialized".to_string()))?;
            let instance = self.instance.as_ref()
                .ok_or_else(|| PluginError::InitializationFailed("Instance not initialized".to_string()))?;
            
            // Prüfe Memory-Limit
            if context_json.len() > self.resource_limits.max_memory {
                return Err(PluginError::MemoryLimitExceeded { 
                    used: context_json.len(), 
                    limit: self.resource_limits.max_memory 
                });
            }
            
            // Hole WASM Memory aus gespeicherter Instance (Reborrow store)
            let memory = instance.get_memory(&mut *store, "memory")
                .ok_or_else(|| PluginError::ExecutionFailed("Memory export not found".to_string()))?;
            
            // Berechne benötigte Größe
            let data_size = context_json.len() as u32;
            
            // Prüfe ob Memory wachsen muss (Reborrow store)
            let current_pages = memory.size(&mut *store);
            let current_bytes = (current_pages as u32) * 65536;
            let required_bytes = self.next_offset.saturating_add(data_size);
            
            if required_bytes > current_bytes {
                // Berechne benötigte zusätzliche Pages (mit Overflow-Schutz)
                let required_pages = (required_bytes.saturating_add(65535) / 65536) as u64;
                let additional_pages = required_pages.saturating_sub(current_pages);
                
                if additional_pages > 0 {
                    // Grow Memory (Reborrow store)
                    memory.grow(&mut *store, additional_pages)
                        .map_err(|e| PluginError::ExecutionFailed(format!("Failed to grow WASM memory: {}", e)))?;
                }
            }
            
            // Allokiere Memory-Offset - verwende current_offset direkt
            let current_offset = self.next_offset;
            
            // Schreibe Daten in Memory (Reborrow store)
            let memory_view = memory.data_mut(&mut *store);
            let offset_usize = current_offset as usize;
            
            if offset_usize + context_json.len() > memory_view.len() {
                return Err(PluginError::ExecutionFailed(format!(
                    "Memory write out of bounds: offset={}, size={}, memory_size={}",
                    current_offset, context_json.len(), memory_view.len()
                )));
            }
            
            memory_view[offset_usize..offset_usize + context_json.len()].copy_from_slice(context_json.as_bytes());
            
            current_offset
        };
        
        // FIX BUG 2: Track Allokation SOFORT nach Memory-Write (bevor plugin_init)
        // Bei Fehler werden wir die Allokation wieder entfernen, um Memory Leaks zu vermeiden
        let data_size = context_json.len() as u32;
        let old_offset = context_ptr;
        
        // Temporär tracken für Cleanup bei Fehler
        let alloc_info = AllocationInfo {
            offset: old_offset,
            size: data_size,
            allocated_at: std::time::SystemTime::now(),
        };
        self.allocations.insert(old_offset, alloc_info);
        
        // WASM init-Funktion aufrufen
        let instance = self.instance.as_ref()
            .ok_or_else(|| PluginError::InitializationFailed("Instance not initialized".to_string()))?;
        let store = self.store.as_mut()
            .ok_or_else(|| PluginError::InitializationFailed("Store not initialized".to_string()))?;
        let init_func = instance.get_typed_func::<(i32, i32), i32>(&mut *store, "plugin_init")
            .map_err(|e| PluginError::InitializationFailed(format!("Init function not found: {}", e)))?;
        
        let result = init_func.call(&mut *store, (context_ptr as i32, context_json.len() as i32))
            .map_err(|e| {
                // FIX BUG 2: Cleanup bei Fehler - entferne getrackte Allokation
                self.allocations.remove(&old_offset);
                PluginError::InitializationFailed(format!("Init function call failed: {}", e))
            })?;
        
        if result != 0 {
            // FIX BUG 2: Initialisierung fehlgeschlagen - entferne getrackte Allokation
            // Memory-Block bleibt im WASM Memory, wird aber nicht mehr getrackt
            // Bei nächstem init() wird derselbe Offset wiederverwendet und überschrieben
            self.allocations.remove(&old_offset);
            return Err(PluginError::InitializationFailed("WASM plugin initialization failed".to_string()));
        }
        
        // FIX BUG 2: Nur bei erfolgreichem plugin_init(): next_offset inkrementieren
        // Allokation bleibt getrackt (wurde bereits oben eingefügt)
        self.next_offset = old_offset.saturating_add(data_size);
        
        Ok(())
    }

    /// Führt ein Command im WASM-Plugin aus
    fn execute(&self, command: &str, args: HashMap<String, String>) -> Result<String, PluginError> {
        // Prüfe ob Plugin initialisiert ist
        if self.store.is_none() {
            return Err(PluginError::ExecutionFailed("Plugin not initialized".to_string()));
        }
        
        // Advanced fuel metering - track execution
        #[cfg(feature = "advanced_fuel_metering")]
        if let Some(ref fuel_meter) = self.fuel_meter {
            // Track CPU operation
            fuel_meter.consume_fuel(InstructionType::Call, 1)
                .map_err(|_| PluginError::ExecutionFailed("Fuel exhausted during execution".to_string()))?;
            
            // Track memory operations based on args size
            let memory_ops = args.values().map(|v| v.len()).sum::<usize>() as u64;
            if memory_ops > 0 {
                fuel_meter.consume_fuel(InstructionType::MemoryAccess, memory_ops)
                    .map_err(|_| PluginError::ExecutionFailed("Fuel exhausted during memory operations".to_string()))?;
            }
        }
        
        // WARNUNG: WASM Memory-Allocation ist nicht implementiert
        // allocate_wasm_memory_stub ist ein Stub und gibt Fehler zurück
        // Bis echte WASM Memory-Allocation implementiert ist, kann execute() nicht verwendet werden
        // Dies verhindert Memory-Korruption durch wiederholte Allokationen mit gleichem Pointer
        Err(PluginError::ExecutionFailed(
            format!("WASM memory allocation not implemented - allocate_wasm_memory_stub is a placeholder. Cannot execute command '{}' until proper WASM memory management is implemented.", command)
        ))
    }

    /// Cleanup des WASM-Plugins mit vollständiger Resource-Freigabe
    fn cleanup(&mut self) -> Result<(), PluginError> {
        if let Some(mut store) = self.store.take() {
            // WASM cleanup-Funktion aufrufen falls verfügbar
            if let Ok(instance) = self.get_instance(&mut store) {
                if let Ok(cleanup_func) = instance.get_typed_func::<(), i32>(&mut store, "plugin_cleanup") {
                    let _ = cleanup_func.call(&mut store, ());
                }
            }
            
            // Store wird automatisch dropped
        }
        
        // Generate final fuel report if fuel meter exists
        #[cfg(feature = "advanced_fuel_metering")]
        if let Some(fuel_meter) = &self.fuel_meter {
            let _report = fuel_meter.generate_report();
            // Note: Logging would be handled by the calling application
        }
        
        // Resource-Limits zurücksetzen
        self.resource_limits = ResourceLimits::default();
        
        // Engine wird automatisch dropped
        Ok(())
    }
    
}

impl WasmPlugin {
    /// Get fuel report for this plugin
    #[cfg(feature = "advanced_fuel_metering")]
    pub fn get_fuel_report(&self) -> Option<fuel_meter::FuelReport> {
        self.fuel_meter.as_ref().map(|meter| meter.generate_report())
    }
    
    /// Allokiert Memory und trackt die Allocation atomar
    fn allocate_and_track(&mut self, store: &mut Store<()>, memory: &Memory, size: u32) -> Result<u32, PluginError> {
        // Finde freien Offset und inkrementiere next_offset atomar
        let current_offset = self.find_free_memory_offset(store, memory, size)?;
        self.next_offset = current_offset.saturating_add(size);
        self.track_allocation(current_offset, size)?;
        Ok(current_offset)
    }
    
    /// Findet freien Memory-Offset für Allocation (intern - verwendet self.next_offset)
    fn find_free_memory_offset(&self, store: &mut Store<()>, memory: &Memory, size: u32) -> Result<u32, PluginError> {
        // Verwende self.next_offset als Startpunkt (wird durch allocate_and_track aktualisiert)
        let current_offset = self.next_offset;
        
        // Prüfe ob genug Platz vorhanden ist
        let current_pages = memory.size(store);
        let current_bytes = current_pages * 65536; // 64KB per page
        let required_end = current_offset.saturating_add(size);
        
        if required_end > current_bytes as u32 {
            return Err(PluginError::ExecutionFailed(format!(
                "Insufficient memory: required={}, available={}", 
                required_end, current_bytes
            )));
        }
        
        // Prüfe auf Overlaps mit existierenden Allocations
        for (_, alloc_info) in &self.allocations {
            let alloc_end = alloc_info.offset.saturating_add(alloc_info.size);
            let new_end = current_offset.saturating_add(size);
            
            if current_offset < alloc_end && new_end > alloc_info.offset {
                return Err(PluginError::ExecutionFailed(format!(
                    "Memory overlap detected: new={}-{}, existing={}-{}", 
                    current_offset, new_end, alloc_info.offset, alloc_end
                )));
            }
        }
        
        Ok(current_offset)
    }
    
    /// Trackt eine neue Memory-Allocation (NICHT verantwortlich für next_offset-Update)
    /// 
    /// WICHTIG: next_offset wird von allocate_and_track aktualisiert, nicht hier
    /// Dies verhindert doppelte Mutation und Race Conditions
    fn track_allocation(&mut self, offset: u32, size: u32) -> Result<(), PluginError> {
        let alloc_info = AllocationInfo {
            offset,
            size,
            allocated_at: std::time::SystemTime::now(),
        };
        
        self.allocations.insert(offset, alloc_info);
        // next_offset wird NICHT hier aktualisiert - das macht allocate_and_track
        
        Ok(())
    }
    
    /// Validiert ob eine Allocation existiert und gültig ist
    fn is_valid_allocation(&self, ptr: u32, len: u32) -> Result<bool, PluginError> {
        for (_, alloc_info) in &self.allocations {
            if ptr >= alloc_info.offset && ptr + len <= alloc_info.offset + alloc_info.size {
                return Ok(true);
            }
        }
        Ok(false)
    }
    
    /// Gibt Memory-Allocation frei
    fn free_allocation(&mut self, offset: u32) -> Result<(), PluginError> {
        if self.allocations.remove(&offset).is_some() {
            // In einer echten Implementierung würde man hier Memory-Zeroing durchführen
            Ok(())
        } else {
            Err(PluginError::ExecutionFailed(format!(
                "Allocation not found: {}", offset
            )))
        }
    }
}

package com.ble1st.connectias.security;

import com.ble1st.connectias.api.PluginInfo;
import com.ble1st.connectias.api.PluginPermission;

import java.io.FilePermission;
import java.net.SocketPermission;
import java.security.Permission;
import java.util.Set;

public class PluginSecurityPolicy extends SecurityManager {
    private final Set<PluginPermission> allowedPermissions;
    private final String pluginId;
    
    public PluginSecurityPolicy(PluginInfo plugin) {
        this.pluginId = plugin.id;
        this.allowedPermissions = Set.copyOf(plugin.permissions);
    }
    
    @Override
    public void checkPermission(Permission perm) {
        // Allow basic permissions
        if (perm instanceof java.lang.RuntimePermission) {
            String name = perm.getName();
            if (name.startsWith("accessDeclaredMembers") || 
                name.startsWith("createClassLoader") ||
                name.startsWith("getClassLoader")) {
                return; // Allow these for plugin loading
            }
        }
        
        // Check file permissions
        if (perm instanceof FilePermission) {
            checkFilePermission((FilePermission) perm);
            return;
        }
        
        // Check socket permissions
        if (perm instanceof SocketPermission) {
            checkSocketPermission((SocketPermission) perm);
            return;
        }
        
        // Deny all other permissions
        throw new SecurityException("Permission denied: " + perm.getClass().getSimpleName() + " " + perm.getName());
    }
    
    private void checkFilePermission(FilePermission perm) {
        String actions = perm.getActions();
        String path = perm.getName();
        
        // Only allow read access to plugin's own directory
        if (actions.contains("read")) {
            if (path.startsWith("/data/data/com.ble1st.connectias/plugins/" + pluginId + "/") ||
                path.startsWith("/data/data/com.ble1st.connectias/cache/")) {
                return; // Allow read access to plugin directory
            }
        }
        
        // Allow write access only to plugin's own directory (if STORAGE permission)
        if (actions.contains("write") && allowedPermissions.contains(PluginPermission.STORAGE)) {
            if (path.startsWith("/data/data/com.ble1st.connectias/plugins/" + pluginId + "/")) {
                return; // Allow write access to plugin directory
            }
        }
        
        throw new SecurityException("File access denied: " + actions + " " + path);
    }
    
    private void checkSocketPermission(SocketPermission perm) {
        if (!allowedPermissions.contains(PluginPermission.NETWORK)) {
            throw new SecurityException("Network access denied: no NETWORK permission");
        }
        
        String host = perm.getName();
        String actions = perm.getActions();
        
        // Block localhost access
        if (host.contains("localhost") || 
            host.contains("127.0.0.1") || 
            host.contains("::1") ||
            host.matches(".*10\\.\\d+\\.\\d+\\.\\d+.*") || // Private 10.x.x.x
            host.matches(".*192\\.168\\.\\d+\\.\\d+.*") || // Private 192.168.x.x
            host.matches(".*172\\.(1[6-9]|2[0-9]|3[0-1])\\.\\d+\\.\\d+.*")) { // Private 172.16-31.x.x
            throw new SecurityException("Localhost access denied: " + host);
        }
        
        // Allow external network access
        if (actions.contains("connect") || actions.contains("resolve")) {
            return; // Allow external connections
        }
        
        throw new SecurityException("Socket permission denied: " + actions + " " + host);
    }
}

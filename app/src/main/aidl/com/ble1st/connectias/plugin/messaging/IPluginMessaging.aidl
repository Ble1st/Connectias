package com.ble1st.connectias.plugin.messaging;

import com.ble1st.connectias.plugin.messaging.PluginMessage;
import com.ble1st.connectias.plugin.messaging.MessageResponse;

/**
 * AIDL interface for plugin-to-plugin messaging
 * 
 * Provides message routing between plugins via the Main Process message broker.
 * All messages go through this interface for security and control.
 */
interface IPluginMessaging {
    /**
     * Send a message to another plugin
     * @param message Message to send
     * @return Response from receiver plugin, or error
     */
    MessageResponse sendMessage(in PluginMessage message);
    
    /**
     * Get pending messages for a plugin
     * @param pluginId Plugin identifier
     * @return List of pending messages
     */
    List<PluginMessage> receiveMessages(String pluginId);
    
    /**
     * Send a response to a previous message
     * @param response Response message
     * @return True if response was delivered
     */
    boolean sendResponse(in MessageResponse response);
    
    /**
     * Register plugin for message receiving
     * @param pluginId Plugin identifier
     * @return True if registration successful
     */
    boolean registerPlugin(String pluginId);
    
    /**
     * Unregister plugin
     * @param pluginId Plugin identifier
     */
    void unregisterPlugin(String pluginId);
}

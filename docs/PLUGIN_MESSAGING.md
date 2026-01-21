# Inter-Plugin Messaging Guide

## Overview

The Inter-Plugin Messaging system enables direct communication between plugins using a Request/Response pattern. All messages are routed through the Main Process message broker for security and control.

## Architecture

```
[Plugin A (Sandbox)]  →  [IPC]  →  [Main Process Message Broker]  →  [IPC]  →  [Plugin B (Sandbox)]
     Request                                                              Response
```

## Features

- **Request/Response Pattern**: Synchronous communication with timeout handling
- **Message Broker**: Central routing in Main Process
- **Security**: Only registered plugins can send/receive messages
- **Rate Limiting**: 100 messages/sec per plugin
- **Payload Limits**: Maximum 1MB per message
- **Thread-Safe**: All operations are thread-safe

## Usage

### Sending Messages

```kotlin
class MyPlugin : IPlugin {
    override fun onEnable(): Boolean {
        lifecycleScope.launch {
            val response = context.sendMessageToPlugin(
                receiverId = "target-plugin",
                messageType = "DATA_REQUEST",
                payload = "Request data".toByteArray()
            )
            
            response.onSuccess { messageResponse ->
                if (messageResponse.success) {
                    val data = String(messageResponse.payload)
                    // Process response
                } else {
                    val error = messageResponse.errorMessage
                    // Handle error
                }
            }.onFailure { error ->
                // Handle failure
            }
        }
        return true
    }
}
```

### Receiving Messages

```kotlin
class MyPlugin : IPlugin {
    override fun onLoad(context: PluginContext): Boolean {
        // Register message handler
        context.registerMessageHandler("DATA_REQUEST") { message ->
            // Process incoming message
            val requestData = String(message.payload)
            
            // Process and create response
            val result = processRequest(requestData)
            
            // Return response
            MessageResponse.success(
                requestId = message.requestId,
                payload = result.toByteArray()
            )
        }
        
        return true
    }
    
    private fun processRequest(data: String): String {
        // Process request and return result
        return "Processed: $data"
    }
}
```

### Receiving Messages via Flow

```kotlin
class MyPlugin : IPlugin {
    override fun onEnable(): Boolean {
        lifecycleScope.launch {
            context.receiveMessages().collect { message ->
                when (message.messageType) {
                    "EVENT_NOTIFICATION" -> {
                        // Handle event
                        handleEvent(message)
                    }
                    "DATA_UPDATE" -> {
                        // Handle data update
                        updateData(message)
                    }
                }
            }
        }
        return true
    }
}
```

## Message Types

Use descriptive message types for better organization:

- `DATA_REQUEST` - Request data from another plugin
- `DATA_RESPONSE` - Response to data request
- `EVENT_NOTIFICATION` - Broadcast event to subscribers
- `COMMAND_EXECUTE` - Execute command in target plugin
- `STATUS_UPDATE` - Status update notification

## Security Considerations

1. **Plugin Registration**: Only registered plugins can send/receive messages
2. **Payload Size**: Maximum 1MB per message (enforced)
3. **Rate Limiting**: 100 messages/sec per plugin (enforced)
4. **Message Validation**: Sender and receiver must be registered
5. **Error Handling**: All errors are logged to SecurityAuditManager

## Best Practices

1. **Use Descriptive Message Types**: Make message types self-documenting
2. **Handle Errors Gracefully**: Always check response.success
3. **Use Timeouts**: Messages have a 5-second default timeout
4. **Keep Payloads Small**: Prefer references over large data
5. **Document Message Contracts**: Document expected message formats

## Example: Data Sharing Plugin

```kotlin
class DataSharingPlugin : IPlugin {
    private val dataStore = mutableMapOf<String, ByteArray>()
    
    override fun onLoad(context: PluginContext): Boolean {
        // Register handler for data requests
        context.registerMessageHandler("GET_DATA") { message ->
            val key = String(message.payload)
            val data = dataStore[key]
            
            if (data != null) {
                MessageResponse.success(message.requestId, data)
            } else {
                MessageResponse.error(message.requestId, "Data not found: $key")
            }
        }
        
        // Register handler for data storage
        context.registerMessageHandler("STORE_DATA") { message ->
            val parts = String(message.payload).split(":", limit = 2)
            if (parts.size == 2) {
                dataStore[parts[0]] = parts[1].toByteArray()
                MessageResponse.success(message.requestId)
            } else {
                MessageResponse.error(message.requestId, "Invalid format")
            }
        }
        
        return true
    }
}
```

## Troubleshooting

### Message Not Received

- Check if receiver plugin is registered and enabled
- Verify message type matches registered handler
- Check plugin logs for errors

### Timeout Errors

- Receiver plugin may be busy or crashed
- Increase timeout if needed (not configurable yet)
- Check receiver plugin status

### Rate Limit Errors

- Reduce message frequency
- Batch multiple messages into one
- Use async processing where possible

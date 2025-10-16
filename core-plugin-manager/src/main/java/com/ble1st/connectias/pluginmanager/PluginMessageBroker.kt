package com.ble1st.connectias.pluginmanager

import timber.log.Timber

class PluginMessageBroker {
    private val subscribers = mutableMapOf<String, MutableList<MessageSubscriber>>()
    
    fun publish(topic: String, data: Any, senderId: String) {
        val topicSubscribers = subscribers[topic] ?: return
        
        Timber.d("Publishing message to topic '$topic' from plugin '$senderId'")
        
        topicSubscribers.forEach { subscriber ->
            try {
                subscriber.callback(data)
            } catch (e: Exception) {
                Timber.e(e, "Error delivering message to subscriber ${subscriber.pluginId}")
            }
        }
    }
    
    fun subscribe(topic: String, subscriber: MessageSubscriber) {
        subscribers.getOrPut(topic) { mutableListOf() }.add(subscriber)
        Timber.d("Plugin ${subscriber.pluginId} subscribed to topic '$topic'")
    }
    
    fun unsubscribe(topic: String, subscriber: MessageSubscriber) {
        subscribers[topic]?.remove(subscriber)
        Timber.d("Plugin ${subscriber.pluginId} unsubscribed from topic '$topic'")
    }
    
    fun unsubscribeAll(pluginId: String) {
        subscribers.values.forEach { topicSubscribers ->
            topicSubscribers.removeAll { it.pluginId == pluginId }
        }
        Timber.d("Plugin $pluginId unsubscribed from all topics")
    }
}

data class MessageSubscriber(
    val pluginId: String,
    val callback: (Any) -> Unit
)

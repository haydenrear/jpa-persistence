package com.hayden.persistence.cdc;

import java.util.Map;

/**
 * Interface for CDC subscribers that process database change events
 */
public interface CdcSubscriber {
    /**
     * Process a change event from the CDC system
     *
     * @param tableName The table that changed
     * @param operation The operation type (INSERT, UPDATE, DELETE)
     * @param data The data associated with the change
     */
    void onDataChange(
        String tableName,
        String operation,
        Map<String, Object> data
    );

    /**
     * Get the subscription name this subscriber is interested in
     *
     * @return The subscription name
     */
    String getSubscriptionName();
}

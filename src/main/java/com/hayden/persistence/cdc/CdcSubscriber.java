package com.hayden.persistence.cdc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    default Optional<String> createSubscription() {
        return Optional.empty();
    }

    /**
     * Get the subscription name this subscriber is interested in
     *
     * @return The subscription name
     */
    List<String> getSubscriptionName();

    default String dbKey() {
        return "cdc-subscriber";
    }

}

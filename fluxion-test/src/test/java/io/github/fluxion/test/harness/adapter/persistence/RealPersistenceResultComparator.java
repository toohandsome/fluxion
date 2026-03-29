package io.github.fluxion.test.harness.adapter.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RealPersistenceResultComparator {

    public List<String> compare(PersistenceFixtureExpectation expect, RealPersistenceExecutionResult actual) {
        List<String> mismatches = new ArrayList<>();
        if (expect.instanceStatus() != null && !expect.instanceStatus().equals(actual.instanceStatus())) {
            mismatches.add("instanceStatus expected " + expect.instanceStatus() + " got " + actual.instanceStatus());
        }
        if (expect.nodeStatuses() != null) {
            for (Map.Entry<String, String> entry : expect.nodeStatuses().entrySet()) {
                String actualStatus = actual.nodeStatuses() != null ? actual.nodeStatuses().get(entry.getKey()) : null;
                if (!entry.getValue().equals(actualStatus)) {
                    mismatches.add("node " + entry.getKey() + " expected " + entry.getValue() + " got " + actualStatus);
                }
            }
        }
        if (expect.skipReason() != null) {
            for (Map.Entry<String, String> entry : expect.skipReason().entrySet()) {
                String actualReason = actual.skipReasons() != null ? actual.skipReasons().get(entry.getKey()) : null;
                if (!entry.getValue().equals(actualReason)) {
                    mismatches.add("skipReason " + entry.getKey() + " expected " + entry.getValue() + " got " + actualReason);
                }
            }
        }
        if (expect.nodeAttemptCounts() != null) {
            for (Map.Entry<String, Integer> entry : expect.nodeAttemptCounts().entrySet()) {
                Integer actualCount = actual.nodeAttemptCounts() != null ? actual.nodeAttemptCounts().get(entry.getKey()) : null;
                if (!entry.getValue().equals(actualCount)) {
                    mismatches.add("attemptCount " + entry.getKey() + " expected " + entry.getValue() + " got " + actualCount);
                }
            }
        }
        if (expect.attempts() != null) {
            for (Map.Entry<String, List<Map<String, Object>>> entry : expect.attempts().entrySet()) {
                List<Map<String, Object>> actualAttempts = actual.attempts() != null ? actual.attempts().get(entry.getKey()) : null;
                if (!containsSubset(actualAttempts, entry.getValue())) {
                    mismatches.add("attempts " + entry.getKey() + " expected subset " + entry.getValue() + " got " + actualAttempts);
                }
            }
        }
        return mismatches;
    }

    @SuppressWarnings("unchecked")
    private boolean containsSubset(Object actual, Object expected) {
        if (expected instanceof Map<?, ?> expectedMap) {
            if (!(actual instanceof Map<?, ?> actualMap)) {
                return false;
            }
            for (Map.Entry<?, ?> entry : expectedMap.entrySet()) {
                if (!actualMap.containsKey(entry.getKey())
                        || !containsSubset(actualMap.get(entry.getKey()), entry.getValue())) {
                    return false;
                }
            }
            return true;
        }
        if (expected instanceof List<?> expectedList) {
            if (!(actual instanceof List<?> actualList) || actualList.size() < expectedList.size()) {
                return false;
            }
            for (int index = 0; index < expectedList.size(); index++) {
                if (!containsSubset(actualList.get(index), expectedList.get(index))) {
                    return false;
                }
            }
            return true;
        }
        if (expected == null) {
            return actual == null;
        }
        if (expected instanceof Number expectedNumber && actual instanceof Number actualNumber) {
            return Double.compare(expectedNumber.doubleValue(), actualNumber.doubleValue()) == 0;
        }
        return expected.equals(actual);
    }
}

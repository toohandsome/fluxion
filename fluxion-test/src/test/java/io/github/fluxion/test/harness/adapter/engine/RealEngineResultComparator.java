package io.github.fluxion.test.harness.adapter.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RealEngineResultComparator {

    public List<String> compare(EngineFixtureExpectation expect, RealEngineExecutionResult actual) {
        List<String> mismatches = new ArrayList<>();
        if (expect.instanceStatus() != null && !expect.instanceStatus().equals(actual.instanceStatus())) {
            mismatches.add("instanceStatus expected " + expect.instanceStatus() + " got " + actual.instanceStatus());
        }
        if (expect.errorCode() != null && !expect.errorCode().equals(actual.errorCode())) {
            mismatches.add("errorCode expected " + expect.errorCode() + " got " + actual.errorCode());
        }
        if (expect.flowOutput() != null && !containsSubset(actual.flowOutput(), expect.flowOutput())) {
            mismatches.add("flowOutput expected subset " + expect.flowOutput() + " got " + actual.flowOutput());
        }
        if (expect.nodeStatuses() != null) {
            for (Map.Entry<String, String> entry : expect.nodeStatuses().entrySet()) {
                String actualStatus = actual.nodeStatuses() != null ? actual.nodeStatuses().get(entry.getKey()) : null;
                if (!entry.getValue().equals(actualStatus)) {
                    mismatches.add("node " + entry.getKey() + " expected " + entry.getValue() + " got " + actualStatus);
                }
            }
        }
        if (expect.skipReasons() != null) {
            for (Map.Entry<String, String> entry : expect.skipReasons().entrySet()) {
                String actualReason = actual.skipReasons() != null ? actual.skipReasons().get(entry.getKey()) : null;
                if (!entry.getValue().equals(actualReason)) {
                    mismatches.add("skipReason " + entry.getKey() + " expected " + entry.getValue() + " got " + actualReason);
                }
            }
        }
        if (expect.nodeErrorCodes() != null) {
            for (Map.Entry<String, String> entry : expect.nodeErrorCodes().entrySet()) {
                String actualErrorCode = actual.nodeErrorCodes() != null ? actual.nodeErrorCodes().get(entry.getKey()) : null;
                if (!entry.getValue().equals(actualErrorCode)) {
                    mismatches.add("nodeErrorCode " + entry.getKey() + " expected " + entry.getValue() + " got " + actualErrorCode);
                }
            }
        }
        if (expect.attemptCounts() != null) {
            for (Map.Entry<String, Integer> entry : expect.attemptCounts().entrySet()) {
                Integer actualAttempts = actual.attemptCounts() != null ? actual.attemptCounts().get(entry.getKey()) : null;
                if (!entry.getValue().equals(actualAttempts)) {
                    mismatches.add("attemptCount " + entry.getKey() + " expected " + entry.getValue() + " got " + actualAttempts);
                }
            }
        }
        if (expect.attemptDetails() != null) {
            for (Map.Entry<String, List<Map<String, Object>>> entry : expect.attemptDetails().entrySet()) {
                List<Map<String, Object>> actualDetails = actual.attemptDetails() != null
                        ? actual.attemptDetails().get(entry.getKey())
                        : null;
                if (!containsSubset(actualDetails, entry.getValue())) {
                    mismatches.add("attemptDetails " + entry.getKey() + " expected subset "
                            + entry.getValue() + " got " + actualDetails);
                }
            }
        }
        if (expect.missingNodes() != null) {
            for (String missingNode : expect.missingNodes()) {
                if (actual.missingNodes() == null || !actual.missingNodes().contains(missingNode)) {
                    mismatches.add("missingNode expected " + missingNode + " to be absent");
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

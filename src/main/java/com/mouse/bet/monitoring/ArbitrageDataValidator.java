package com.mouse.bet.monitoring;

import com.mouse.bet.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validator for arbitrage data integrity
 * Checks for common issues like missing odds, invalid mappings, etc.
 */
@Slf4j
@Component
public class ArbitrageDataValidator {

    private static final String EMOJI_CHECK = "✓";
    private static final String EMOJI_ERROR = "❌";
    private static final String EMOJI_WARNING = "⚠️";
    private static final String EMOJI_INFO = "ℹ️";

    /**
     * Validate Breaking-Bet API response
     * Performs comprehensive validation of the entire response structure
     */
    public ValidationResult validateResponse(BreakingBetResponse response) {
        ValidationResult result = new ValidationResult();

        if (response == null) {
            result.addError("Response is null");
            return result;
        }

        log.debug("{} {} Starting response validation...", EMOJI_CHECK, EMOJI_INFO);

        // Check items
        if (response.getItems() == null) {
            result.addError("Items array is null");
        } else if (response.getItems().isEmpty()) {
            result.addWarning("No items in response (empty array)");
        } else {
            log.debug("{} {} Response has {} items", EMOJI_CHECK, EMOJI_INFO, response.getItems().size());
            result.addInfo("Found " + response.getItems().size() + " arbitrage items");
        }

        // Check events
        if (response.getEvents() == null) {
            result.addError("Events array is null - cannot map odds to bookmakers");
        } else if (response.getEvents().isEmpty()) {
            result.addError("No events in response - cannot map odds to bookmakers");
        } else {
            log.debug("{} {} Response has {} events", EMOJI_CHECK, EMOJI_INFO, response.getEvents().size());
            result.addInfo("Found " + response.getEvents().size() + " events");
        }

        // Check masked flag
        if (response.getMasked() != null && response.getMasked()) {
            result.addWarning("Response data is masked (subscription required for real odds)");
        }

        // Validate each item
        if (response.getItems() != null) {
            int twoWayCount = 0;
            int threeWayCount = 0;
            int otherCount = 0;

            for (int i = 0; i < response.getItems().size(); i++) {
                ArbItem item = response.getItems().get(i);
                validateArbItem(item, i, result);

                // Count arb types
                if (item.getOdds() != null) {
                    if (item.getOdds().size() == 2) {
                        twoWayCount++;
                    } else if (item.getOdds().size() == 3) {
                        threeWayCount++;
                    } else {
                        otherCount++;
                    }
                }
            }

            result.addInfo(String.format("Arb types: %d 2-way, %d 3-way, %d other",
                    twoWayCount, threeWayCount, otherCount));
        }

        // Validate events and sub-events mapping
        if (response.getEvents() != null && response.getItems() != null) {
            validateEventMapping(response, result);
        }

        log.debug("{} {} Response validation completed: {}",
                EMOJI_CHECK, EMOJI_INFO, result.getSummary());

        return result;
    }

    /**
     * Validate individual arb item
     */
    private void validateArbItem(ArbItem item, int index, ValidationResult result) {
        String itemPrefix = "Item[" + index + "]";

        // Check ID
        if (item.getId() == null || item.getId().isEmpty()) {
            result.addError(itemPrefix + ": Missing or empty ID");
        }

        // Check event_id
        if (item.getEventId() == null || item.getEventId().isEmpty()) {
            result.addError(itemPrefix + ": Missing or empty event_id");
        }

        // Check profit value
        if (item.getValue() == null) {
            result.addWarning(itemPrefix + ": Profit value is null");
        } else if (item.getValue().compareTo(BigDecimal.ZERO) <= 0) {
            result.addWarning(itemPrefix + ": Profit value is 0 or negative (" + item.getValue() + "%)");
        } else if (item.getValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            result.addWarning(itemPrefix + ": Profit value seems unusually high (" + item.getValue() + "%)");
        }

        // Check ROI
        if (item.getRoi() == null) {
            result.addWarning(itemPrefix + ": ROI is null");
        }

        // Check created timestamp
        if (item.getCreated() == null || item.getCreated().isEmpty()) {
            result.addWarning(itemPrefix + ": Missing created timestamp");
        }

        // Validate odds array
        if (item.getOdds() == null) {
            result.addError(itemPrefix + ": Odds array is null");
        } else if (item.getOdds().isEmpty()) {
            result.addError(itemPrefix + ": Odds array is empty");
        } else {
            if (item.getOdds().size() < 2) {
                result.addError(itemPrefix + ": Less than 2 outcomes (need at least 2 for arb)");
            }

            // Check each odd
            for (int i = 0; i < item.getOdds().size(); i++) {
                Odd odd = item.getOdds().get(i);
                String oddPrefix = itemPrefix + ".odds[" + i + "]";

                validateOdd(odd, oddPrefix, result);
            }
        }
    }

    /**
     * Validate individual odd/outcome
     */
    private void validateOdd(Odd odd, String prefix, ValidationResult result) {
        // Check sub_event_id (CRITICAL FIELD)
        if (odd.getSubEventId() == null || odd.getSubEventId().isEmpty()) {
            result.addError(prefix + ": Missing sub_event_id (cannot map to bookmaker!)");
        }

        // Check odds value (will be 0 for non-subscribers)
        if (odd.getValue() == null) {
            result.addWarning(prefix + ": Odds value is null");
        } else if (odd.getValue().compareTo(BigDecimal.ZERO) == 0) {
            result.addWarning(prefix + ": Odds value is 0 (sub_event_id: " +
                    odd.getSubEventId() + "). Likely masked data (subscription required)");

            // Check if previous odds available as fallback
            if (odd.getPrev() != null && odd.getPrev().compareTo(BigDecimal.ZERO) > 0) {
                result.addInfo(prefix + ": Previous odds available as fallback: " + odd.getPrev());
            } else {
                result.addWarning(prefix + ": No valid odds data (both value and prev are 0 or null)");
            }
        } else if (odd.getValue().compareTo(BigDecimal.ONE) < 0) {
            result.addWarning(prefix + ": Odds value < 1.0 (" + odd.getValue() + ") - unusual");
        } else if (odd.getValue().compareTo(BigDecimal.valueOf(1000)) > 0) {
            result.addWarning(prefix + ": Odds value > 1000 (" + odd.getValue() + ") - seems unrealistic");
        }

        // Check updated timestamp
        if (odd.getUpdated() == null || odd.getUpdated().isEmpty()) {
            result.addWarning(prefix + ": Missing updated timestamp");
        }

        // Check type_id
        if (odd.getTypeId() == null) {
            result.addWarning(prefix + ": Missing type_id (market type)");
        }
    }

    /**
     * Validate event to sub-event mapping
     * This ensures all odds can be linked to bookmakers
     */
    private void validateEventMapping(BreakingBetResponse response, ValidationResult result) {
        log.debug("{} {} Validating event mappings...", EMOJI_CHECK, EMOJI_INFO);

        // Collect all sub_event_ids referenced in odds
        Set<String> requiredSubEventIds = getStrings(response);

        // Collect all available sub_event_ids from events
        Set<String> availableSubEventIds = new HashSet<>();
        if (response.getEvents() != null) {
            for (Event event : response.getEvents()) {
                if (event.getSubEvents() != null) {
                    for (SubEvent subEvent : event.getSubEvents()) {
                        if (subEvent.getId() != null && !subEvent.getId().isEmpty()) {
                            availableSubEventIds.add(subEvent.getId());
                        }
                    }
                }
            }
        }

        // Check for missing mappings
        Set<String> missingIds = new HashSet<>(requiredSubEventIds);
        missingIds.removeAll(availableSubEventIds);

        if (!missingIds.isEmpty()) {
            for (String missingId : missingIds) {
                result.addError("Sub-event mapping MISSING: sub_event_id '" + missingId +
                        "' referenced in odds but NOT found in events.sub_events");
            }
        } else if (!requiredSubEventIds.isEmpty()) {
            result.addInfo("All " + requiredSubEventIds.size() + " sub-event IDs properly mapped");
            log.debug("{} {} All sub-event IDs properly mapped", EMOJI_CHECK, EMOJI_INFO);
        }

        // Validate event_id references
        Set<String> requiredEventIds = response.getItems().stream()
                .map(ArbItem::getEventId)
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toSet());

        Set<String> availableEventIds = response.getEvents().stream()
                .map(Event::getId)
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toSet());

        Set<String> missingEventIds = new HashSet<>(requiredEventIds);
        missingEventIds.removeAll(availableEventIds);

        if (!missingEventIds.isEmpty()) {
            for (String missingId : missingEventIds) {
                result.addError("Event mapping MISSING: event_id '" + missingId +
                        "' referenced in items but NOT found in events array");
            }
        }
    }

    private static @NonNull Set<String> getStrings(BreakingBetResponse response) {
        Set<String> requiredSubEventIds = new HashSet<>();
        if (response.getItems() != null) {
            for (ArbItem item : response.getItems()) {
                if (item.getOdds() != null) {
                    for (Odd odd : item.getOdds()) {
                        if (odd.getSubEventId() != null && !odd.getSubEventId().isEmpty()) {
                            requiredSubEventIds.add(odd.getSubEventId());
                        }
                    }
                }
            }
        }
        return requiredSubEventIds;
    }

    /**
     * Validate parsed arbitrage data before entity conversion
     */
    public ValidationResult validateParsedData(ParsedArbitrageData data) {
        ValidationResult result = new ValidationResult();

        if (data == null) {
            result.addError("Parsed data is null");
            return result;
        }

        log.debug("{} {} Validating parsed arbitrage data...", EMOJI_CHECK, EMOJI_INFO);

        // Check basic fields
        if (data.getArbId() == null || data.getArbId().isEmpty()) {
            result.addError("Missing arb ID");
        }

        if (data.getEventId() == null || data.getEventId().isEmpty()) {
            result.addError("Missing event ID");
        }

        if (data.getProfitPercentage() == null) {
            result.addError("Missing profit percentage");
        } else if (data.getProfitPercentage().compareTo(BigDecimal.ZERO) <= 0) {
            result.addWarning("Profit percentage is 0 or negative: " + data.getProfitPercentage());
        }

        // Check sport info
        if (data.getSportId() == null) {
            result.addWarning("Missing sport ID");
        }

        if (data.getSportName() == null || data.getSportName().isEmpty()) {
            result.addWarning("Missing sport name");
        }

        // Check team info
        if (data.getTeam1() == null || data.getTeam1().isEmpty()) {
            result.addWarning("Missing team 1 name");
        }

        if (data.getTeam2() == null || data.getTeam2().isEmpty()) {
            result.addWarning("Missing team 2 name");
        }

        // Validate outcomes (CRITICAL)
        if (data.getOutcomes() == null) {
            result.addError("Outcomes array is null");
        } else if (data.getOutcomes().isEmpty()) {
            result.addError("No outcomes in parsed data");
        } else if (data.getOutcomes().size() < 2) {
            result.addError("Less than 2 outcomes (need at least 2 for arb). Found: " +
                    data.getOutcomes().size());
        } else if (data.getOutcomes().size() > 2) {
            result.addWarning("More than 2 outcomes (3-way arb). Count: " +
                    data.getOutcomes().size());
        } else {
            result.addInfo("Valid 2-way arbitrage with " + data.getOutcomes().size() + " outcomes");

            // Validate each outcome
            for (int i = 0; i < data.getOutcomes().size(); i++) {
                OutcomeData outcome = data.getOutcomes().get(i);
                String outcomePrefix = "Outcome[" + i + "]";

                validateOutcomeData(outcome, outcomePrefix, result);
            }

            // Check for duplicate bookmakers
            List<Integer> bookmakerIds = data.getOutcomes().stream()
                    .map(OutcomeData::getBookmakerId)
                    .filter(id -> id != null)
                    .collect(Collectors.toList());

            long uniqueBookmakers = bookmakerIds.stream().distinct().count();
            if (uniqueBookmakers < bookmakerIds.size()) {
                result.addWarning("Duplicate bookmakers detected in outcomes - this is unusual for arbitrage");
            } else if (uniqueBookmakers == bookmakerIds.size() && uniqueBookmakers > 0) {
                result.addInfo("All outcomes use different bookmakers (valid arbitrage)");
            }
        }

        log.debug("{} {} Parsed data validation completed: {}",
                EMOJI_CHECK, EMOJI_INFO, result.getSummary());

        return result;
    }

    /**
     * Validate individual outcome data
     */
    private void validateOutcomeData(OutcomeData outcome,
                                     String prefix,
                                     ValidationResult result) {
        // Check sub-event ID
        if (outcome.getSubEventId() == null || outcome.getSubEventId().isEmpty()) {
            result.addError(prefix + ": Missing sub-event ID");
        }

        // Check bookmaker info
        if (outcome.getBookmakerId() == null) {
            result.addError(prefix + ": Missing bookmaker ID");
        }

        if (outcome.getBookmakerName() == null ) {
            result.addWarning(prefix + ": Missing bookmaker name");
        } else if (outcome.getBookmakerName().getDisplayName().startsWith("Bookmaker_")) {
            result.addWarning(prefix + ": Bookmaker name not mapped (using ID: " +
                    outcome.getBookmakerName() + ")");
        }

        // Check odds
        if (outcome.getOdds() == null) {
            result.addError(prefix + ": Missing odds value");
        } else if (outcome.getOdds().compareTo(BigDecimal.ONE) < 0) {
            result.addError(prefix + ": Invalid odds value (< 1.0): " + outcome.getOdds());
        } else if (outcome.getOdds().compareTo(BigDecimal.valueOf(1000)) > 0) {
            result.addWarning(prefix + ": Unusually high odds value: " + outcome.getOdds());
        }

        // Check outcome name
        if (outcome.getOutcomeName() == null || outcome.getOutcomeName().isEmpty()) {
            result.addWarning(prefix + ": Missing outcome name");
        }

        // Check original ID
        if (outcome.getOriginalId() == null || outcome.getOriginalId().isEmpty()) {
            result.addWarning(prefix + ": Missing original event ID from bookmaker");
        }
    }

    /**
     * Validation result holder with error/warning/info collections
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> info = new ArrayList<>();

        public void addError(String message) {
            errors.add(message);
            log.error("{} {}", EMOJI_ERROR, message);
        }

        public void addWarning(String message) {
            warnings.add(message);
            log.warn("{} {}", EMOJI_WARNING, message);
        }

        public void addInfo(String message) {
            info.add(message);
            log.debug("{} {}", EMOJI_INFO, message);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }

        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }

        public List<String> getInfo() {
            return new ArrayList<>(info);
        }

        public int getErrorCount() {
            return errors.size();
        }

        public int getWarningCount() {
            return warnings.size();
        }

        public int getInfoCount() {
            return info.size();
        }

        public String getSummary() {
            return String.format("Errors: %d, Warnings: %d, Info: %d",
                    errors.size(), warnings.size(), info.size());
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("═══════════════════════════════════════════\n");
            sb.append("  VALIDATION RESULT\n");
            sb.append("═══════════════════════════════════════════\n");
            sb.append("Status: ").append(isValid() ? "✓ VALID" : "✗ INVALID").append("\n");
            sb.append("Errors: ").append(errors.size()).append("\n");

            if (!errors.isEmpty()) {
                sb.append("\nERRORS:\n");
                errors.forEach(e -> sb.append("  ❌ ").append(e).append("\n"));
            }

            sb.append("\nWarnings: ").append(warnings.size()).append("\n");
            if (!warnings.isEmpty()) {
                sb.append("\nWARNINGS:\n");
                warnings.forEach(w -> sb.append("  ⚠️  ").append(w).append("\n"));
            }

            if (!info.isEmpty()) {
                sb.append("\nINFO:\n");
                info.forEach(i -> sb.append("  ℹ️  ").append(i).append("\n"));
            }

            sb.append("═══════════════════════════════════════════\n");

            return sb.toString();
        }
    }
}
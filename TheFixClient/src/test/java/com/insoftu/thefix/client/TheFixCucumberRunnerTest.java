/*
 * Copyright (C) 2026 Debjyoti SARKAR
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.insoftu.thefix.client;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TheFixCucumberRunner}: Gherkin parser and step execution.
 * Uses a real (non-connected) {@link TheFixClientWorkbenchState} so tests
 * exercise all state interactions without requiring a live FIX session.
 */
class TheFixCucumberRunnerTest {

    @TempDir
    Path tempDir;

    private TheFixClientWorkbenchState state;
    private TheFixCucumberRunner runner;

    @BeforeEach
    void setUp() {
        TheFixClientConfig config = new TheFixClientConfig(
                "0.0.0.0", 8081, "localhost", 9880,
                "FIX.4.4", "THEFIX_TRDR01", "LLEXSIM",
                "FIX.4.4", 30, 5, 25,
                tempDir.toString(), false, 480, 1);
        TheFixSessionProfileStore store = new TheFixSessionProfileStore(config);
        store.updateStoragePath(tempDir.toString());
        state = new TheFixClientWorkbenchState(config, store,
                new TheFixMessageTemplateStore(tempDir.resolve("templates-db").resolve("message-templates")));
        runner = new TheFixCucumberRunner(state);
    }

    @AfterEach
    void tearDown() {
        state.close();
    }

    // -----------------------------------------------------------------------
    // Empty / blank input
    // -----------------------------------------------------------------------

    @Test
    void emptyFeatureTextReturnsUndefinedStatusWithZeroScenarios() {
        JsonObject result = runner.run("", null);

        assertEquals("UNDEFINED", result.getString("status"));
        assertEquals(0, result.getInteger("totalScenarios"));
        assertEquals(0, result.getInteger("passed"));
        assertEquals(0, result.getInteger("failed"));
        assertEquals(0, result.getInteger("skipped"));
        assertTrue(result.getJsonArray("scenarios").isEmpty());
    }

    @Test
    void featureWithNoScenariosReturnsUndefinedStatus() {
        String text = """
                Feature: Empty feature

                  # Only a background, no scenarios
                  Background:
                    Given the FIX session is disconnected
                """;

        JsonObject result = runner.run(text, null);

        assertEquals("UNDEFINED", result.getString("status"));
        assertEquals(0, result.getInteger("totalScenarios"));
    }

    // -----------------------------------------------------------------------
    // Feature name extraction
    // -----------------------------------------------------------------------

    @Test
    void featureNameIsExtractedCorrectly() {
        String text = """
                Feature: Order routing smoke tests

                  Scenario: Session is not connected at startup
                    Then the session should not be connected
                """;

        JsonObject result = runner.run(text, null);

        assertEquals("Order routing smoke tests", result.getString("featureName"));
    }

    // -----------------------------------------------------------------------
    // Single scenario – step PASSED
    // -----------------------------------------------------------------------

    @Test
    void singleScenarioAllStepsPassedReturnsPassedStatus() {
        String text = """
                Feature: Session checks

                  Scenario: Verify initial disconnected state
                    Given the FIX session is disconnected
                    Then the session should not be connected
                    And the sent orders count should be at least 0
                """;

        JsonObject result = runner.run(text, null);

        assertEquals("PASSED", result.getString("status"));
        assertEquals(1, result.getInteger("totalScenarios"));
        assertEquals(1, result.getInteger("passed"));
        assertEquals(0, result.getInteger("failed"));

        JsonObject scenarioResult = result.getJsonArray("scenarios").getJsonObject(0);
        assertEquals("PASSED", scenarioResult.getString("status"));
        assertEquals(3, scenarioResult.getJsonArray("steps").size());
        for (int i = 0; i < 3; i++) {
            assertEquals("PASSED", scenarioResult.getJsonArray("steps").getJsonObject(i).getString("status"));
        }
    }

    // -----------------------------------------------------------------------
    // Unknown step → FAILED
    // -----------------------------------------------------------------------

    @Test
    void unknownStepFailsWithNoMatchingStepDefinitionMessage() {
        String text = """
                Feature: Unknown steps

                  Scenario: Step with no match
                    Given this step does not exist in any definition
                """;

        JsonObject result = runner.run(text, null);

        assertEquals("FAILED", result.getString("status"));
        assertEquals(1, result.getInteger("failed"));

        JsonObject step = result.getJsonArray("scenarios").getJsonObject(0).getJsonArray("steps").getJsonObject(0);
        assertEquals("FAILED", step.getString("status"));
        assertTrue(step.getString("message").contains("No matching step definition found"),
                "Expected 'No matching step definition found' in: " + step.getString("message"));
    }

    // -----------------------------------------------------------------------
    // FAILED step → subsequent steps SKIPPED
    // -----------------------------------------------------------------------

    @Test
    void failedStepCausesAllSubsequentStepsToBeSkipped() {
        String text = """
                Feature: Propagation

                  Scenario: First step fails
                    Given this step has no matching definition
                    When I wait 0 seconds
                    Then the session should not be connected
                """;

        JsonObject result = runner.run(text, null);

        assertEquals("FAILED", result.getString("status"));
        JsonArray steps = result.getJsonArray("scenarios").getJsonObject(0).getJsonArray("steps");
        assertEquals(3, steps.size());
        assertEquals("FAILED",  steps.getJsonObject(0).getString("status"));
        assertEquals("SKIPPED", steps.getJsonObject(1).getString("status"));
        assertEquals("SKIPPED", steps.getJsonObject(2).getString("status"));
    }

    // -----------------------------------------------------------------------
    // Verification step that fails explicitly → FAILED + message
    // -----------------------------------------------------------------------

    @Test
    void verificationStepFailsWhenAssertionIsNotMet() {
        // On a fresh (disconnected) state, sentOrders = 0, so "at least 1" must fail.
        String text = """
                Feature: Verification

                  Scenario: Sent orders count too low
                    Then the sent orders count should be at least 1
                """;

        JsonObject result = runner.run(text, null);

        assertEquals("FAILED", result.getString("status"));
        JsonObject step = result.getJsonArray("scenarios").getJsonObject(0).getJsonArray("steps").getJsonObject(0);
        assertEquals("FAILED", step.getString("status"));
        assertTrue(step.getString("message").contains("sentOrders"),
                "Expected sentOrders info in: " + step.getString("message"));
    }

    // -----------------------------------------------------------------------
    // Background steps run before every scenario
    // -----------------------------------------------------------------------

    @Test
    void backgroundStepsRunBeforeEachScenarioAndAreIncludedInStepResults() {
        String text = """
                Feature: Background handling

                  Background:
                    Given the FIX session is disconnected

                  Scenario: First scenario
                    Then the session should not be connected

                  Scenario: Second scenario
                    Then the sent orders count should be at least 0
                """;

        JsonObject result = runner.run(text, null);

        assertEquals("PASSED", result.getString("status"));
        assertEquals(2, result.getInteger("totalScenarios"));

        for (int s = 0; s < 2; s++) {
            JsonObject scenario = result.getJsonArray("scenarios").getJsonObject(s);
            assertEquals("PASSED", scenario.getString("status"));
            // Each scenario should have 2 steps: 1 background + 1 scenario step
            assertEquals(2, scenario.getJsonArray("steps").size(),
                    "Scenario " + s + " should have background step + scenario step");
            assertEquals("PASSED", scenario.getJsonArray("steps").getJsonObject(0).getString("status"),
                    "Background step in scenario " + s + " should be PASSED");
        }
    }

    @Test
    void failedBackgroundStepCausesScenarioStepsToBeSkipped() {
        String text = """
                Feature: Background failure

                  Background:
                    Given this background step has no match

                  Scenario: Affected scenario
                    Then the session should not be connected
                """;

        JsonObject result = runner.run(text, null);

        assertEquals("FAILED", result.getString("status"));
        JsonObject scenario = result.getJsonArray("scenarios").getJsonObject(0);
        assertEquals("FAILED", scenario.getString("status"));
        JsonArray steps = scenario.getJsonArray("steps");
        assertEquals(2, steps.size());
        assertEquals("FAILED",  steps.getJsonObject(0).getString("status")); // background
        assertEquals("SKIPPED", steps.getJsonObject(1).getString("status")); // scenario step skipped
    }

    // -----------------------------------------------------------------------
    // Multiple scenarios – PASSED + FAILED counted correctly
    // -----------------------------------------------------------------------

    @Test
    void overallStatusIsFailedIfAnyScenarioFails() {
        String text = """
                Feature: Mixed results

                  Scenario: Passes
                    Then the session should not be connected

                  Scenario: Fails
                    Then this step has no match
                """;

        JsonObject result = runner.run(text, null);

        assertEquals("FAILED", result.getString("status"));
        assertEquals(2, result.getInteger("totalScenarios"));
        assertEquals(1, result.getInteger("passed"));
        assertEquals(1, result.getInteger("failed"));
    }

    @Test
    void overallStatusIsPassedWhenAllScenariosPass() {
        String text = """
                Feature: All pass

                  Scenario: Pass 1
                    Then the session should not be connected

                  Scenario: Pass 2
                    Then the sent orders count should be at least 0
                """;

        JsonObject result = runner.run(text, null);

        assertEquals("PASSED", result.getString("status"));
        assertEquals(2, result.getInteger("totalScenarios"));
        assertEquals(2, result.getInteger("passed"));
        assertEquals(0, result.getInteger("failed"));
    }

    // -----------------------------------------------------------------------
    // Scenario Outline – single Examples block
    // -----------------------------------------------------------------------

    @Test
    void scenarioOutlineWithSingleExamplesBlockExpandsIntoOneScenarioPerRow() {
        String text = """
                Feature: Scenario Outline

                  Scenario Outline: Verify count threshold <threshold>
                    Then the sent orders count should be at least <threshold>

                    Examples:
                      | threshold |
                      | 0         |
                      | 0         |
                      | 0         |
                """;

        JsonObject result = runner.run(text, null);

        assertEquals(3, result.getInteger("totalScenarios"),
                "Should expand 3 data rows to 3 scenarios");
        assertEquals("PASSED", result.getString("status"));
        assertEquals(3, result.getInteger("passed"));
    }

    @Test
    void scenarioOutlineSubstitutesPlaceholdersIntoExpandedSteps() {
        String text = """
                Feature: Placeholder substitution

                  Scenario Outline: Wait step with <seconds> second(s)
                    When I wait <seconds> seconds

                    Examples:
                      | seconds |
                      | 0       |
                """;

        JsonObject result = runner.run(text, null);

        assertEquals(1, result.getInteger("totalScenarios"));
        assertEquals("PASSED", result.getString("status"));
        JsonObject step = result.getJsonArray("scenarios").getJsonObject(0).getJsonArray("steps").getJsonObject(0);
        assertEquals("I wait 0 seconds", step.getString("text"),
                "Placeholder <seconds> should be replaced with 0");
    }

    // -----------------------------------------------------------------------
    // Scenario Outline – multiple Examples blocks
    // -----------------------------------------------------------------------

    @Test
    void scenarioOutlineWithMultipleExamplesBlocksExpandsAllRows() {
        String text = """
                Feature: Multiple Examples blocks

                  Scenario Outline: Verify disconnect (<case>)
                    Given the FIX session is disconnected
                    Then the session should not be connected

                    Examples: Group A
                      | case |
                      | A1   |
                      | A2   |

                    Examples: Group B
                      | case |
                      | B1   |
                """;

        JsonObject result = runner.run(text, null);

        // 2 from Group A + 1 from Group B = 3 total
        assertEquals(3, result.getInteger("totalScenarios"),
                "Should expand rows from all Examples blocks: 2 + 1 = 3");
        assertEquals("PASSED", result.getString("status"));
    }

    // -----------------------------------------------------------------------
    // Comments and tags are ignored
    // -----------------------------------------------------------------------

    @Test
    void commentsAndTagLinesAreIgnoredDuringParsing() {
        String text = """
                # Top-level comment
                Feature: Comment handling

                  # Feature comment
                  @smoke
                  Scenario: Tagged scenario
                    # Step comment
                    @tag
                    Then the session should not be connected
                """;

        JsonObject result = runner.run(text, null);

        assertEquals("PASSED", result.getString("status"));
        assertEquals(1, result.getInteger("totalScenarios"));

        // The step list should only contain the actual step, not the comment/tag lines
        JsonArray steps = result.getJsonArray("scenarios").getJsonObject(0).getJsonArray("steps");
        assertEquals(1, steps.size());
        assertEquals("the session should not be connected", steps.getJsonObject(0).getString("text"));
    }

    // -----------------------------------------------------------------------
    // Given / When / Then / And / But keywords
    // -----------------------------------------------------------------------

    @Test
    void allGherkinStepKeywordsAreRecognised() {
        String text = """
                Feature: Keywords

                  Scenario: All keywords
                    Given the FIX session is disconnected
                    When I wait 0 seconds
                    Then the session should not be connected
                    And the sent orders count should be at least 0
                    But the sent orders count should be at least 0
                """;

        JsonObject result = runner.run(text, null);

        assertEquals("PASSED", result.getString("status"));
        JsonArray steps = result.getJsonArray("scenarios").getJsonObject(0).getJsonArray("steps");
        assertEquals(5, steps.size());
        assertEquals("Given", steps.getJsonObject(0).getString("keyword"));
        assertEquals("When",  steps.getJsonObject(1).getString("keyword"));
        assertEquals("Then",  steps.getJsonObject(2).getString("keyword"));
        assertEquals("And",   steps.getJsonObject(3).getString("keyword"));
        assertEquals("But",   steps.getJsonObject(4).getString("keyword"));
    }

    // -----------------------------------------------------------------------
    // Step result fields are all populated
    // -----------------------------------------------------------------------

    @Test
    void stepResultContainsKeywordTextStatusAndMessageFields() {
        String text = """
                Feature: Step result fields

                  Scenario: Basic step
                    Then the session should not be connected
                """;

        JsonObject result = runner.run(text, null);

        JsonObject step = result.getJsonArray("scenarios").getJsonObject(0).getJsonArray("steps").getJsonObject(0);
        assertNotNull(step.getString("keyword"),  "keyword must be present");
        assertNotNull(step.getString("text"),     "text must be present");
        assertNotNull(step.getString("status"),   "status must be present");
        assertNotNull(step.getString("message"),  "message must be present");
        assertFalse(step.getString("message").isBlank(), "message must not be blank on PASSED step");
    }

    // -----------------------------------------------------------------------
    // profileName is forwarded to snapshot calls
    // -----------------------------------------------------------------------

    @Test
    void nullOrBlankProfileNameDoesNotCauseParserToThrow() {
        String text = """
                Feature: Null profile

                  Scenario: Works with null profile
                    Then the session should not be connected
                """;

        // should not throw
        JsonObject result1 = runner.run(text, null);
        JsonObject result2 = runner.run(text, "");
        JsonObject result3 = runner.run(text, "  ");

        assertEquals("PASSED", result1.getString("status"));
        assertEquals("PASSED", result2.getString("status"));
        assertEquals("PASSED", result3.getString("status"));
    }

    // -----------------------------------------------------------------------
    // Scenario name propagated to result
    // -----------------------------------------------------------------------

    @Test
    void scenarioNamesArePropagatedToResults() {
        String text = """
                Feature: Scenario names

                  Scenario: My specific scenario name
                    Then the session should not be connected
                """;

        JsonObject result = runner.run(text, null);

        String name = result.getJsonArray("scenarios").getJsonObject(0).getString("name");
        assertEquals("My specific scenario name", name);
    }

    // -----------------------------------------------------------------------
    // Market-specific order step resolves region from market code
    // -----------------------------------------------------------------------

    @Test
    void marketSpecificOrderStepDerivesEmeaRegionFromXlonMarketCode() {
        // Before the fix, "on market XLON" sent region=AMERICAS+market=XLON which caused
        // buildPreview to add a "Select a valid market for the chosen region" warning and
        // return early WITHOUT calling the service layer → sendFailures stays 0.
        // After the fix, regionForMarket("XLON") returns "EMEA", validation passes, and
        // sendOrderInternal IS called (but fails because no live FIX session) → sendFailures=1.
        String text = """
                Feature: Market routing

                  Scenario: Send order on London Stock Exchange (EMEA market)
                    When I send a New Order Single for 100 shares of "BP.L" on market "XLON"
                """;

        runner.run(text, null);

        int sendFailures = state.snapshot().getJsonObject("kpis", new JsonObject()).getInteger("sendFailures", -1);
        assertEquals(1, sendFailures,
                "EMEA/XLON should pass validation and reach the service layer (sendFailures must be 1, not 0)");
    }

    @Test
    void marketSpecificOrderStepDerivesAsiaRegionFromXtksMarketCode() {
        String text = """
                Feature: Market routing

                  Scenario: Send order on Tokyo Stock Exchange (ASIA market)
                    When I send a New Order Single for 50 shares of "7203.T" on market "XTKS"
                """;

        runner.run(text, null);

        int sendFailures = state.snapshot().getJsonObject("kpis", new JsonObject()).getInteger("sendFailures", -1);
        assertEquals(1, sendFailures,
                "ASIA/XTKS should pass validation and reach the service layer (sendFailures must be 1, not 0)");
    }
}

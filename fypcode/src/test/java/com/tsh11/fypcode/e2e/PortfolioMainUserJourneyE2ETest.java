package com.tsh11.fypcode.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PortfolioMainUserJourneyE2ETest {

    private static final String LOGIN_URL = "http://localhost:8080/login";
    private static final String BASE_URL = "http://localhost:8080";

    private static final String TEST_USERNAME = "test1@example.com";
    private static final String TEST_PASSWORD = "12345678";

    private static final String OPTIMISATION_FROM_DATE = "2024-01-01";
    private static final String OPTIMISATION_TO_DATE = "2026-06-07";

    private static Playwright playwright;
    private static Browser browser;

    private BrowserContext context;
    private Page page;

    // Starts the shared Playwright browser before all E2E tests.
    @BeforeAll
    static void setUpBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(true)
        );
    }

    // Closes the shared browser and Playwright instance after all E2E tests.
    @AfterAll
    static void tearDownBrowser() {
        if (browser != null) {
            browser.close();
        }

        if (playwright != null) {
            playwright.close();
        }
    }

    // Creates a fresh browser context and page before each test.
    @BeforeEach
    void setUpPage() {
        context = browser.newContext(
                new Browser.NewContextOptions()
                        .setAcceptDownloads(true)
        );
        page = context.newPage();
    }

    // Closes the browser context after each test.
    @AfterEach
    void tearDownPage() {
        if (context != null) {
            context.close();
        }
    }

    // Runs the full main user journey described in the dissertation E2E scenario.
    @Test
    void mainUserJourney_loginNavigateOptimiseAndExportPdf() {
        openLoginPage();
        loginWithPopulatedTestAccount();

        navigateToHoldingsPage();
        navigateToAllocationPage();
        navigateToAnalysisPage();

        runMeanVarianceOptimisation();
        runRiskParityOptimisation();

        assertEfficientFrontierCardHiddenForRiskParity();

        assertPdfDownloadTriggered();
    }

    // Opens the login page and checks that the login form is displayed.
    private void openLoginPage() {
        page.navigate(LOGIN_URL);

        assertTrue(page.locator("form[action='/login']").isVisible(),
                "Login form should be displayed.");

        assertTrue(page.locator("#username").isVisible(),
                "Username/email field should be displayed.");

        assertTrue(page.locator("#password").isVisible(),
                "Password field should be displayed.");
    }

    // Logs in using the populated test account and checks that portfolio pages become accessible.
    private void loginWithPopulatedTestAccount() {
        page.locator("#username").fill(TEST_USERNAME);
        page.locator("#password").fill(TEST_PASSWORD);

        page.locator("button[type='submit']").click();

        page.waitForURL(url -> !url.equals(LOGIN_URL));

        assertTrue(page.locator("body").textContent().contains("Portfolio")
                        || page.locator("body").textContent().contains("Overview")
                        || page.locator("body").textContent().contains("Hello"),
                "User should be authenticated and able to access portfolio pages.");
    }

    // Navigates to the holdings page and checks that it loads successfully.
    private void navigateToHoldingsPage() {
        clickNavigationLink("Holdings");

        page.waitForURL("**/portfolio/holdings");

        assertTrue(page.locator("body").textContent().contains("Holdings"),
                "Holdings page should load successfully.");
    }

    // Navigates to the allocation page and checks that it loads successfully.
    private void navigateToAllocationPage() {
        clickNavigationLink("Allocation");

        page.waitForURL("**/portfolio/allocation");

        assertTrue(page.locator("body").textContent().contains("Allocation"),
                "Allocation page should load successfully.");
    }

    // Navigates to the analysis page and checks that the optimisation form is visible.
    private void navigateToAnalysisPage() {
        clickNavigationLink("Analysis");

        page.waitForURL("**/portfolio/analysis");

        assertTrue(page.locator("body").textContent().contains("Portfolio Analysis"),
                "Analysis page should load successfully.");

        assertTrue(page.locator("#optimisationForm").isVisible(),
                "Optimisation form should be visible on the analysis page.");
    }

    // Runs Mean-Variance optimisation and checks that optimisation result content appears.
    private void runMeanVarianceOptimisation() {
        setOptimisationDates();

        page.locator("#optimisationAlgorithm").selectOption("MEAN_VARIANCE");
        page.locator("#runOptimisationButton").click();

        page.locator("#optimisationContent").waitFor(
                new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
        );

        assertFalse(page.locator("#optimisationContent").getAttribute("class").contains("d-none"),
                "Mean-Variance optimisation result content should appear.");

        assertTrue(page.locator("#optimisationAllocationTableBody").textContent().length() > 0,
                "Mean-Variance allocation result table should contain content.");

        assertTrue(page.locator("#mptOptimisedSharpe").textContent().trim().length() > 0,
                "Mean-Variance optimised Sharpe ratio should be displayed.");
    }

    // Runs Risk Parity optimisation and checks that Risk Parity result content appears.
    private void runRiskParityOptimisation() {
        setOptimisationDates();

        page.locator("#optimisationAlgorithm").selectOption("RISK_PARITY");
        page.locator("#runOptimisationButton").click();

        page.locator("#optimisationContent").waitFor(
                new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
        );

        assertFalse(page.locator("#optimisationContent").getAttribute("class").contains("d-none"),
                "Risk Parity optimisation result content should appear.");

        assertTrue(page.locator("#optimisationAllocationTableBody").textContent().length() > 0,
                "Risk Parity allocation result table should contain content.");

        assertTrue(page.locator("#mptOptimisedSharpe").textContent().trim().length() > 0,
                "Risk Parity optimised Sharpe ratio should be displayed.");
    }

    // Checks that the Efficient Frontier card is hidden after running Risk Parity.
    private void assertEfficientFrontierCardHiddenForRiskParity() {
        String classAttribute = page.locator("#efficientFrontierCard").getAttribute("class");

        assertTrue(classAttribute != null && classAttribute.contains("d-none"),
                "Efficient Frontier card should be hidden for Risk Parity.");
    }

    // Clicks the export button and checks that a PDF download is triggered.
    private void assertPdfDownloadTriggered() {
        Download download = page.waitForDownload(() ->
                page.locator("#exportPdfButton").click()
        );

        assertTrue(download.suggestedFilename().endsWith(".pdf"),
                "PDF download should be triggered.");

        download.saveAs(Paths.get("target/e2e-downloads", download.suggestedFilename()));
    }

    // Fills the optimisation date range used by both optimisation algorithm runs.
    private void setOptimisationDates() {
        page.locator("#optimisationFromDate").fill(OPTIMISATION_FROM_DATE);
        page.locator("#optimisationToDate").fill(OPTIMISATION_TO_DATE);
    }

    // Clicks a page navigation link by its visible text.
    private void clickNavigationLink(String linkText) {
        page.getByRole(
                AriaRole.LINK,
                new Page.GetByRoleOptions().setName(linkText)
        ).click();
    }
}
/*
Overview page controller.
This file controls the dynamic behaviour of overview.html.

Main responsibilities:
1. Load portfolio summary data from /api/portfolio/overview.
2. Load assistant data from /api/portfolio/assistant.
3. Render summary cards, assistant allocation comparison, and insight messages.
4. Show loading, empty, and error states.
5. Show the new-user onboarding modal when both profile and portfolio data are missing.

The frontend only renders data. Portfolio calculations are done by backend services.
*/
document.addEventListener("DOMContentLoaded", () => {
    // Backend endpoints used by this page.
    // /api/portfolio/overview returns portfolio totals.
    // /api/portfolio/assistant returns investor-profile-based suitability data.
    const apiUrl = "/api/portfolio/overview";
    const assistantApiUrl = "/api/portfolio/assistant";

    // Main overview DOM elements.
    // These IDs must match the elements defined in overview.html.
    const loadingState = document.getElementById("loadingState");
    const overviewContent = document.getElementById("overviewContent");
    const emptyState = document.getElementById("emptyState");
    const alertContainer = document.getElementById("alertContainer");

    // Portfolio summary card elements.
    // These are filled using the response from /api/portfolio/overview.
    const totalHoldingsCount = document.getElementById("totalHoldingsCount");
    const totalQuantity = document.getElementById("totalQuantity");
    const totalCostBasis = document.getElementById("totalCostBasis");
    const totalMarketValue = document.getElementById("totalMarketValue");
    const totalUnrealizedGainLoss = document.getElementById("totalUnrealizedGainLoss");
    const totalUnrealizedGainLossPercent = document.getElementById("totalUnrealizedGainLossPercent");
    const baseCurrency = document.getElementById("baseCurrency");
    const portfolioStatus = document.getElementById("portfolioStatus");

    // Assistant DOM elements.
    // These are filled using the response from /api/portfolio/assistant.
    const assistantLoadingState = document.getElementById("assistantLoadingState");
    const assistantContent = document.getElementById("assistantContent");
    const assistantEmptyState = document.getElementById("assistantEmptyState");
    const assistantRiskProfile = document.getElementById("assistantRiskProfile");
    const assistantRiskScore = document.getElementById("assistantRiskScore");
    const assistantTargetAllocation = document.getElementById("assistantTargetAllocation");
    const assistantActualAllocation = document.getElementById("assistantActualAllocation");
    const assistantComparisonTableBody = document.getElementById("assistantComparisonTableBody");
    const assistantInsightsList = document.getElementById("assistantInsightsList");

    // New-user onboarding modal elements.
    // The modal appears only when both overview data and investor profile are missing.
    const newUserOnboardingModal = document.getElementById("newUserOnboardingModal");
    const dismissNewUserOnboardingButton = document.getElementById("dismissNewUserOnboardingButton");
    const onboardingDismissedKey = "portfolioOverviewInvestorProfileOnboardingDismissed";

    // These flags are set after the two API calls finish.
    // The onboarding modal only appears when:
    // overviewHasNoData === true AND assistantProfileMissing === true.
    let overviewHasNoData = null;
    let assistantProfileMissing = null;

    // If the user dismisses the onboarding modal, remember it for this browser session.
    // This prevents the popup from repeatedly appearing after "Ignore and continue".
    if (dismissNewUserOnboardingButton) {
        dismissNewUserOnboardingButton.addEventListener("click", () => {
            setSessionFlag(onboardingDismissedKey, "true");
        });
    }

    // Load the two independent parts of the Overview page.
    // They are separate API calls so the portfolio summary and assistant summary can be handled independently.
    loadOverview();
    loadAssistant();

    /*
    Loads portfolio summary data.

    Flow:
    1. Show loading state.
    2. Call GET /api/portfolio/overview.
    3. If there are no holdings, show the empty state.
    4. If data exists, render the overview summary cards.
    5. Check whether the new-user onboarding modal should be shown.
    */
    async function loadOverview() {
        showLoading();
        clearAlert();

        try {
            const response = await fetch(apiUrl, {
                method: "GET",
                headers: {
                    "Accept": "application/json"
                }
            });

            if (!response.ok) {
                throw new Error(`Failed to load overview. HTTP ${response.status}`);
            }

            const overview = await response.json();

            // If the user has no holdings, there is no meaningful overview summary yet.
            // Show the empty state and record this for onboarding modal logic.
            if (!overview || Number(overview.totalHoldingsCount ?? 0) === 0) {
                overviewHasNoData = true;
                showEmpty();
                maybeShowNewUserOnboarding();
                return;
            }

            overviewHasNoData = false;
            renderOverview(overview);
            maybeShowNewUserOnboarding();
        } catch (error) {
            console.error("Error loading overview:", error);
            showError("Unable to load portfolio overview right now. Please try again.");
            hideLoading();
        }
    }

    /*
    Renders the main portfolio summary cards.
    The values are calculated by backend OverviewService and only formatted/displayed here.
    */
    function renderOverview(overview) {
        hideLoading();
        overviewContent.classList.remove("d-none");
        emptyState.classList.add("d-none");

        const currency = overview.baseCurrency ?? "";

        totalHoldingsCount.textContent = String(overview.totalHoldingsCount ?? 0);
        totalQuantity.textContent = formatNumber(overview.totalQuantity, 6);
        totalCostBasis.textContent = formatMoney(overview.totalCostBasis, currency);
        totalMarketValue.textContent = formatMoney(overview.totalMarketValue, currency);

        // Colour-code profit/loss values for readability.
        // Positive values use green, negative values use red.
        totalUnrealizedGainLoss.textContent = formatMoney(overview.totalUnrealizedGainLoss, currency);
        totalUnrealizedGainLoss.className = `fs-4 fw-semibold ${gainLossClass(overview.totalUnrealizedGainLoss)}`;

        totalUnrealizedGainLossPercent.textContent = formatPercent(overview.totalUnrealizedGainLossPercent);
        totalUnrealizedGainLossPercent.className = `fs-4 fw-semibold ${gainLossClass(overview.totalUnrealizedGainLossPercent)}`;

        baseCurrency.textContent = currency || "-";
        portfolioStatus.textContent = buildPortfolioStatus(overview);
    }

    /*
    Builds a simple readable status from unrealised gain/loss.
    This is display text only; the actual gain/loss value is calculated by the backend.
    */
    function buildPortfolioStatus(overview) {
        const gainLoss = Number(overview.totalUnrealizedGainLoss ?? 0);

        if (!Number.isFinite(gainLoss) || gainLoss === 0) {
            return "Break-even or market data not available yet";
        }

        if (gainLoss > 0) {
            return "Portfolio is currently in profit";
        }

        return "Portfolio is currently in loss";
    }

    // Display-formatting helper.
    // This does not change the calculated value; it only formats it for the page.
    function formatMoney(value, currency) {
        const number = Number(value ?? 0);

        if (!Number.isFinite(number)) {
            return "-";
        }

        if (!currency) {
            return number.toFixed(2);
        }

        try {
            return new Intl.NumberFormat(undefined, {
                style: "currency",
                currency: currency,
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            }).format(number);
        } catch (error) {
            return `${number.toFixed(2)} ${currency}`;
        }
    }

    // Display-formatting helper for ordinary numbers such as total quantity.
    function formatNumber(value, fractionDigits = 2) {
        const number = Number(value ?? 0);

        if (!Number.isFinite(number)) {
            return "-";
        }

        return new Intl.NumberFormat(undefined, {
            minimumFractionDigits: 0,
            maximumFractionDigits: fractionDigits
        }).format(number);
    }

    // Display-formatting helper for percentage values already returned as percentage-point values by the backend.
    function formatPercent(value) {
        const number = Number(value ?? 0);

        if (!Number.isFinite(number)) {
            return "-";
        }

        return `${number.toFixed(2)}%`;
    }

    // Returns Bootstrap text colour class for gain/loss display.
    function gainLossClass(value) {
        const number = Number(value ?? 0);

        if (!Number.isFinite(number) || number === 0) {
            return "text-body";
        }

        return number > 0 ? "text-success" : "text-danger";
    }

    /*
    Loads Financial Consultant Assistant data.

    Flow:
    1. Call GET /api/portfolio/assistant.
    2. If no investor profile exists, show assistant empty state.
    3. If profile exists, render risk profile, target allocation, actual allocation, comparisons and insights.
    4. Check whether onboarding modal should be shown.
    */
    async function loadAssistant() {
        showAssistantLoading();

        try {
            const response = await fetch(assistantApiUrl, {
                method: "GET",
                headers: {
                    "Accept": "application/json"
                }
            });

            if (!response.ok) {
                throw new Error(`Failed to load assistant insights. HTTP ${response.status}`);
            }

            const assistant = await response.json();

            // If no investor profile exists, the assistant cannot calculate suitability insights.
            // Show the assistant empty state and record this for onboarding modal logic.
            if (!assistant || !assistant.profileExists) {
                assistantProfileMissing = true;
                showAssistantEmpty();
                maybeShowNewUserOnboarding();
                return;
            }

            assistantProfileMissing = false;
            renderAssistant(assistant);
            maybeShowNewUserOnboarding();
        } catch (error) {
            console.error("Error loading assistant insights:", error);
            showAssistantEmpty();
        }
    }

    /*
    Renders the assistant summary.
    Risk profile comes from investor profile.
    Target allocation comes from the rule-based target allocation service.
    Actual allocation comes from current holdings mapped into assistant buckets.
    */
    function renderAssistant(assistant) {
        hideAssistantLoading();
        assistantContent.classList.remove("d-none");
        assistantEmptyState.classList.add("d-none");

        assistantRiskProfile.textContent = formatEnum(assistant.riskProfileType);
        assistantRiskScore.textContent = `Risk score: ${assistant.riskScore ?? 0}/100`;

        const target = assistant.targetAllocation || {};
        const actual = assistant.actualAllocation || {};

        // Target allocation is the recommended equity/bond/cash mix for the user's risk profile.
        assistantTargetAllocation.innerHTML = `
            <div>Equity: ${formatPercent(target.equityPercent)}</div>
            <div>Bond: ${formatPercent(target.bondPercent)}</div>
            <div>Cash: ${formatPercent(target.cashPercent)}</div>
        `;

        // Actual allocation is the user's current portfolio mapped into assistant buckets.
        assistantActualAllocation.innerHTML = `
            <div>Equity: ${formatPercent(actual.equityPercent)}</div>
            <div>Bond: ${formatPercent(actual.bondPercent)}</div>
            <div>Cash: ${formatPercent(actual.cashPercent)}</div>
            <div>Crypto: ${formatPercent(actual.cryptoPercent)}</div>
            <div>Real estate: ${formatPercent(actual.realEstatePercent)}</div>
            <div>Other: ${formatPercent(actual.otherPercent)}</div>
            <div>Unclassified: ${formatPercent(actual.unclassifiedPercent)}</div>
        `;

        renderAssistantComparisons(assistant.comparisons || []);
        renderAssistantInsights(assistant.insights || []);
    }

    /*
    Renders assistant insight messages.
    The backend returns insight severity and message text.
    The frontend maps severity to Bootstrap alert colours.
    */
    function renderAssistantInsights(insights) {
        if (!assistantInsightsList) {
            return;
        }

        if (!insights.length) {
            assistantInsightsList.innerHTML = `
                <div class="alert alert-success mb-0" role="alert">
                    No major suitability warnings were found for the current portfolio.
                </div>
            `;
            return;
        }

        assistantInsightsList.innerHTML = insights.map(insight => {
            const severity = String(insight.severity || "INFO").toUpperCase();
            const title = insight.title || formatEnum(severity);
            const message = insight.message || insight.text || insight.description || "";
            const alertClass = assistantInsightClass(severity);

            return `
                <div class="alert ${alertClass} mb-0" role="alert">
                    <div class="fw-semibold">${escapeHtml(title)}</div>
                    <div>${escapeHtml(message)}</div>
                </div>
            `;
        }).join("");
    }

    // Maps backend insight severity from AssistantInsightService to Bootstrap alert colour.
    function assistantInsightClass(severity) {
        switch (severity) {
            case "CRITICAL":
            case "HIGH":
                return "alert-danger";
            case "WARNING":
            case "MEDIUM":
                return "alert-warning";
            case "SUCCESS":
                return "alert-success";
            case "INFO":
            default:
                return "alert-info";
        }
    }

    /*
    Renders target-vs-actual allocation comparison rows.
    Each row shows the bucket, target %, actual %, difference %, and status.
    */
    function renderAssistantComparisons(comparisons) {
        if (!comparisons.length) {
            assistantComparisonTableBody.innerHTML = `<tr><td colspan="5" class="text-muted">No comparison available.</td></tr>`;
            return;
        }

        assistantComparisonTableBody.innerHTML = comparisons.map(item => `
            <tr>
                <td>${escapeHtml(formatEnum(item.bucket))}</td>
                <td class="text-end">${formatPercent(item.targetPercent)}</td>
                <td class="text-end">${formatPercent(item.actualPercent)}</td>
                <td class="text-end ${assistantDifferenceClass(item.differencePercent)}">${formatSignedPercent(item.differencePercent)}</td>
                <td>${escapeHtml(formatEnum(item.status))}</td>
            </tr>
        `).join("");
    }

    // Colour-codes assistant allocation differences.
    // Positive difference is green and negative difference is red.
    function assistantDifferenceClass(value) {
        const number = Number(value ?? 0);
        if (!Number.isFinite(number) || number === 0) {
            return "";
        }
        return number > 0 ? "text-success" : "text-danger";
    }

    // Formats a percentage and adds + for positive values.
    function formatSignedPercent(value) {
        const number = Number(value ?? 0);
        if (!Number.isFinite(number)) {
            return "-";
        }
        const sign = number > 0 ? "+" : "";
        return `${sign}${number.toFixed(2)}%`;
    }

    // Converts backend enum values such as BALANCED_GROWTH into readable text such as "Balanced Growth".
    function formatEnum(value) {
        if (!value) {
            return "-";
        }
        return String(value)
            .toLowerCase()
            .split("_")
            .map(part => part.charAt(0).toUpperCase() + part.slice(1))
            .join(" ");
    }

    /*
    Shows the new-user onboarding modal only when:
    1. the user has no overview data / holdings, and
    2. the user has no investor profile, and
    3. the user has not dismissed the modal in this browser session.

    This guides new users to complete the investor profile first.
    */
    function maybeShowNewUserOnboarding() {
        if (overviewHasNoData !== true || assistantProfileMissing !== true) {
            return;
        }

        if (getSessionFlag(onboardingDismissedKey) === "true") {
            return;
        }

        if (!newUserOnboardingModal || typeof bootstrap === "undefined" || !bootstrap.Modal) {
            return;
        }

        const modal = bootstrap.Modal.getOrCreateInstance(newUserOnboardingModal);
        modal.show();
    }

    // Safely reads a browser session flag.
    function getSessionFlag(key) {
        try {
            return sessionStorage.getItem(key);
        } catch (error) {
            return null;
        }
    }

    // Safely writes a browser session flag.
    function setSessionFlag(key, value) {
        try {
            sessionStorage.setItem(key, value);
        } catch (error) {
            // If sessionStorage is unavailable, the dismiss button still closes the modal normally.
        }
    }

    // UI state helper for the assistant loading state.
    function showAssistantLoading() {
        if (!assistantLoadingState) {
            return;
        }
        assistantLoadingState.classList.remove("d-none");
        assistantContent.classList.add("d-none");
        assistantEmptyState.classList.add("d-none");
    }

    // UI state helper for hiding assistant loading text.
    function hideAssistantLoading() {
        if (assistantLoadingState) {
            assistantLoadingState.classList.add("d-none");
        }
    }

    // UI state helper shown when no investor profile exists or assistant loading fails.
    function showAssistantEmpty() {
        hideAssistantLoading();
        assistantContent.classList.add("d-none");
        assistantEmptyState.classList.remove("d-none");
    }

    // UI state helper for the main overview loading state.
    function showLoading() {
        loadingState.classList.remove("d-none");
        overviewContent.classList.add("d-none");
        emptyState.classList.add("d-none");
    }

    // UI state helper for hiding the main overview loading state.
    function hideLoading() {
        loadingState.classList.add("d-none");
    }

    // UI state helper shown when the user has no holdings/overview data.
    function showEmpty() {
        hideLoading();
        overviewContent.classList.add("d-none");
        emptyState.classList.remove("d-none");
    }

    // Shows a dismissible Bootstrap error alert if overview loading fails.
    function showError(message) {
        alertContainer.innerHTML = `
            <div class="alert alert-danger alert-dismissible fade show" role="alert">
                ${escapeHtml(message)}
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            </div>
        `;
    }

    // Clears any previously displayed overview error message.
    function clearAlert() {
        alertContainer.innerHTML = "";
    }

    // Escapes text before inserting it into innerHTML to reduce unsafe HTML injection risk.
    function escapeHtml(value) {
        return String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }
});
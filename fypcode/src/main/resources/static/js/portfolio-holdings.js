/*
Holdings page controller.
This file controls the dynamic behaviour of holdings.html.

Main responsibilities:
1. Load backend-derived holdings from GET /api/portfolio/holdings.
2. Render the returned HoldingResponse list into the holdings table.
3. Update summary cards by summing values from the returned holdings list.
4. Format money, quantity and percentage values for display.
5. Colour-code gain/loss values.
6. Show loading, empty and error states.

Important design point:
The frontend does not process BUY/SELL activities or calculate holdings.
HoldingService on the backend derives holdings from activities.
*/
document.addEventListener("DOMContentLoaded", () => {
    // Backend endpoint used by this page.
    // It returns the current holdings derived from the user's activity records.
    const apiUrl = "/api/portfolio/holdings";

    // Main page state elements.
    // These IDs must match elements in holdings.html.
    const loadingState = document.getElementById("loadingState");
    const emptyState = document.getElementById("emptyState");
    const tableContainer = document.getElementById("tableContainer");
    const tableBody = document.getElementById("holdingsTableBody");
    const alertContainer = document.getElementById("alertContainer");
    const refreshButton = document.getElementById("refreshButton");

    // Summary card elements.
    // These are filled after holdings are loaded.
    const summaryHoldingsCount = document.getElementById("summaryHoldingsCount");
    const summaryTotalCost = document.getElementById("summaryTotalCost");
    const summaryMarketValue = document.getElementById("summaryMarketValue");
    const summaryGainLoss = document.getElementById("summaryGainLoss");

    // Refresh button simply reloads the backend-derived holdings.
    refreshButton.addEventListener("click", loadHoldings);

    // Initial load when the page opens.
    loadHoldings();

    /*
    Loads holdings from the backend.

    Flow:
    1. Show loading state.
    2. Clear previous error alert.
    3. Call GET /api/portfolio/holdings.
    4. Parse the JSON HoldingResponse list.
    5. Render the holdings table and summary cards.
    */
    async function loadHoldings() {
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
                throw new Error(`Failed to load holdings. HTTP ${response.status}`);
            }

            const holdings = await response.json();
            renderHoldings(holdings);
        } catch (error) {
            console.error("Error loading holdings:", error);
            showError("Unable to load holdings right now. Please try again.");
            showEmpty(false);
        }
    }

    /*
    Renders the holdings table.

    Important:
    The holdings are already calculated by the backend HoldingService.
    This function only displays each returned holding and then updates summary cards.
    */
    function renderHoldings(holdings) {
        hideLoading();
        tableBody.innerHTML = "";

        if (!Array.isArray(holdings) || holdings.length === 0) {
            updateSummary([]);
            showEmpty(true);
            return;
        }

        showTable();

        holdings.forEach(holding => {
            const row = document.createElement("tr");

            /*
            Each row displays one HoldingResponse.

            Main fields:
            - symbol: asset identifier
            - totalQuantity: quantity currently held
            - averageCost: average cost basis per unit
            - totalCost: total cost basis
            - latestPrice: latest market/manual price
            - marketValue: current value
            - unrealizedGainLoss: market value minus cost basis
            - unrealizedGainLossPercent: gain/loss relative to cost basis
            - currency: display currency
            */
            row.innerHTML = `
                <td>${escapeHtml(holding.symbol ?? "-")}</td>
                <td class="text-end">${formatNumber(holding.totalQuantity, 6)}</td>
                <td class="text-end">${formatMoney(holding.averageCost, holding.currency)}</td>
                <td class="text-end">${formatMoney(holding.totalCost, holding.currency)}</td>
                <td class="text-end">${formatMoney(holding.latestPrice, holding.currency)}</td>
                <td class="text-end">${formatMoney(holding.marketValue, holding.currency)}</td>
                <td class="text-end ${gainLossClass(holding.unrealizedGainLoss)}">
                    ${formatMoney(holding.unrealizedGainLoss, holding.currency)}
                </td>
                <td class="text-end ${gainLossClass(holding.unrealizedGainLossPercent)}">
                    ${formatPercent(holding.unrealizedGainLossPercent)}
                </td>
                <td>${escapeHtml(holding.currency ?? "-")}</td>
            `;

            tableBody.appendChild(row);
        });

        updateSummary(holdings);
    }

    /*
    Updates the summary cards at the top of the page.

    Important:
    These are display summaries calculated from already-derived holdings.
    They are not recalculating BUY/SELL transaction logic.
    */
    function updateSummary(holdings) {
        summaryHoldingsCount.textContent = holdings.length.toString();

        const totalCost = sumBy(holdings, "totalCost");
        const totalMarketValue = sumBy(holdings, "marketValue");
        const totalGainLoss = sumBy(holdings, "unrealizedGainLoss");

        /*
        The first detected currency is used for display.
        This does not perform currency conversion.
        */
        const primaryCurrency = detectPrimaryCurrency(holdings);

        summaryTotalCost.textContent = formatMoney(totalCost, primaryCurrency);
        summaryMarketValue.textContent = formatMoney(totalMarketValue, primaryCurrency);
        summaryGainLoss.textContent = formatMoney(totalGainLoss, primaryCurrency);
        summaryGainLoss.className = `fs-4 fw-semibold ${gainLossClass(totalGainLoss)}`;
    }

    /*
    Sums a numeric field across all returned holdings.
    Missing or invalid values are treated as zero.
    */
    function sumBy(items, fieldName) {
        return items.reduce((sum, item) => {
            const value = Number(item[fieldName] ?? 0);
            return sum + (Number.isFinite(value) ? value : 0);
        }, 0);
    }

    /*
    Chooses a display currency from the first holding that has a currency.
    This is only a display helper and does not convert multi-currency holdings.
    */
    function detectPrimaryCurrency(holdings) {
        const firstWithCurrency = holdings.find(h => h.currency);
        return firstWithCurrency ? firstWithCurrency.currency : "";
    }

    /*
    Formats a money value for display.
    If a valid currency is provided, Intl.NumberFormat is used.
    If not, it falls back to a plain decimal number.
    */
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
        } catch (e) {
            return `${number.toFixed(2)} ${currency}`;
        }
    }

    // Formats ordinary numbers such as quantity.
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

    // Formats percentage-point values such as unrealizedGainLossPercent.
    function formatPercent(value) {
        const number = Number(value ?? 0);

        if (!Number.isFinite(number)) {
            return "-";
        }

        return `${number.toFixed(2)}%`;
    }

    /*
    Returns Bootstrap text colour class for gain/loss values.
    Positive values are green, negative values are red, and zero/invalid values use normal text colour.
    */
    function gainLossClass(value) {
        const number = Number(value ?? 0);

        if (!Number.isFinite(number) || number === 0) {
            return "text-body";
        }

        return number > 0 ? "text-success" : "text-danger";
    }

    // Shows loading state and hides table/empty state while the API request is running.
    function showLoading() {
        loadingState.classList.remove("d-none");
        emptyState.classList.add("d-none");
        tableContainer.classList.add("d-none");
    }

    // Hides loading text after the API call finishes.
    function hideLoading() {
        loadingState.classList.add("d-none");
    }

    /*
    Shows or hides the empty state.
    isEmpty=true means the API returned no holdings.
    isEmpty=false is used after errors to hide the table without showing the no-holdings message.
    */
    function showEmpty(isEmpty) {
        hideLoading();

        if (isEmpty) {
            emptyState.classList.remove("d-none");
            tableContainer.classList.add("d-none");
        } else {
            emptyState.classList.add("d-none");
        }
    }

    // Shows the holdings table and hides the empty state.
    function showTable() {
        emptyState.classList.add("d-none");
        tableContainer.classList.remove("d-none");
    }

    // Shows a page-level Bootstrap error alert.
    function showError(message) {
        alertContainer.innerHTML = `
            <div class="alert alert-danger alert-dismissible fade show" role="alert">
                ${escapeHtml(message)}
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            </div>
        `;
    }

    // Clears any previous page-level alert.
    function clearAlert() {
        alertContainer.innerHTML = "";
    }

    /*
    Escapes text before inserting it into innerHTML.
    This reduces unsafe HTML injection risk when rendering backend-provided values.
    */
    function escapeHtml(value) {
        return String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }
});
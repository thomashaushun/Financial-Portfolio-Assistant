/*
Allocation page controller.
This file controls the dynamic behaviour of allocation.html.

Main responsibilities:
1. Load backend-calculated allocation data from GET /api/portfolio/allocation.
2. Render five allocation groups: holding, asset class, sector, market and currency.
3. Render each group as both a table and a Chart.js doughnut chart.
4. Destroy old Chart.js instances before re-rendering to avoid duplicate charts.
5. Show loading, empty and error states.

Important design point:
The frontend does not group holdings or calculate allocation percentages.
AllocationService on the backend calculates the AllocationResponse.
*/
document.addEventListener("DOMContentLoaded", () => {
    // Backend endpoint used by this page.
    // It returns allocation groups calculated from current holdings.
    const apiBaseUrl = "/api/portfolio/allocation";

    // Main page state elements.
    // These IDs must match elements in allocation.html.
    const alertContainer = document.getElementById("alertContainer");
    const refreshButton = document.getElementById("refreshAllocationButton");
    const loadingState = document.getElementById("loadingState");
    const allocationContent = document.getElementById("allocationContent");
    const emptyState = document.getElementById("emptyState");

    /*
    Maps each allocation group to its chart canvas and table body.

    This avoids repeating separate rendering code for:
    - by holding
    - by asset class
    - by sector
    - by market
    - by currency
    */
    const chartConfigs = {
        byHolding: {
            canvas: document.getElementById("byHoldingChart"),
            tableBody: document.getElementById("byHoldingTableBody"),
            emptyLabel: "No holding allocation data."
        },
        byAssetClass: {
            canvas: document.getElementById("byAssetClassChart"),
            tableBody: document.getElementById("byAssetClassTableBody"),
            emptyLabel: "No asset class allocation data."
        },
        bySector: {
            canvas: document.getElementById("bySectorChart"),
            tableBody: document.getElementById("bySectorTableBody"),
            emptyLabel: "No sector allocation data."
        },
        byMarket: {
            canvas: document.getElementById("byMarketChart"),
            tableBody: document.getElementById("byMarketTableBody"),
            emptyLabel: "No market allocation data."
        },
        byCurrency: {
            canvas: document.getElementById("byCurrencyChart"),
            tableBody: document.getElementById("byCurrencyTableBody"),
            emptyLabel: "No currency allocation data."
        }
    };

    /*
    Stores active Chart.js instances.
    This is needed because existing charts must be destroyed before drawing new ones after refresh.
    Without this, charts can overlap or leak memory.
    */
    const charts = {
        byHolding: null,
        byAssetClass: null,
        bySector: null,
        byMarket: null,
        byCurrency: null
    };

    // Refresh button reloads allocation data from the backend.
    if (refreshButton) {
        refreshButton.addEventListener("click", loadAllocation);
    }

    // Initial load when the page opens.
    loadAllocation();

    /*
    Loads allocation data from the backend.

    Flow:
    1. Clear previous alert.
    2. Show loading state.
    3. Disable Refresh button to avoid duplicate requests.
    4. Call GET /api/portfolio/allocation.
    5. Render the returned AllocationResponse.
    6. Re-enable Refresh button.
    */
    async function loadAllocation() {
        clearAlert();
        showLoading();

        if (refreshButton) {
            refreshButton.disabled = true;
        }

        try {
            const response = await fetch(apiBaseUrl, {
                headers: {
                    Accept: "application/json"
                }
            });

            if (!response.ok) {
                const errorBody = await tryReadJson(response);
                throw new Error(
                    extractErrorMessage(errorBody, `Failed to load allocation. HTTP ${response.status}`)
                );
            }

            const result = await response.json();
            renderAllocation(result);
        } catch (error) {
            console.error("Error loading allocation:", error);
            showError(error.message || "Unable to load allocation.");
            showEmpty(false);
            destroyAllCharts();
        } finally {
            hideLoading();
            if (refreshButton) {
                refreshButton.disabled = false;
            }
        }
    }

    /*
    Renders the complete allocation result.

    The expected backend response has groups such as:
    - result.byHolding
    - result.byAssetClass
    - result.bySector
    - result.byMarket
    - result.byCurrency

    Each group contains allocation items with:
    - label
    - value
    - weightPercent
    */
    function renderAllocation(result) {
        const hasAnyData =
            hasItems(result?.byHolding) ||
            hasItems(result?.byAssetClass) ||
            hasItems(result?.bySector) ||
            hasItems(result?.byMarket) ||
            hasItems(result?.byCurrency);

        if (!hasAnyData) {
            showEmpty(true);
            destroyAllCharts();
            return;
        }

        allocationContent?.classList.remove("d-none");
        emptyState?.classList.add("d-none");

        renderAllocationGroup("byHolding", result.byHolding || []);
        renderAllocationGroup("byAssetClass", result.byAssetClass || []);
        renderAllocationGroup("bySector", result.bySector || []);
        renderAllocationGroup("byMarket", result.byMarket || []);
        renderAllocationGroup("byCurrency", result.byCurrency || []);
    }

    /*
    Renders one allocation group.

    The same function is reused for holding, asset class, sector, market and currency.
    It renders both:
    - a table
    - a doughnut chart
    */
    function renderAllocationGroup(groupKey, items) {
        const config = chartConfigs[groupKey];
        if (!config) {
            return;
        }

        renderTable(config.tableBody, items, config.emptyLabel);
        renderChart(groupKey, config.canvas, items);
    }

    /*
    Renders an allocation table.

    Each item row shows:
    - label: group name, e.g. AAPL, EQUITY, Technology, USD
    - value: monetary/current portfolio value for that group
    - weightPercent: percentage of the portfolio
    */
    function renderTable(tableBody, items, emptyLabel) {
        if (!tableBody) {
            return;
        }

        tableBody.innerHTML = "";

        if (!Array.isArray(items) || items.length === 0) {
            tableBody.innerHTML = `
                <tr>
                    <td colspan="3" class="text-muted">${escapeHtml(emptyLabel)}</td>
                </tr>
            `;
            return;
        }

        items.forEach(item => {
            const row = document.createElement("tr");
            row.innerHTML = `
                <td>${escapeHtml(item.label || "-")}</td>
                <td class="text-end">${formatNumber(item.value)}</td>
                <td class="text-end">${formatPercent(item.weightPercent)}</td>
            `;
            tableBody.appendChild(row);
        });
    }

    /*
    Renders a Chart.js doughnut chart for one allocation group.

    Important:
    - Chart data uses item.value as the dataset.
    - The tooltip displays both value and weight percentage.
    - Existing chart is destroyed before drawing a new one.
    */
    function renderChart(groupKey, canvas, items) {
        destroyChart(groupKey);

        if (!canvas || !Array.isArray(items) || items.length === 0) {
            return;
        }

        const labels = items.map(item => item.label || "-");
        const values = items.map(item => toNumber(item.value));

        charts[groupKey] = new Chart(canvas, {
            type: "doughnut",
            data: {
                labels,
                datasets: [
                    {
                        data: values
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        position: "bottom"
                    },
                    tooltip: {
                        callbacks: {
                            label(context) {
                                const item = items[context.dataIndex];
                                return `${item.label}: ${formatNumber(item.value)} (${formatPercent(item.weightPercent)})`;
                            }
                        }
                    }
                }
            }
        });
    }

    /*
    Destroys one Chart.js instance before it is replaced.
    This prevents duplicate/overlapping charts after refresh.
    */
    function destroyChart(groupKey) {
        if (charts[groupKey]) {
            charts[groupKey].destroy();
            charts[groupKey] = null;
        }
    }

    // Destroys all active charts, usually when no data is available or an error occurs.
    function destroyAllCharts() {
        Object.keys(charts).forEach(destroyChart);
    }

    // Returns true if the backend allocation group contains at least one item.
    function hasItems(value) {
        return Array.isArray(value) && value.length > 0;
    }

    // Shows loading state and hides the content/empty state while the API request is running.
    function showLoading() {
        loadingState?.classList.remove("d-none");
        allocationContent?.classList.add("d-none");
        emptyState?.classList.add("d-none");
    }

    // Hides the loading text after the API call finishes.
    function hideLoading() {
        loadingState?.classList.add("d-none");
    }

    /*
    Shows or hides the empty state.
    isEmpty=true means the API returned no allocation data.
    isEmpty=false is used after errors to hide empty-state wording while the alert explains the issue.
    */
    function showEmpty(isEmpty) {
        if (isEmpty) {
            allocationContent?.classList.add("d-none");
            emptyState?.classList.remove("d-none");
        } else {
            emptyState?.classList.add("d-none");
        }
    }

    // Shows a page-level Bootstrap error alert.
    function showError(message) {
        if (!alertContainer) {
            return;
        }

        alertContainer.innerHTML = `
            <div class="alert alert-danger alert-dismissible fade show" role="alert">
                ${escapeHtml(message)}
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            </div>
        `;
    }

    // Clears any previous page-level alert.
    function clearAlert() {
        if (alertContainer) {
            alertContainer.innerHTML = "";
        }
    }

    // Safely attempts to parse an error response body as JSON.
    async function tryReadJson(response) {
        try {
            return await response.json();
        } catch {
            return null;
        }
    }

    /*
    Extracts a readable error message from backend error responses.
    Supports common ApiErrorDto-style fields:
    - message
    - detail
    Falls back to a generic message if neither exists.
    */
    function extractErrorMessage(errorBody, fallbackMessage) {
        if (!errorBody) {
            return fallbackMessage;
        }

        if (typeof errorBody.message === "string" && errorBody.message.trim() !== "") {
            return errorBody.message;
        }

        if (typeof errorBody.detail === "string" && errorBody.detail.trim() !== "") {
            return errorBody.detail;
        }

        return fallbackMessage;
    }

    // Formats percentage-point values such as 25.50 as "25.50%".
    function formatPercent(value) {
        const number = toNumber(value);
        return `${number.toFixed(2)}%`;
    }

    // Formats allocation values to two decimal places for table/chart tooltips.
    function formatNumber(value) {
        const number = toNumber(value);
        return new Intl.NumberFormat(undefined, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }).format(number);
    }

    // Safely converts values to numbers. Invalid/missing values become 0.
    function toNumber(value) {
        const number = Number(value);
        return Number.isFinite(number) ? number : 0;
    }

    /*
    Escapes text before inserting it into innerHTML.
    This reduces unsafe HTML injection risk when rendering backend-provided labels.
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
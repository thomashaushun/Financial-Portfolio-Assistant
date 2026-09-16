/*
Analysis page controller.
This file controls the dynamic behaviour of analysis.html.

Main responsibilities:
1. Run benchmark comparison using GET /api/portfolio/analysis/benchmark.
2. Search benchmark symbols using GET /api/benchmarks/search.
3. Run portfolio optimisation using GET /api/portfolio/analysis/optimise.
4. Render benchmark and optimisation Chart.js charts.
5. Render metric cards, allocation comparison, suggestions and warnings.
6. Request beginner-friendly explanations using POST /api/portfolio/analysis/explain.

Important design point:
The frontend does not calculate benchmark returns, optimisation, efficient frontier or explanations.
Backend services calculate the results; this file collects inputs, calls APIs, and renders responses.
*/
document.addEventListener("DOMContentLoaded", () => {
    // Backend endpoints used by this page.
    const benchmarkApiBaseUrl = "/api/portfolio/analysis/benchmark";
    const optimisationApiBaseUrl = "/api/portfolio/analysis/optimise";
    const explanationApiBaseUrl = "/api/portfolio/analysis/explain";

    /*
    CSRF token/header are rendered into analysis.html.
    They are needed for the explanation POST request.
    */
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute("content");
    const csrfHeaderName = document.querySelector('meta[name="_csrf_header"]')?.getAttribute("content");

    // Page-level alert container for benchmark/optimisation/explanation errors.
    const alertContainer = document.getElementById("alertContainer");

    /*
    Benchmark UI element references.
    Grouping them into benchmarkUi keeps benchmark-related DOM elements organised.
    */
    const benchmarkUi = {
        form: document.getElementById("analysisForm"),
        benchmarkSymbolInput: document.getElementById("benchmarkSymbol"),
        benchmarkSearchResults: document.getElementById("benchmarkSearchResults"),
        fromDateInput: document.getElementById("fromDate"),
        toDateInput: document.getElementById("toDate"),
        runButton: document.getElementById("runAnalysisButton"),
        resetButton: document.getElementById("resetDatesButton"),
        loadingState: document.getElementById("loadingState"),
        content: document.getElementById("analysisContent"),
        emptyState: document.getElementById("emptyState"),
        portfolioReturnPercent: document.getElementById("portfolioReturnPercent"),
        benchmarkReturnPercent: document.getElementById("benchmarkReturnPercent"),
        excessReturnPercent: document.getElementById("excessReturnPercent"),
        portfolioValueSummary: document.getElementById("portfolioValueSummary"),
        benchmarkValueSummary: document.getElementById("benchmarkValueSummary"),
        benchmarkSummary: document.getElementById("benchmarkSummary"),
        summaryBenchmarkSymbol: document.getElementById("summaryBenchmarkSymbol"),
        summaryDateRange: document.getElementById("summaryDateRange"),
        summaryPointCount: document.getElementById("summaryPointCount"),
        summaryResultText: document.getElementById("summaryResultText"),
        chartCanvas: document.getElementById("analysisChart")
    };

    /*
    Optimisation UI element references.
    These elements are filled after GET /api/portfolio/analysis/optimise returns.
    */
    const optimisationUi = {
        form: document.getElementById("optimisationForm"),
        fromDateInput: document.getElementById("optimisationFromDate"),
        toDateInput: document.getElementById("optimisationToDate"),
        algorithmSelect: document.getElementById("optimisationAlgorithm"),
        runButton: document.getElementById("runOptimisationButton"),
        loadingState: document.getElementById("optimisationLoadingState"),
        content: document.getElementById("optimisationContent"),
        emptyState: document.getElementById("optimisationEmptyState"),
        currentReturn: document.getElementById("mptCurrentReturn"),
        currentVolatility: document.getElementById("mptCurrentVolatility"),
        currentSharpe: document.getElementById("mptCurrentSharpe"),
        optimisedReturn: document.getElementById("mptOptimisedReturn"),
        optimisedVolatility: document.getElementById("mptOptimisedVolatility"),
        optimisedSharpe: document.getElementById("mptOptimisedSharpe"),
        sharpeImprovement: document.getElementById("mptSharpeImprovement"),
        allocationTableBody: document.getElementById("optimisationAllocationTableBody"),
        suggestionsList: document.getElementById("optimisationSuggestionsList"),
        warningsList: document.getElementById("optimisationWarningsList"),
        chartCanvas: document.getElementById("optimisationChart"),
        efficientFrontierCard: document.getElementById("efficientFrontierCard"),
        explainButton: document.getElementById("explainOptimisationButton"),
        explanationLoadingState: document.getElementById("explanationLoadingState"),
        explanationContent: document.getElementById("explanationContent"),
        explanationTitle: document.getElementById("explanationTitle"),
        explanationText: document.getElementById("explanationText"),
        explanationFallbackNote: document.getElementById("explanationFallbackNote")
    };

    /*
    Chart.js instance references.
    Existing charts must be destroyed before rendering new charts to avoid overlap after refresh.
    */
    let benchmarkChart = null;
    let benchmarkSearchTimeoutId = null;
    let optimisationChart = null;

    /*
    Stores the most recent optimisation result.
    Explanation requests use this object, so the LLM explains the result currently shown on the page.
    */
    let latestOptimisationResult = null;

    // Default both benchmark and optimisation date ranges to approximately one year.
    setDefaultBenchmarkDates();
    setDefaultOptimisationDates();

    // Event listeners for benchmark comparison.
    if (benchmarkUi.form) {
        benchmarkUi.form.addEventListener("submit", onBenchmarkSubmit);
    }

    if (benchmarkUi.benchmarkSymbolInput) {
        benchmarkUi.benchmarkSymbolInput.addEventListener("input", onBenchmarkSearchInput);
    }

    if (benchmarkUi.resetButton) {
        benchmarkUi.resetButton.addEventListener("click", () => {
            setDefaultBenchmarkDates();
            loadBenchmarkAnalysis();
        });
    }

    // Event listener for portfolio optimisation.
    if (optimisationUi.form) {
        optimisationUi.form.addEventListener("submit", onOptimisationSubmit);
    }

    // Explanation buttons. They all depend on latestOptimisationResult.
    if (optimisationUi.explainButton) {
        optimisationUi.explainButton.addEventListener("click", async () => {
            await loadExplanation("SUMMARY", null);
        });
    }

    const explainPortfolioBasicsButton = document.getElementById("explainPortfolioBasicsButton");
    const explainCalculationButton = document.getElementById("explainCalculationButton");

    if (explainPortfolioBasicsButton) {
        explainPortfolioBasicsButton.addEventListener("click", async () => {
            await loadExplanation("PORTFOLIO_BASICS", null);
        });
    }

    if (explainCalculationButton) {
        explainCalculationButton.addEventListener("click", async () => {
            await loadExplanation("ALGORITHM_EXPLANATION", null);
        });
    }

    // Prevent normal form submission and run the benchmark API request through JavaScript.
    async function onBenchmarkSubmit(event) {
        event.preventDefault();
        await loadBenchmarkAnalysis();
    }

    // Prevent normal form submission and run the optimisation API request through JavaScript.
    async function onOptimisationSubmit(event) {
        event.preventDefault();
        await loadPortfolioOptimisation();
    }

    /*
    Runs benchmark comparison.

    Flow:
    1. Validate symbol and dates.
    2. Build query parameters.
    3. Call GET /api/portfolio/analysis/benchmark.
    4. Render cards, summary and line chart.
    */
    async function loadBenchmarkAnalysis() {
        clearAlert();
        showBenchmarkLoading();

        const symbol = normalizeSymbol(benchmarkUi.benchmarkSymbolInput?.value) || "SPX";
        const from = benchmarkUi.fromDateInput?.value;
        const to = benchmarkUi.toDateInput?.value;

        if (!from || !to) {
            showError("Please select both start and end dates.");
            hideBenchmarkLoading();
            return;
        }

        if (from > to) {
            showError("The start date must be earlier than or equal to the end date.");
            hideBenchmarkLoading();
            return;
        }

        const params = new URLSearchParams({ symbol, from, to });

        if (benchmarkUi.runButton) {
            benchmarkUi.runButton.disabled = true;
        }

        try {
            const response = await fetch(`${benchmarkApiBaseUrl}?${params.toString()}`, {
                headers: {
                    Accept: "application/json"
                }
            });

            if (!response.ok) {
                const errorBody = await tryReadJson(response);
                throw new Error(
                    extractErrorMessage(errorBody, `Failed to load analysis. HTTP ${response.status}`)
                );
            }

            const result = await response.json();
            renderBenchmarkAnalysis(result);
        } catch (error) {
            console.error("Error loading benchmark analysis:", error);
            showError(error.message || "Unable to load analysis.");
            showBenchmarkEmpty(false);
            destroyBenchmarkChart();
        } finally {
            hideBenchmarkLoading();
            if (benchmarkUi.runButton) {
                benchmarkUi.runButton.disabled = false;
            }
        }
    }

    /*
    Handles benchmark search typing.
    The 350ms debounce avoids calling the search API on every single key press immediately.
    */
    function onBenchmarkSearchInput() {
        const query = benchmarkUi.benchmarkSymbolInput.value.trim();

        if (query.length < 2) {
            clearBenchmarkSearchResults();
            return;
        }

        window.clearTimeout(benchmarkSearchTimeoutId);
        benchmarkSearchTimeoutId = window.setTimeout(() => {
            searchBenchmarkAssets(query);
        }, 350);
    }

    // Calls the benchmark search endpoint and renders clickable search results.
    async function searchBenchmarkAssets(query) {
        try {
            const params = new URLSearchParams({ query });

            const response = await fetch(`/api/benchmarks/search?${params.toString()}`, {
                headers: {
                    Accept: "application/json"
                }
            });

            if (!response.ok) {
                throw new Error(`Benchmark search failed. HTTP ${response.status}`);
            }

            const results = await response.json();
            renderBenchmarkSearchResults(results);
        } catch (error) {
            console.error("Benchmark search failed:", error);
            clearBenchmarkSearchResults();
        }
    }

    // Renders benchmark search results as clickable buttons below the benchmark input.
    function renderBenchmarkSearchResults(results) {
        if (!benchmarkUi.benchmarkSearchResults) {
            return;
        }

        benchmarkUi.benchmarkSearchResults.innerHTML = "";

        if (!Array.isArray(results) || results.length === 0) {
            benchmarkUi.benchmarkSearchResults.classList.add("d-none");
            return;
        }

        results.forEach(result => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "list-group-item list-group-item-action";

            button.innerHTML = `
            <div class="fw-semibold">${escapeHtml(result.name ?? "-")}</div>
            <div class="small text-muted">${escapeHtml(result.symbol ?? "-")}</div>
        `;

            button.addEventListener("click", () => selectBenchmarkSearchResult(result));
            benchmarkUi.benchmarkSearchResults.appendChild(button);
        });

        benchmarkUi.benchmarkSearchResults.classList.remove("d-none");
    }

    // Copies selected benchmark symbol into the input.
    function selectBenchmarkSearchResult(result) {
        benchmarkUi.benchmarkSymbolInput.value = result.symbol ?? "";
        clearBenchmarkSearchResults();
    }

    // Clears the benchmark search dropdown.
    function clearBenchmarkSearchResults() {
        if (!benchmarkUi.benchmarkSearchResults) {
            return;
        }

        benchmarkUi.benchmarkSearchResults.innerHTML = "";
        benchmarkUi.benchmarkSearchResults.classList.add("d-none");
    }

    /*
    Runs portfolio optimisation.

    Flow:
    1. Validate date range.
    2. Read selected algorithm.
    3. Call GET /api/portfolio/analysis/optimise.
    4. Render metrics, allocation table, suggestions, warnings and chart.
    5. Reset explanation because old explanation should not remain after a new optimisation result.
    */
    async function loadPortfolioOptimisation() {
        clearAlert();
        showOptimisationLoading();
        resetExplanationState();

        const from = optimisationUi.fromDateInput?.value;
        const to = optimisationUi.toDateInput?.value;
        const algorithm = optimisationUi.algorithmSelect?.value || "MEAN_VARIANCE";

        if (!from || !to) {
            showError("Please select both optimisation dates.");
            hideOptimisationLoading();
            return;
        }

        if (from > to) {
            showError("The optimisation start date must be earlier than or equal to the end date.");
            hideOptimisationLoading();
            return;
        }

        const params = new URLSearchParams({ from, to, algorithm });

        if (optimisationUi.runButton) {
            optimisationUi.runButton.disabled = true;
        }

        try {
            const response = await fetch(`${optimisationApiBaseUrl}?${params.toString()}`, {
                headers: {
                    Accept: "application/json"
                }
            });

            if (!response.ok) {
                const errorBody = await tryReadJson(response);
                throw new Error(
                    extractErrorMessage(errorBody, `Failed to load optimisation. HTTP ${response.status}`)
                );
            }

            const result = await response.json();
            renderPortfolioOptimisation(result);
        } catch (error) {
            console.error("Error loading portfolio optimisation:", error);
            latestOptimisationResult = null;
            showError(error.message || "Unable to load optimisation.");
            showOptimisationEmpty(false);
            destroyOptimisationChart();
            resetExplanationState();
        } finally {
            hideOptimisationLoading();
            if (optimisationUi.runButton) {
                optimisationUi.runButton.disabled = false;
            }
        }
    }

    /*
    Renders benchmark comparison result.
    Backend provides the return percentages and indexed performance points.
    Frontend formats values and draws the line chart.
    */
    function renderBenchmarkAnalysis(result) {
        if (!result || !Array.isArray(result.points) || result.points.length === 0) {
            showBenchmarkEmpty(true);
            destroyBenchmarkChart();
            return;
        }

        benchmarkUi.content?.classList.remove("d-none");
        benchmarkUi.emptyState?.classList.add("d-none");

        if (benchmarkUi.portfolioReturnPercent) {
            benchmarkUi.portfolioReturnPercent.textContent = formatPercent(result.portfolioReturnPercent);
            benchmarkUi.portfolioReturnPercent.className =
                `fs-4 fw-semibold ${gainLossClass(result.portfolioReturnPercent)}`;
        }

        if (benchmarkUi.benchmarkReturnPercent) {
            benchmarkUi.benchmarkReturnPercent.textContent = formatPercent(result.benchmarkReturnPercent);
            benchmarkUi.benchmarkReturnPercent.className =
                `fs-4 fw-semibold ${gainLossClass(result.benchmarkReturnPercent)}`;
        }

        if (benchmarkUi.excessReturnPercent) {
            benchmarkUi.excessReturnPercent.textContent = formatPercent(result.excessReturnPercent);
            benchmarkUi.excessReturnPercent.className =
                `fs-4 fw-semibold ${gainLossClass(result.excessReturnPercent)}`;
        }

        setText(
            benchmarkUi.portfolioValueSummary,
            `Start ${formatNumber(result.startingPortfolioValue)} → End ${formatNumber(result.endingPortfolioValue)}`
        );

        setText(
            benchmarkUi.benchmarkValueSummary,
            `Start ${formatNumber(result.startingBenchmarkValue)} → End ${formatNumber(result.endingBenchmarkValue)}`
        );

        setText(
            benchmarkUi.benchmarkSummary,
            `Compared against ${result.benchmarkSymbol ?? "-"}`
        );

        setText(benchmarkUi.summaryBenchmarkSymbol, result.benchmarkSymbol ?? "-");
        setText(
            benchmarkUi.summaryDateRange,
            `${formatDate(result.fromDate)} to ${formatDate(result.toDate)}`
        );
        setText(benchmarkUi.summaryPointCount, String(result.points.length));
        setText(benchmarkUi.summaryResultText, buildBenchmarkResultText(result));

        renderBenchmarkChart(result.points);
    }

    /*
    Renders optimisation result.
    Backend provides current/optimised metrics, asset weights, suggestions, warnings and frontier points.
    Frontend only formats and displays them.
    */
    function renderPortfolioOptimisation(result) {
        if (!result || !Array.isArray(result.assets) || result.assets.length === 0) {
            latestOptimisationResult = null;
            showOptimisationEmpty(true);
            destroyOptimisationChart();
            resetExplanationState();
            return;
        }

        latestOptimisationResult = result;
        optimisationUi.content?.classList.remove("d-none");
        optimisationUi.emptyState?.classList.add("d-none");

        const currentPortfolio = result.currentPortfolio || {};
        const optimisedPortfolio = result.optimisedPortfolio || {};

        setText(optimisationUi.currentReturn, formatRatioAsPercent(currentPortfolio.expectedAnnualReturn));
        setText(optimisationUi.currentVolatility, formatRatioAsPercent(currentPortfolio.annualVolatility));
        setText(optimisationUi.currentSharpe, formatDecimal(currentPortfolio.sharpeRatio));

        setText(optimisationUi.optimisedReturn, formatRatioAsPercent(optimisedPortfolio.expectedAnnualReturn));
        setText(optimisationUi.optimisedVolatility, formatRatioAsPercent(optimisedPortfolio.annualVolatility));
        setText(optimisationUi.optimisedSharpe, formatDecimal(optimisedPortfolio.sharpeRatio));

        const sharpeDiff =
            toNumber(optimisedPortfolio.sharpeRatio) - toNumber(currentPortfolio.sharpeRatio);

        setText(
            optimisationUi.sharpeImprovement,
            sharpeDiff >= 0
                ? `Sharpe ratio improved by ${sharpeDiff.toFixed(4)}`
                : `Sharpe ratio changed by ${sharpeDiff.toFixed(4)}`
        );

        renderOptimisationAllocationTable(result.assets);
        renderStringList(optimisationUi.suggestionsList, result.suggestions, "No suggestions.");
        renderStringList(optimisationUi.warningsList, result.warnings, "No warnings.");
        renderOptimisationVisualisation(result);
    }

    /*
    Renders benchmark indexed performance chart.
    Backend sends already-indexed series, so the frontend only plots them.
    */
    function renderBenchmarkChart(points) {
        destroyBenchmarkChart();

        if (!benchmarkUi.chartCanvas) {
            return;
        }

        const labels = points.map(point => point.date);
        const portfolioSeries = points.map(point => toNumber(point.portfolioIndexed));
        const benchmarkSeries = points.map(point => toNumber(point.benchmarkIndexed));

        benchmarkChart = new Chart(benchmarkUi.chartCanvas, {
            type: "line",
            data: {
                labels,
                datasets: [
                    {
                        label: "Portfolio",
                        data: portfolioSeries,
                        tension: 0.2
                    },
                    {
                        label: "Benchmark",
                        data: benchmarkSeries,
                        tension: 0.2
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: true,
                interaction: {
                    mode: "index",
                    intersect: false
                },
                scales: {
                    x: {
                        ticks: {
                            maxTicksLimit: 10
                        }
                    },
                    y: {
                        title: {
                            display: true,
                            text: "Indexed Value"
                        }
                    }
                }
            }
        });
    }

    /*
    Chooses the correct optimisation visualisation.
    Risk Parity returns one target allocation, so the efficient frontier card is hidden.
    Mean Variance uses a scatter chart for efficient frontier/current/optimised points.
    */
    function renderOptimisationVisualisation(result) {
        const algorithm = result?.algorithm || optimisationUi.algorithmSelect?.value || "MEAN_VARIANCE";

        if (algorithm === "RISK_PARITY") {
            optimisationUi.efficientFrontierCard?.classList.add("d-none");
            destroyOptimisationChart();
            return;
        }

        optimisationUi.efficientFrontierCard?.classList.remove("d-none");
        renderOptimisationChart(result);
    }

    /*
    Renders Mean-Variance efficient frontier chart.
    x-axis = volatility
    y-axis = expected return
    */
    function renderOptimisationChart(result) {
        destroyOptimisationChart();

        if (!optimisationUi.chartCanvas) {
            return;
        }

        const frontierData = Array.isArray(result.efficientFrontier)
            ? result.efficientFrontier.map(point => ({
                x: toNumber(point.annualVolatility),
                y: toNumber(point.expectedAnnualReturn)
            }))
            : [];

        const currentPoint = {
            x: toNumber(result.currentPortfolio?.annualVolatility),
            y: toNumber(result.currentPortfolio?.expectedAnnualReturn)
        };

        const optimisedPoint = {
            x: toNumber(result.optimisedPortfolio?.annualVolatility),
            y: toNumber(result.optimisedPortfolio?.expectedAnnualReturn)
        };

        optimisationChart = new Chart(optimisationUi.chartCanvas, {
            type: "scatter",
            data: {
                datasets: [
                    {
                        label: "Efficient Frontier",
                        data: frontierData,
                        showLine: true
                    },
                    {
                        label: "Current Portfolio",
                        data: [currentPoint],
                        pointRadius: 6
                    },
                    {
                        label: "Optimised Portfolio",
                        data: [optimisedPoint],
                        pointRadius: 6
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: true,
                scales: {
                    x: {
                        title: {
                            display: true,
                            text: "Volatility"
                        }
                    },
                    y: {
                        title: {
                            display: true,
                            text: "Expected Return"
                        }
                    }
                }
            }
        });
    }

    /*
    Renders asset-level optimisation result rows.
    Each row shows current weight, target weight, difference and suggested change.
    */
    function renderOptimisationAllocationTable(assets) {
        if (!optimisationUi.allocationTableBody) {
            return;
        }

        optimisationUi.allocationTableBody.innerHTML = "";

        if (!Array.isArray(assets) || assets.length === 0) {
            optimisationUi.allocationTableBody.innerHTML = `
                <tr>
                    <td colspan="5" class="text-muted">No optimisation data yet.</td>
                </tr>
            `;
            return;
        }

        assets.forEach(asset => {
            const difference = toNumber(asset.weightDifference);
            const differenceClass =
                difference > 0 ? "text-success" :
                    difference < 0 ? "text-danger" :
                        "text-body";

            const row = document.createElement("tr");
            row.innerHTML = `
                <td>${escapeHtml(asset.symbol || "-")}</td>
                <td>${formatRatioAsPercent(asset.currentWeight)}</td>
                <td>${formatRatioAsPercent(asset.targetWeight)}</td>
                <td class="${differenceClass}">${formatSignedRatioAsPercent(asset.weightDifference)}</td>
                <td>${formatSuggestedChange(asset)}</td>
            `;
            optimisationUi.allocationTableBody.appendChild(row);
        });
    }

    // Renders suggestion/warning arrays as simple list items.
    function renderStringList(container, items, emptyMessage) {
        if (!container) {
            return;
        }

        container.innerHTML = "";

        if (!Array.isArray(items) || items.length === 0) {
            container.innerHTML = `<li class="text-muted">${escapeHtml(emptyMessage)}</li>`;
            return;
        }

        items.forEach(item => {
            const li = document.createElement("li");
            li.textContent = item;
            container.appendChild(li);
        });
    }

    // Destroys old benchmark chart before drawing a new one.
    function destroyBenchmarkChart() {
        if (benchmarkChart) {
            benchmarkChart.destroy();
            benchmarkChart = null;
        }
    }

    // Destroys old optimisation chart before drawing a new one.
    function destroyOptimisationChart() {
        if (optimisationChart) {
            optimisationChart.destroy();
            optimisationChart = null;
        }
    }

    // Builds a readable benchmark result sentence from excess return.
    function buildBenchmarkResultText(result) {
        const excess = toNumber(result.excessReturnPercent);

        if (excess > 0) {
            return `Your portfolio outperformed ${result.benchmarkSymbol} over this period.`;
        }

        if (excess < 0) {
            return `Your portfolio underperformed ${result.benchmarkSymbol} over this period.`;
        }

        return `Your portfolio matched ${result.benchmarkSymbol} over this period.`;
    }

    // Sets default benchmark range to one year ago until today.
    function setDefaultBenchmarkDates() {
        const today = new Date();
        const oneYearAgo = new Date(today);
        oneYearAgo.setFullYear(today.getFullYear() - 1);

        if (benchmarkUi.toDateInput) {
            benchmarkUi.toDateInput.value = toIsoDate(today);
        }

        if (benchmarkUi.fromDateInput) {
            benchmarkUi.fromDateInput.value = toIsoDate(oneYearAgo);
        }
    }

    // Sets default optimisation range to one year ago until today.
    function setDefaultOptimisationDates() {
        if (!optimisationUi.fromDateInput || !optimisationUi.toDateInput) {
            return;
        }

        const today = new Date();
        const oneYearAgo = new Date(today);
        oneYearAgo.setFullYear(today.getFullYear() - 1);

        optimisationUi.toDateInput.value = toIsoDate(today);
        optimisationUi.fromDateInput.value = toIsoDate(oneYearAgo);
    }

    // Converts Date object to yyyy-MM-dd for date inputs.
    function toIsoDate(date) {
        return date.toISOString().slice(0, 10);
    }

    // Normalises user-entered symbols, e.g. " spx " becomes "SPX".
    function normalizeSymbol(value) {
        const trimmed = value?.trim();
        return trimmed ? trimmed.toUpperCase() : null;
    }

    // Formats values already returned as percentage points, e.g. 5.25 -> "5.25%".
    function formatPercent(value) {
        const number = toNumber(value);
        return `${number.toFixed(2)}%`;
    }

    // Formats decimal ratios, e.g. 0.0525 -> "5.25%".
    function formatRatioAsPercent(value) {
        const number = toNumber(value);
        return `${(number * 100).toFixed(2)}%`;
    }

    // Formats signed decimal ratios, e.g. 0.05 -> "+5.00%".
    function formatSignedRatioAsPercent(value) {
        const number = toNumber(value);
        const percent = number * 100;
        return percent > 0 ? `+${percent.toFixed(2)}%` : `${percent.toFixed(2)}%`;
    }

    // Formats Sharpe ratio values to four decimal places.
    function formatDecimal(value) {
        const number = toNumber(value);
        return number.toFixed(4);
    }

    // Formats ordinary numeric values to two decimal places.
    function formatNumber(value) {
        const number = toNumber(value);
        return new Intl.NumberFormat(undefined, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }).format(number);
    }

    // Formats dates for display in the benchmark summary.
    function formatDate(value) {
        if (!value) {
            return "-";
        }

        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return value;
        }

        return new Intl.DateTimeFormat(undefined, {
            year: "numeric",
            month: "short",
            day: "2-digit"
        }).format(date);
    }

    // Safely converts values to numbers. Invalid/missing values become 0.
    function toNumber(value) {
        const number = Number(value);
        return Number.isFinite(number) ? number : 0;
    }

    // Returns Bootstrap text colour class for positive/negative values.
    function gainLossClass(value) {
        const number = toNumber(value);
        if (number > 0) {
            return "text-success";
        }
        if (number < 0) {
            return "text-danger";
        }
        return "text-body";
    }

    // Shows benchmark loading state and hides benchmark result/empty state.
    function showBenchmarkLoading() {
        benchmarkUi.loadingState?.classList.remove("d-none");
        benchmarkUi.content?.classList.add("d-none");
        benchmarkUi.emptyState?.classList.add("d-none");
    }

    function hideBenchmarkLoading() {
        benchmarkUi.loadingState?.classList.add("d-none");
    }

    // Shows optimisation loading state and hides optimisation result/empty state.
    function showOptimisationLoading() {
        optimisationUi.loadingState?.classList.remove("d-none");
        optimisationUi.content?.classList.add("d-none");
        optimisationUi.emptyState?.classList.add("d-none");
    }

    function hideOptimisationLoading() {
        optimisationUi.loadingState?.classList.add("d-none");
    }

    // Shows benchmark empty state when the benchmark endpoint returns no usable points.
    function showBenchmarkEmpty(isEmpty) {
        hideBenchmarkLoading();

        if (isEmpty) {
            benchmarkUi.content?.classList.add("d-none");
            benchmarkUi.emptyState?.classList.remove("d-none");
        } else {
            benchmarkUi.emptyState?.classList.add("d-none");
        }
    }

    // Shows optimisation empty state when optimisation returns no usable asset result.
    function showOptimisationEmpty(isEmpty) {
        hideOptimisationLoading();

        if (isEmpty) {
            optimisationUi.content?.classList.add("d-none");
            optimisationUi.emptyState?.classList.remove("d-none");
            optimisationUi.efficientFrontierCard?.classList.add("d-none");
        } else {
            optimisationUi.emptyState?.classList.add("d-none");
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

    // Clears page-level alert.
    function clearAlert() {
        if (alertContainer) {
            alertContainer.innerHTML = "";
        }
    }

    // Safely attempts to parse an error response as JSON.
    async function tryReadJson(response) {
        try {
            return await response.json();
        } catch {
            return null;
        }
    }

    // Extracts readable error messages from backend ApiErrorDto-style responses.
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

    // Safely sets text content when an element exists.
    function setText(element, value) {
        if (element) {
            element.textContent = value;
        }
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

    /*
    Requests explanation from the backend.

    Important:
    The explanation is based on latestOptimisationResult.
    The LLM explains existing backend-calculated values; it does not calculate the portfolio.
    */
    async function loadExplanation(mode, term) {
        if (!latestOptimisationResult) {
            showError("Run portfolio optimisation first before requesting an explanation.");
            return;
        }

        clearAlert();
        showExplanationLoading();

        try {
            const payload = buildExplanationRequest(mode, term);

            const headers = {
                Accept: "application/json",
                "Content-Type": "application/json"
            };

            if (csrfToken && csrfHeaderName) {
                headers[csrfHeaderName] = csrfToken;
            }

            const response = await fetch(explanationApiBaseUrl, {
                method: "POST",
                headers,
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                const errorBody = await tryReadJson(response);
                throw new Error(
                    extractErrorMessage(errorBody, `Failed to load explanation. HTTP ${response.status}`)
                );
            }

            const result = await response.json();
            renderExplanation(result);
        } catch (error) {
            console.error("Error loading explanation:", error);
            showError(error.message || "Unable to load explanation.");
            hideExplanationLoading();
        }
    }

    /*
    Builds the explanation request payload.
    It sends current/optimised portfolio metrics, assets, suggestions and warnings to the backend.
    This keeps the explanation grounded in existing calculated results.
    */
    function buildExplanationRequest(mode, term) {
        return {
            mode,
            term,
            algorithm: latestOptimisationResult?.algorithm || optimisationUi.algorithmSelect?.value || "MEAN_VARIANCE",

            currentPortfolio: latestOptimisationResult.currentPortfolio,
            optimisedPortfolio: latestOptimisationResult.optimisedPortfolio,

            assets: latestOptimisationResult.assets || [],
            suggestions: latestOptimisationResult.suggestions || [],
            warnings: latestOptimisationResult.warnings || [],

            benchmarkSymbol: benchmarkUi.summaryBenchmarkSymbol?.textContent || null,
            benchmarkSummary: benchmarkUi.summaryResultText?.textContent || null,

            userQuestion: buildUserQuestion(mode, term)
        };
    }

    // Creates a plain-language question for the selected explanation mode.
    function buildUserQuestion(mode, term) {
        if (mode === "PORTFOLIO_BASICS") {
            return "Explain what a finance portfolio is for a beginner.";
        }

        if (mode === "ALGORITHM_EXPLANATION") {
            const algorithm = latestOptimisationResult?.algorithm || optimisationUi.algorithmSelect?.value || "MEAN_VARIANCE";
            return `Explain how the ${algorithm} portfolio calculation works in plain language for a beginner.`;
        }

        if (mode === "TERM" && term) {
            return `Explain ${term} in plain language for a beginner.`;
        }

        return "Explain the overall portfolio result in plain language for a beginner.";
    }

    // Renders explanation title, explanation text and fallback note.
    function renderExplanation(result) {
        hideExplanationLoading();

        optimisationUi.explanationContent?.classList.remove("d-none");
        setText(optimisationUi.explanationTitle, result.title || "Explanation");
        renderMultilineText(optimisationUi.explanationText, result.explanation || "-");

        if (result.fallbackUsed) {
            optimisationUi.explanationFallbackNote?.classList.remove("d-none");
        } else {
            optimisationUi.explanationFallbackNote?.classList.add("d-none");
        }
    }

    // Shows explanation loading state.
    function showExplanationLoading() {
        optimisationUi.explanationLoadingState?.classList.remove("d-none");
        optimisationUi.explanationContent?.classList.add("d-none");
        optimisationUi.explanationFallbackNote?.classList.add("d-none");
    }

    function hideExplanationLoading() {
        optimisationUi.explanationLoadingState?.classList.add("d-none");
    }

    // Clears previous explanation when a new optimisation result is requested.
    function resetExplanationState() {
        hideExplanationLoading();
        optimisationUi.explanationContent?.classList.add("d-none");
        optimisationUi.explanationFallbackNote?.classList.add("d-none");
        setText(optimisationUi.explanationTitle, "");
        setText(optimisationUi.explanationText, "");
    }

    /*
    Formats suggested rebalance change.
    Backend provides suggestedAction, suggestedUnitChange and valueDifference.
    Frontend turns those into readable text.
    */
    function formatSuggestedChange(asset) {
        const action = asset.suggestedAction || "HOLD";
        const units = Math.abs(toNumber(asset.suggestedUnitChange));
        const value = Math.abs(toNumber(asset.valueDifference));

        if (action === "BUY") {
            return `+ ${formatNumber(units)} units (${formatMoneyAmount(value)})`;
        }

        if (action === "REDUCE") {
            return `- ${formatNumber(units)} units (${formatMoneyAmount(value)})`;
        }

        return "No change";
    }

    // Formats plain money-like values without applying a currency symbol.
    function formatMoneyAmount(value) {
        const number = toNumber(value);

        if (!Number.isFinite(number)) {
            return "-";
        }

        return new Intl.NumberFormat(undefined, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }).format(number);
    }

    /*
    Renders multiline explanation text.
    Escapes HTML first, then converts line breaks into <br> so LLM/fallback paragraphs remain readable.
    */
    function renderMultilineText(element, value) {
        if (!element) {
            return;
        }

        const escaped = escapeHtml(value || "-");
        element.innerHTML = escaped
            .replaceAll("\r\n", "\n")
            .replaceAll("\n", "<br>");
    }
});
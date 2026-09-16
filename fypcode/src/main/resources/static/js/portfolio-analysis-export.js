/*
PDF export controller for the Analysis page.

Main purpose:
1. Generate a PDF report in the browser using jsPDF and jspdf-autotable.
2. Fetch fresh holdings and allocation data from backend APIs.
3. Reuse benchmark, optimisation and explanation results already shown on the Analysis page.
4. Avoid rerunning benchmark comparison, optimisation or LLM explanation during export.

Important design point:
This export is frontend-driven. It creates a report from the current page state plus fresh holdings/allocation data.
It does not modify the portfolio and does not save anything to the backend.
*/

/*
Backend data fetched directly by this export file:
- GET /api/portfolio/holdings
- GET /api/portfolio/allocation

Benchmark, optimisation and explanation sections are read from the current DOM instead of calling their APIs again.
This avoids extra external market-data/API/LLM quota usage.
*/
document.addEventListener("DOMContentLoaded", () => {
    /*
    The export button is defined in analysis.html.
    If this file is loaded on a page without the button, it safely does nothing.
    */
    const exportButton = document.getElementById("exportPdfButton");

    if (!exportButton) {
        return;
    }

    // When clicked, generate and download the PDF report.
    exportButton.addEventListener("click", exportPortfolioAnalysisPdf);
});

/*
Main export function.

Flow:
1. Disable the export button and show "Creating PDF...".
2. Fetch holdings and allocation data in parallel.
3. Create an A4 portrait jsPDF document.
4. Add report sections in order.
5. Add page numbers.
6. Save the PDF with a timestamped filename.
7. Re-enable the export button.
*/
async function exportPortfolioAnalysisPdf() {
    try {
        setExportButtonLoading(true);

        /*
        Fetch holdings and allocation in parallel because they are independent.
        These are fresh backend responses at export time.
        */
        const [holdings, allocation] = await Promise.all([
            fetchJson("/api/portfolio/holdings"),
            fetchJson("/api/portfolio/allocation")
        ]);

        // jsPDF is provided by the CDN script loaded in analysis.html.
        const { jsPDF } = window.jspdf;

        // Create an A4 portrait document using millimetres.
        const doc = new jsPDF("p", "mm", "a4");

        // y tracks the current vertical position on the PDF page.
        let y = 15;

        /*
        Build the report section by section.
        Each add... function returns the next y position.
        */
        y = addReportHeader(doc, y);
        y = addPortfolioSummary(doc, y, holdings);
        y = addHoldingsTable(doc, y, holdings);
        y = await addAllocationSection(doc, y, allocation);
        y = addBenchmarkSection(doc, y);
        y = addOptimisationSection(doc, y);
        y = addExplanationSection(doc, y);
        y = addDisclaimer(doc, y);

        // Add "Page X of Y" footer after all pages have been created.
        addFooterPageNumbers(doc);

        /*
        Timestamped filename prevents overwriting older exported reports.
        Example: portfolio-analysis-report-2026-06-07-18-30-00.pdf
        */
        const timestamp = new Date()
            .toISOString()
            .slice(0, 19)
            .replace(/[:T]/g, "-");

        doc.save(`portfolio-analysis-report-${timestamp}.pdf`);
    } catch (error) {
        console.error(error);
        alert("Could not export the PDF report. Please check the console for details.");
    } finally {
        setExportButtonLoading(false);
    }
}

/*
Fetches JSON from a backend endpoint.

Used by PDF export for:
- /api/portfolio/holdings
- /api/portfolio/allocation

This helper throws an error if the response is not successful.
*/
async function fetchJson(url) {
    const response = await fetch(url, {
        headers: {
            "Accept": "application/json"
        }
    });

    if (!response.ok) {
        throw new Error(`Request failed: ${url} (${response.status})`);
    }

    return response.json();
}

/*
Disables/enables the export button while the PDF is being generated.
This prevents repeated clicks and duplicate export attempts.
*/
function setExportButtonLoading(isLoading) {
    const button = document.getElementById("exportPdfButton");

    if (!button) {
        return;
    }

    button.disabled = isLoading;
    button.textContent = isLoading ? "Creating PDF..." : "Export your portfolio as PDF";
}

/*
Adds the report title and selected analysis settings.

This reads current input values from the Analysis page:
- benchmark symbol
- benchmark date range
- optimisation date range
- selected optimisation algorithm

These values document what the user selected when exporting.
*/
function addReportHeader(doc, y) {
    doc.setFontSize(18);
    doc.text("Portfolio Analysis Report", 14, y);

    y += 8;

    doc.setFontSize(10);
    doc.text(`Generated on: ${new Date().toLocaleString()}`, 14, y);

    y += 6;

    const benchmarkSymbol = valueOfInput("benchmarkSymbol") || "Not selected";
    const benchmarkFrom = valueOfInput("fromDate") || "Not selected";
    const benchmarkTo = valueOfInput("toDate") || "Not selected";
    const optimisationFrom = valueOfInput("optimisationFromDate") || "Not selected";
    const optimisationTo = valueOfInput("optimisationToDate") || "Not selected";
    const algorithm = valueOfInput("optimisationAlgorithm") || "MEAN_VARIANCE";

    doc.text(`Benchmark: ${benchmarkSymbol}`, 14, y);
    y += 5;
    doc.text(`Benchmark period: ${benchmarkFrom} to ${benchmarkTo}`, 14, y);
    y += 5;
    doc.text(`Optimisation period: ${optimisationFrom} to ${optimisationTo}`, 14, y);
    y += 5;
    doc.text(`Optimisation algorithm: ${algorithm}`, 14, y);

    y += 8;

    return y;
}

/*
Adds the portfolio summary section.

Important:
Holdings are already derived by the backend HoldingService.
This function only sums fields from the returned HoldingResponse list for PDF display.
*/
function addPortfolioSummary(doc, y, holdings) {
    y = addSectionTitle(doc, y, "Portfolio Summary");

    const safeHoldings = Array.isArray(holdings) ? holdings : [];

    const totalCost = sum(safeHoldings, "totalCost");
    const marketValue = sum(safeHoldings, "marketValue");
    const unrealisedGainLoss = sum(safeHoldings, "unrealizedGainLoss");

    /*
    Simple gain/loss percentage for the PDF summary.
    Uses total cost as denominator.
    If total cost is zero, it avoids division by zero.
    */
    const gainLossPercent = totalCost > 0
        ? (unrealisedGainLoss / totalCost) * 100
        : 0;

    const rows = [
        ["Number of holdings", String(safeHoldings.length)],
        ["Total cost", money(totalCost)],
        ["Current market value", money(marketValue)],
        ["Unrealised gain/loss", money(unrealisedGainLoss)],
        ["Unrealised gain/loss %", percent(gainLossPercent)]
    ];

    doc.autoTable({
        startY: y,
        head: [["Metric", "Value"]],
        body: rows,
        theme: "grid",
        styles: {
            fontSize: 9
        },
        headStyles: {
            fillColor: [40, 40, 40]
        }
    });

    return doc.lastAutoTable.finalY + 8;
}

/*
Adds the detailed holdings table.

Data source:
GET /api/portfolio/holdings

Important:
This table is a snapshot of current backend-derived holdings at export time.
*/
function addHoldingsTable(doc, y, holdings) {
    y = addSectionTitle(doc, y, "Holdings");

    const safeHoldings = Array.isArray(holdings) ? holdings : [];

    if (safeHoldings.length === 0) {
        return addParagraph(doc, y, "No holdings were available when this report was exported.");
    }

    const rows = safeHoldings.map(holding => [
        safeText(holding.symbol),
        safeText(holding.displayName),
        safeText(holding.assetClass),
        number(holding.totalQuantity),
        money(holding.averageCost),
        money(holding.latestPrice),
        money(holding.marketValue),
        money(holding.unrealizedGainLoss),
        safeText(holding.currency)
    ]);

    doc.autoTable({
        startY: y,
        head: [[
            "Symbol",
            "Name",
            "Class",
            "Qty",
            "Avg Cost",
            "Latest",
            "Value",
            "P/L",
            "Currency"
        ]],
        body: rows,
        theme: "striped",
        styles: {
            fontSize: 7
        },
        headStyles: {
            fillColor: [40, 40, 40]
        }
    });

    return doc.lastAutoTable.finalY + 8;
}

/*
Adds allocation charts and tables.

Data source:
GET /api/portfolio/allocation

Important:
The PDF includes:
- By Asset Class
- By Sector
- By Market
- By Currency

It does not include "By Holding" in the PDF even though the Allocation page displays it.
*/
async function addAllocationSection(doc, y, allocation) {
    y = addSectionTitle(doc, y, "Allocation");

    if (!allocation) {
        return addParagraph(doc, y, "Allocation data was not available when this report was exported.");
    }

    const sections = [
        ["By Asset Class", allocation.byAssetClass],
        ["By Sector", allocation.bySector],
        ["By Market", allocation.byMarket],
        ["By Currency", allocation.byCurrency]
    ];

    for (const [title, items] of sections) {
        y = ensureSpace(doc, y, 60);

        doc.setFontSize(11);
        doc.text(title, 14, y);
        y += 5;

        if (!Array.isArray(items) || items.length === 0) {
            y = addParagraph(doc, y, "No allocation data available for this category.");
            continue;
        }

        /*
        Allocation charts are generated specifically for the PDF using temporary off-screen canvases.
        They are not copied from the visible Allocation page.
        */
        const chartImage = await createAllocationChartImage(title, items);

        if (chartImage) {
            doc.addImage(chartImage, "PNG", 14, y, 180, 65);
            y += 70;
        }

        const rows = items.map(item => [
            safeText(item.label),
            money(item.value),
            percent(toNumber(item.weightPercent))
        ]);

        doc.autoTable({
            startY: y,
            head: [["Label", "Value", "Weight"]],
            body: rows,
            theme: "striped",
            styles: {
                fontSize: 8
            },
            headStyles: {
                fillColor: [40, 40, 40]
            }
        });

        y = doc.lastAutoTable.finalY + 8;
    }

    return y;
}

/*
Adds benchmark comparison section.

Important:
This section does not call the benchmark API again.
It reads the benchmark result currently displayed on the Analysis page.
If benchmark analysis has not been run, it writes a message explaining that.
*/
function addBenchmarkSection(doc, y) {
    y = addSectionTitle(doc, y, "Benchmark Comparison");

    const analysisContent = document.getElementById("analysisContent");

    if (!isVisible(analysisContent)) {
        return addParagraph(
            doc,
            y,
            "Benchmark comparison was not calculated in this session. Run benchmark analysis on the Analysis page to include this section."
        );
    }

    const rows = [
        ["Benchmark", textOf("summaryBenchmarkSymbol") || valueOfInput("benchmarkSymbol")],
        ["Date range", textOf("summaryDateRange")],
        ["Portfolio return", textOf("portfolioReturnPercent")],
        ["Portfolio value", textOf("portfolioValueSummary")],
        ["Benchmark return", textOf("benchmarkReturnPercent")],
        ["Benchmark value", textOf("benchmarkValueSummary")],
        ["Excess return", textOf("excessReturnPercent")],
        ["Result", textOf("summaryResultText")]
    ];

    doc.autoTable({
        startY: y,
        head: [["Metric", "Value"]],
        body: rows,
        theme: "grid",
        styles: {
            fontSize: 9
        },
        headStyles: {
            fillColor: [40, 40, 40]
        }
    });

    y = doc.lastAutoTable.finalY + 8;

    /*
    Capture the visible Chart.js benchmark chart.
    Canvas.toDataURL converts the chart into an image that jsPDF can insert.
    */
    const chart = document.getElementById("analysisChart");

    if (chart && chart.toDataURL) {
        y = ensureSpace(doc, y, 75);
        doc.setFontSize(11);
        doc.text("Indexed Performance Chart", 14, y);
        y += 5;

        doc.addImage(chart.toDataURL("image/png"), "PNG", 14, y, 180, 65);
        y += 72;
    }

    return y;
}

/*
Adds portfolio optimisation section.

Important:
This section does not call the optimisation API again.
It reads the optimisation result currently displayed on the Analysis page.
If optimisation has not been run, it writes a message explaining that.
*/
function addOptimisationSection(doc, y) {
    y = addSectionTitle(doc, y, "Portfolio Optimisation");

    const optimisationContent = document.getElementById("optimisationContent");

    if (!isVisible(optimisationContent)) {
        return addParagraph(
            doc,
            y,
            "Portfolio optimisation was not calculated in this session. Run portfolio optimisation on the Analysis page to include this section."
        );
    }

    const metricRows = [
        ["Current return", textOf("mptCurrentReturn")],
        ["Current volatility", textOf("mptCurrentVolatility")],
        ["Current Sharpe ratio", textOf("mptCurrentSharpe")],
        ["Optimised return", textOf("mptOptimisedReturn")],
        ["Optimised volatility", textOf("mptOptimisedVolatility")],
        ["Optimised Sharpe ratio", textOf("mptOptimisedSharpe")],
        ["Sharpe improvement", textOf("mptSharpeImprovement")]
    ];

    doc.autoTable({
        startY: y,
        head: [["Metric", "Value"]],
        body: metricRows,
        theme: "grid",
        styles: {
            fontSize: 9
        },
        headStyles: {
            fillColor: [40, 40, 40]
        }
    });

    y = doc.lastAutoTable.finalY + 8;

    /*
    Include the efficient frontier chart only when visible.
    This matters because Risk Parity hides this card, while Mean-Variance shows it.
    */
    const efficientFrontierCard = document.getElementById("efficientFrontierCard");
    const chart = document.getElementById("optimisationChart");

    if (isVisible(efficientFrontierCard) && chart && chart.toDataURL) {
        y = ensureSpace(doc, y, 75);
        doc.setFontSize(11);
        doc.text("Efficient Frontier Chart", 14, y);
        y += 5;

        doc.addImage(chart.toDataURL("image/png"), "PNG", 14, y, 180, 65);
        y += 72;
    }

    y = addOptimisationAllocationTable(doc, y);
    y = addListSectionFromDom(doc, y, "Suggestions", "optimisationSuggestionsList");
    y = addListSectionFromDom(doc, y, "Warnings", "optimisationWarningsList");

    return y;
}

/*
Exports the optimisation allocation comparison table.

Important:
This reads table rows directly from the DOM instead of recomputing or refetching optimisation results.
*/
function addOptimisationAllocationTable(doc, y) {
    y = ensureSpace(doc, y, 35);

    doc.setFontSize(11);
    doc.text("Allocation Comparison", 14, y);
    y += 5;

    const tableBody = document.getElementById("optimisationAllocationTableBody");

    if (!tableBody) {
        return addParagraph(doc, y, "No allocation comparison table was available.");
    }

    const rows = Array.from(tableBody.querySelectorAll("tr"))
        .map(row => Array.from(row.querySelectorAll("td")).map(cell => cell.textContent.trim()))
        .filter(cells => cells.length > 1 && !cells.join(" ").includes("No optimisation data yet"));

    if (rows.length === 0) {
        return addParagraph(doc, y, "No optimisation allocation comparison was available.");
    }

    doc.autoTable({
        startY: y,
        head: [["Asset", "Current Weight", "Target Weight", "Difference", "Suggested Change"]],
        body: rows,
        theme: "striped",
        styles: {
            fontSize: 8
        },
        headStyles: {
            fillColor: [40, 40, 40]
        }
    });

    return doc.lastAutoTable.finalY + 8;
}

/*
Exports a list section from the DOM.

Used for:
- Suggestions
- Warnings
*/
function addListSectionFromDom(doc, y, title, listId) {
    y = ensureSpace(doc, y, 30);

    doc.setFontSize(11);
    doc.text(title, 14, y);
    y += 5;

    const list = document.getElementById(listId);

    if (!list) {
        return addParagraph(doc, y, `No ${title.toLowerCase()} were available.`);
    }

    const items = Array.from(list.querySelectorAll("li"))
        .map(item => item.textContent.trim())
        .filter(Boolean);

    if (items.length === 0) {
        return addParagraph(doc, y, `No ${title.toLowerCase()} were available.`);
    }

    for (const item of items) {
        y = addParagraph(doc, y, `- ${item}`);
    }

    return y + 3;
}

/*
Adds the explanation section.

Important:
This does not call the LLM or explanation API again.
It reads the explanation currently visible on the Analysis page.
*/
function addExplanationSection(doc, y) {
    y = addSectionTitle(doc, y, "Explanation");

    const explanationContent = document.getElementById("explanationContent");

    if (!isVisible(explanationContent)) {
        return addParagraph(
            doc,
            y,
            "No explanation was generated in this session. Use the explanation buttons on the Analysis page to include this section."
        );
    }

    const title = textOf("explanationTitle");
    const explanationParagraphs = paragraphsOf("explanationText");

    if (title) {
        doc.setFontSize(11);
        doc.text(title, 14, y);
        y += 6;
    }

    y = addParagraphs(doc, y, explanationParagraphs);

    /*
    Include fallback note if the backend indicated that deterministic fallback explanation was used.
    */
    const fallbackNote = document.getElementById("explanationFallbackNote");

    if (isVisible(fallbackNote)) {
        y = addParagraph(doc, y, "Note: A fallback explanation was used.");
    }

    return y + 4;
}

/*
Adds the educational/financial-advice disclaimer.
This is important because the system is a learning/support tool, not regulated financial advice.
*/
function addDisclaimer(doc, y) {
    y = addSectionTitle(doc, y, "Disclaimer");

    return addParagraph(
        doc,
        y,
        "This report is for educational guidance only. It is based on user-provided portfolio data, available market data, and algorithmic calculations. It does not constitute regulated financial advice."
    );
}

/*
Adds a section heading.

ensureSpace is called first to avoid placing the heading at the bottom of a page.
*/
function addSectionTitle(doc, y, title) {
    y = ensureSpace(doc, y, 18);

    doc.setFontSize(14);
    doc.text(title, 14, y);

    y += 7;

    return y;
}

/*
Adds wrapped paragraph text to the PDF.
doc.splitTextToSize wraps long text so it fits within the page width.
*/
function addParagraph(doc, y, text) {
    y = ensureSpace(doc, y, 18);

    doc.setFontSize(9);

    const lines = doc.splitTextToSize(String(text || ""), 180);

    doc.text(lines, 14, y);

    return y + lines.length * 5 + 2;
}

/*
Adds multiple paragraphs to the PDF.
Used mainly for explanation text so it does not become one long unreadable block.
*/
function addParagraphs(doc, y, paragraphs) {
    const safeParagraphs = Array.isArray(paragraphs)
        ? paragraphs.filter(Boolean)
        : [];

    if (safeParagraphs.length === 0) {
        return addParagraph(doc, y, "No explanation text was available.");
    }

    for (const paragraph of safeParagraphs) {
        y = addParagraph(doc, y, paragraph);
        y += 3;
    }

    return y;
}

/*
Ensures there is enough vertical space left on the page.
If not, add a new page and reset y to the top margin.
*/
function ensureSpace(doc, y, neededHeight) {
    const pageHeight = doc.internal.pageSize.getHeight();

    if (y + neededHeight > pageHeight - 15) {
        doc.addPage();
        return 15;
    }

    return y;
}

/*
Adds page numbers to every page after the document has been fully built.
*/
function addFooterPageNumbers(doc) {
    const pageCount = doc.internal.getNumberOfPages();

    for (let page = 1; page <= pageCount; page++) {
        doc.setPage(page);
        doc.setFontSize(8);
        doc.text(
            `Page ${page} of ${pageCount}`,
            105,
            290,
            {
                align: "center"
            }
        );
    }
}

/*
Creates an allocation chart image for the PDF.

Important:
This creates a temporary off-screen canvas and Chart.js pie chart,
then converts that chart into a PNG image for jsPDF.

The chart is destroyed after conversion to avoid memory leaks.
*/
async function createAllocationChartImage(title, items) {
    if (!window.Chart || !Array.isArray(items) || items.length === 0) {
        return null;
    }

    const canvas = document.createElement("canvas");
    canvas.width = 900;
    canvas.height = 350;

    const context = canvas.getContext("2d");

    const chart = new Chart(context, {
        type: "pie",
        data: {
            labels: items.map(item => safeText(item.label)),
            datasets: [{
                data: items.map(item => toNumber(item.value))
            }]
        },
        options: {
            responsive: false,
            animation: false,
            plugins: {
                title: {
                    display: true,
                    text: title
                },
                legend: {
                    position: "right"
                }
            }
        }
    });

    chart.update();

    const image = canvas.toDataURL("image/png");

    chart.destroy();

    return image;
}

/*
Checks whether a DOM section is currently visible.
This project uses Bootstrap's d-none class to hide sections.
*/
function isVisible(element) {
    if (!element) {
        return false;
    }

    return !element.classList.contains("d-none");
}

/*
Reads text content from a DOM element by ID.
Used to export currently displayed benchmark/optimisation/explanation values.
*/
function textOf(id) {
    const element = document.getElementById(id);

    if (!element) {
        return "";
    }

    return element.textContent.trim();
}

/*
Reads current value from an input/select by ID.
Used for report header fields such as selected benchmark/date/algorithm.
*/
function valueOfInput(id) {
    const element = document.getElementById(id);

    if (!element) {
        return "";
    }

    return element.value ? element.value.trim() : "";
}

/*
Sums a numeric field across an array of objects.
Used for holdings summary totals.
*/
function sum(items, field) {
    if (!Array.isArray(items)) {
        return 0;
    }

    return items.reduce((total, item) => total + toNumber(item[field]), 0);
}

/*
Safely converts a value to a number.
Invalid, missing or non-numeric values become 0.
*/
function toNumber(value) {
    const numberValue = Number(value);

    return Number.isFinite(numberValue) ? numberValue : 0;
}

/*
Converts missing/blank values into a dash for clean PDF display.
*/
function safeText(value) {
    if (value === null || value === undefined || value === "") {
        return "-";
    }

    return String(value);
}

/*
Formats a number for general display.
Used for quantity values.
*/
function number(value) {
    const numeric = toNumber(value);

    return numeric.toLocaleString(undefined, {
        maximumFractionDigits: 6
    });
}

/*
Formats a money-like value without applying a currency symbol.
*/
function money(value) {
    const numeric = toNumber(value);

    return numeric.toLocaleString(undefined, {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    });
}

/*
Formats a number as a percentage-point value.
Example: 12.345 -> 12.35%
*/
function percent(value) {
    const numeric = toNumber(value);

    return `${numeric.toLocaleString(undefined, {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    })}%`;
}

/*
Preserves explanation paragraphs.

Important:
The explanation may be rendered as <p> elements or as text with line breaks.
This helper prevents explanation text from collapsing into one long paragraph in the PDF.
*/
function paragraphsOf(id) {
    const element = document.getElementById(id);

    if (!element) {
        return [];
    }

    const paragraphElements = Array.from(element.querySelectorAll("p"));

    if (paragraphElements.length > 0) {
        return paragraphElements
            .map(paragraph => paragraph.innerText.trim())
            .filter(Boolean);
    }

    return element.innerText
        .split(/\n\s*\n|\n/)
        .map(text => text.trim())
        .filter(Boolean);
}
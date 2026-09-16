/*
Activities page controller.
This file controls the dynamic behaviour of activities.html.

Main responsibilities:
1. Load existing activities from /api/portfolio/activities.
2. Render activities into the activity table.
3. Open the modal for creating or editing activities.
4. Build and validate the activity request payload.
5. Send POST, PUT and DELETE requests for activity CRUD.
6. Search market assets through /api/assets/search.
7. Show/hide form fields depending on activity type and asset type.

The frontend improves user experience, but backend ActivityService remains the source of truth for validation and persistence.
*/

/*
CSRF token/header are rendered into meta tags by activities.html.
They are added to POST, PUT and DELETE requests so Spring Security accepts modifying requests.
*/
const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute("content");
const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute("content");

document.addEventListener("DOMContentLoaded", () => {
    // Backend endpoints used by this page.
    const apiBaseUrl = "/api/portfolio/activities";
    const assetSearchApiUrl = "/api/assets/search";

    // Main page state elements.
    // These IDs must match the elements defined in activities.html.
    const alertContainer = document.getElementById("alertContainer");
    const loadingState = document.getElementById("loadingState");
    const emptyState = document.getElementById("emptyState");
    const tableContainer = document.getElementById("tableContainer");
    const activitiesTableBody = document.getElementById("activitiesTableBody");
    const summaryActivityCount = document.getElementById("summaryActivityCount");
    const refreshButton = document.getElementById("refreshButton");
    const openCreateModalButton = document.getElementById("openCreateModalButton");

    // Modal and form elements.
    // The same form is used for both create and edit.
    const activityForm = document.getElementById("activityForm");
    const activityModalElement = document.getElementById("activityModal");
    const activityModalLabel = document.getElementById("activityModalLabel");
    const formError = document.getElementById("formError");
    const saveActivityButton = document.getElementById("saveActivityButton");

    // Hidden ID decides whether submit means create or update.
    // Empty ID = POST create. Existing ID = PUT update.
    const activityIdInput = document.getElementById("activityId");
    const typeInput = document.getElementById("type");
    const assetTypeInput = document.getElementById("assetType");

    // Market asset search elements.
    // The user sees assetSearchInput, while symbol/displayName store the selected result.
    const marketSearchGroup = document.getElementById("marketSearchGroup");
    const assetSearchInput = document.getElementById("assetSearch");
    const assetSearchResults = document.getElementById("assetSearchResults");

    const symbolInput = document.getElementById("symbol");
    const displayNameInput = document.getElementById("displayName");

    // Manual asset fields.
    // These are used for assets that do not come from market search.
    const manualNameGroup = document.getElementById("manualNameGroup");
    const manualDisplayNameInput = document.getElementById("manualDisplayName");

    // Real estate-specific address field.
    const addressGroup = document.getElementById("addressGroup");
    const addressInput = document.getElementById("address");

    // Common activity form fields.
    const dateInput = document.getElementById("date");
    const quantityGroup = document.getElementById("quantityGroup");
    const quantityInput = document.getElementById("quantity");
    const unitPriceInput = document.getElementById("unitPrice");
    const feeInput = document.getElementById("fee");
    const currencyInput = document.getElementById("currency");
    const commentInput = document.getElementById("comment");

    // Bootstrap modal instance used by create/edit functions.
    const activityModal = new bootstrap.Modal(activityModalElement);

    // Used to debounce asset search so the API is not called on every keystroke immediately.
    let searchTimeoutId = null;

    // Page event listeners.
    refreshButton.addEventListener("click", loadActivities);
    openCreateModalButton.addEventListener("click", openCreateModal);
    activityForm.addEventListener("submit", onSubmitForm);

    // When activity type or asset type changes, the form must show/hide relevant fields.
    typeInput.addEventListener("change", updateDynamicFields);
    assetTypeInput.addEventListener("change", () => {
        clearSelectedMarketAsset();
        updateDynamicFields();
    });

    // Market asset search input handler.
    assetSearchInput.addEventListener("input", onAssetSearchInput);

    // Initial page load.
    loadActivities();

    /*
    Loads all activities for the authenticated user.

    Flow:
    1. Show loading state.
    2. Call GET /api/portfolio/activities.
    3. Render the returned activity list.
    4. Show an error if loading fails.
    */
    async function loadActivities() {
        showLoading();
        clearAlert();

        try {
            const response = await fetch(apiBaseUrl, {
                method: "GET",
                headers: { "Accept": "application/json" }
            });

            if (!response.ok) {
                throw new Error(`Failed to load activities. HTTP ${response.status}`);
            }

            const activities = await response.json();
            renderActivities(activities);
        } catch (error) {
            console.error("Error loading activities:", error);
            showError("Unable to load activities right now. Please try again.");
            hideLoading();
            showTable(false);
        }
    }

    /*
    Renders the activity list table.
    The backend returns activity response objects.
    The frontend displays them and adds Edit/Delete buttons for each row.
    */
    function renderActivities(activities) {
        hideLoading();
        activitiesTableBody.innerHTML = "";

        if (!Array.isArray(activities) || activities.length === 0) {
            summaryActivityCount.textContent = "0";
            emptyState.classList.remove("d-none");
            tableContainer.classList.add("d-none");
            return;
        }

        summaryActivityCount.textContent = String(activities.length);
        emptyState.classList.add("d-none");
        tableContainer.classList.remove("d-none");

        activities.forEach(activity => {
            // Choose the most useful visible asset label.
            // Market assets usually have displayName/symbol; real estate may use address.
            const assetLabel = activity.displayName || activity.symbol || activity.address || "-";

            const row = document.createElement("tr");
            row.innerHTML = `
                <td>${formatDate(activity.date)}</td>
                <td><span class="badge text-bg-light">${escapeHtml(activity.type ?? "-")}</span></td>
                <td>${escapeHtml(activity.assetType ?? "-")}</td>
                <td>
                    <div>${escapeHtml(assetLabel)}</div>
                    <div class="small text-muted">${escapeHtml(activity.symbol ?? "")}</div>
                </td>
                <td class="text-end">${activity.quantity == null ? "-" : formatNumber(activity.quantity, 6)}</td>
                <td class="text-end">${formatMoney(activity.unitPrice, activity.currency)}</td>
                <td class="text-end">${formatMoney(activity.fee, activity.currency)}</td>
                <td>${escapeHtml(activity.currency ?? "-")}</td>
                <td>${escapeHtml(activity.comment ?? "-")}</td>
                <td class="text-end">
                    <button type="button" class="btn btn-sm btn-outline-primary me-2 edit-button" data-id="${escapeHtml(activity.id)}">
                        Edit
                    </button>
                    <button type="button" class="btn btn-sm btn-outline-danger delete-button" data-id="${escapeHtml(activity.id)}">
                        Delete
                    </button>
                </td>
            `;

            activitiesTableBody.appendChild(row);
        });

        bindRowButtons();
    }

    /*
    Binds event handlers for dynamically created Edit/Delete buttons.
    This must run after renderActivities() because the buttons do not exist before the table rows are rendered.
    */
    function bindRowButtons() {
        document.querySelectorAll(".edit-button").forEach(button => {
            button.addEventListener("click", async event => {
                await openEditModal(event.currentTarget.dataset.id);
            });
        });

        document.querySelectorAll(".delete-button").forEach(button => {
            button.addEventListener("click", async event => {
                await deleteActivity(event.currentTarget.dataset.id);
            });
        });
    }

    /*
    Opens the modal in create mode.

    Important behaviour:
    - Clears previous form state.
    - Clears hidden activityId, so submit uses POST.
    - Sets sensible defaults: current date/time, USD currency, fee 0.
    - Calls updateDynamicFields() so the correct form fields are visible.
    */
    function openCreateModal() {
        activityForm.reset();
        activityForm.classList.remove("was-validated");
        clearFormError();
        clearSelectedMarketAsset();
        clearSearchResults();

        activityIdInput.value = "";
        activityModalLabel.textContent = "Add Activity";
        feeInput.value = "0";
        dateInput.value = currentDateTimeLocalValue();
        currencyInput.value = "USD";

        updateDynamicFields();
        activityModal.show();
    }

    /*
    Opens the modal in edit mode.

    Flow:
    1. GET /api/portfolio/activities/{id}.
    2. Fill form fields with returned activity data.
    3. Store the activity ID in the hidden field.
    4. Show the modal.
    */
    async function openEditModal(id) {
        clearFormError();
        clearSearchResults();
        activityForm.classList.remove("was-validated");

        try {
            const response = await fetch(`${apiBaseUrl}/${encodeURIComponent(id)}`, {
                method: "GET",
                headers: { "Accept": "application/json" }
            });

            if (!response.ok) {
                throw new Error(`Failed to load activity. HTTP ${response.status}`);
            }

            const activity = await response.json();

            activityIdInput.value = activity.id ?? "";
            typeInput.value = activity.type ?? "";
            assetTypeInput.value = activity.assetType ?? "";

            symbolInput.value = activity.symbol ?? "";
            displayNameInput.value = activity.displayName ?? "";
            assetSearchInput.value = activity.displayName || activity.symbol || "";

            manualDisplayNameInput.value = activity.displayName ?? "";
            addressInput.value = activity.address ?? "";

            dateInput.value = toDateTimeLocalValue(activity.date);
            quantityInput.value = activity.quantity ?? "";
            unitPriceInput.value = activity.unitPrice ?? "";
            feeInput.value = activity.fee ?? "0";
            currencyInput.value = activity.currency ?? "";
            commentInput.value = activity.comment ?? "";

            activityModalLabel.textContent = "Edit Activity";
            updateDynamicFields();
            activityModal.show();
        } catch (error) {
            console.error("Error loading activity:", error);
            showError("Unable to load the selected activity.");
        }
    }

    /*
    Handles form submission for both create and edit.

    Decision:
    - if activityId is empty, send POST /api/portfolio/activities
    - if activityId exists, send PUT /api/portfolio/activities/{id}

    The request includes CSRF token when available.
    */
    async function onSubmitForm(event) {
        event.preventDefault();
        clearFormError();

        activityForm.classList.add("was-validated");

        if (!activityForm.checkValidity()) {
            return;
        }

        const payload = buildPayload();

        if (!validateBusinessFields(payload)) {
            return;
        }

        const id = activityIdInput.value.trim();
        const isEdit = id.length > 0;
        const url = isEdit ? `${apiBaseUrl}/${encodeURIComponent(id)}` : apiBaseUrl;
        const method = isEdit ? "PUT" : "POST";

        saveActivityButton.disabled = true;

        try {
            const headers = {
                "Content-Type": "application/json",
                "Accept": "application/json"
            };

            if (csrfToken && csrfHeader) {
                headers[csrfHeader] = csrfToken;
            }

            const response = await fetch(url, {
                method,
                headers,
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                const errorBody = await tryReadJson(response);
                throw new Error(extractErrorMessage(errorBody, `Failed to save activity. HTTP ${response.status}`));
            }

            activityModal.hide();
            showSuccess(isEdit ? "Activity updated successfully." : "Activity created successfully.");
            await loadActivities();
        } catch (error) {
            console.error("Error saving activity:", error);
            showFormError(error.message || "Unable to save activity.");
        } finally {
            saveActivityButton.disabled = false;
        }
    }

    /*
    Deletes one activity after user confirmation.

    Flow:
    1. Ask for confirmation.
    2. Send DELETE /api/portfolio/activities/{id}.
    3. Reload the activity list after success.
    */
    async function deleteActivity(id) {
        if (!window.confirm("Delete this activity?")) {
            return;
        }

        clearAlert();

        try {
            const headers = {};
            if (csrfToken && csrfHeader) {
                headers[csrfHeader] = csrfToken;
            }

            const response = await fetch(`${apiBaseUrl}/${encodeURIComponent(id)}`, {
                method: "DELETE",
                headers
            });

            if (!response.ok) {
                const errorBody = await tryReadJson(response);
                throw new Error(extractErrorMessage(errorBody, `Failed to delete activity. HTTP ${response.status}`));
            }

            showSuccess("Activity deleted successfully.");
            await loadActivities();
        } catch (error) {
            console.error("Error deleting activity:", error);
            showError(error.message || "Unable to delete activity.");
        }
    }

    /*
    Builds the ActivityRequest payload sent to the backend.

    Important behaviour:
    - Market assets send symbol and displayName from the selected search result.
    - Manual assets send manualDisplayName instead.
    - Real estate sends address and no quantity.
    - Manual/value-based assets send quantity as null.
    - Currency is normalised to uppercase.
    */
    function buildPayload() {
        const assetType = normalizeString(assetTypeInput.value);
        const isMarket = isMarketAsset(assetType);
        const isRealEstate = assetType === "REAL_ESTATE";

        return {
            type: normalizeString(typeInput.value),
            assetType,
            symbol: isMarket ? normalizeOptionalString(symbolInput.value) : null,
            displayName: isMarket
                ? normalizeOptionalString(displayNameInput.value)
                : normalizeOptionalString(manualDisplayNameInput.value),
            address: isRealEstate ? normalizeOptionalString(addressInput.value) : null,
            date: normalizeDateTimeLocal(dateInput.value),
            quantity: isRealEstate || isManualAsset(assetType)
                ? null
                : normalizeOptionalNumber(quantityInput.value),
            unitPrice: normalizeOptionalNumber(unitPriceInput.value),
            fee: normalizeOptionalNumber(feeInput.value) ?? 0,
            currency: normalizeString(currencyInput.value)?.toUpperCase(),
            comment: normalizeOptionalString(commentInput.value)
        };
    }

    /*
    Frontend business validation.
    This improves user experience before sending the request, but backend ActivityService still validates authoritatively.

    Main rules:
    - BUY/SELL require asset type.
    - Market assets require selected symbol, quantity and unit price.
    - Real estate requires property name, address and value.
    - Manual assets require name and value.
    - FEE requires positive fee.
    */
    function validateBusinessFields(payload) {
        if (payload.type === "BUY" || payload.type === "SELL") {
            if (!payload.assetType) {
                showFormError("Asset type is required for BUY and SELL.");
                return false;
            }

            if (isMarketAsset(payload.assetType)) {
                if (!payload.symbol || !payload.displayName) {
                    showFormError("Please search for and select a market asset.");
                    return false;
                }

                if (payload.quantity == null || payload.quantity <= 0) {
                    showFormError("Quantity must be greater than 0 for market assets.");
                    return false;
                }

                if (payload.unitPrice == null || payload.unitPrice <= 0) {
                    showFormError("Unit price must be greater than 0.");
                    return false;
                }
            } else if (payload.assetType === "REAL_ESTATE") {
                if (!payload.displayName) {
                    showFormError("Property name is required.");
                    return false;
                }

                if (!payload.address) {
                    showFormError("Address is required for real estate.");
                    return false;
                }

                if (payload.unitPrice == null || payload.unitPrice <= 0) {
                    showFormError("Purchase value must be greater than 0 for real estate.");
                    return false;
                }
            } else {
                if (!payload.displayName) {
                    showFormError("Asset name is required.");
                    return false;
                }

                if (payload.unitPrice == null || payload.unitPrice <= 0) {
                    showFormError("Value must be greater than 0 for manual assets.");
                    return false;
                }
            }
        }

        if (payload.type === "FEE") {
            if (payload.fee == null || payload.fee <= 0) {
                showFormError("Fee must be greater than 0 for FEE activity.");
                return false;
            }
        }

        return true;
    }

    /*
    Shows or hides form fields based on selected activity type and asset type.

    Examples:
    - BUY + EQUITY: show market search and quantity.
    - BUY + REAL_ESTATE: show manual name and address, hide quantity.
    - BUY + ART/OTHER_MANUAL: show manual name, hide market search and quantity.
    - FEE/CASH types: disable asset-specific fields.
    */
    function updateDynamicFields() {
        const activityType = typeInput.value;
        const assetType = assetTypeInput.value;
        const isTrade = activityType === "BUY" || activityType === "SELL";
        const market = isMarketAsset(assetType);
        const realEstate = assetType === "REAL_ESTATE";
        const manual = isManualAsset(assetType) || realEstate;

        assetTypeInput.disabled = !isTrade;

        toggleGroup(marketSearchGroup, isTrade && market);
        toggleGroup(manualNameGroup, isTrade && manual);
        toggleGroup(addressGroup, isTrade && realEstate);
        toggleGroup(quantityGroup, isTrade && market);

        if (!isTrade) {
            clearSelectedMarketAsset();
            manualDisplayNameInput.value = "";
            addressInput.value = "";
            quantityInput.value = "";
        }

        if (realEstate || isManualAsset(assetType)) {
            quantityInput.value = "";
        }

        if (!realEstate) {
            addressInput.value = "";
        }

        if (!market) {
            clearSelectedMarketAsset();
            clearSearchResults();
        }
    }

    // Small helper for showing/hiding form groups.
    function toggleGroup(element, visible) {
        if (!element) {
            return;
        }

        if (visible) {
            element.classList.remove("d-none");
        } else {
            element.classList.add("d-none");
        }
    }

    /*
    Handles typing in the market asset search box.

    Important behaviour:
    - Clears previous selected symbol/displayName when the user types again.
    - Requires at least 2 characters before searching.
    - Uses a 350ms debounce to reduce unnecessary API calls.
    */
    function onAssetSearchInput() {
        clearSelectedMarketAsset();

        const query = assetSearchInput.value.trim();
        if (query.length < 2) {
            clearSearchResults();
            return;
        }

        window.clearTimeout(searchTimeoutId);
        searchTimeoutId = window.setTimeout(() => {
            searchAssets(query);
        }, 350);
    }

    /*
    Calls the asset search API for market assets.
    This supports selecting assets such as stocks/ETFs/bonds before saving an activity.
    */
    async function searchAssets(query) {
        try {
            const params = new URLSearchParams({ query });
            const response = await fetch(`${assetSearchApiUrl}?${params.toString()}`, {
                headers: { "Accept": "application/json" }
            });

            if (!response.ok) {
                throw new Error(`Search failed. HTTP ${response.status}`);
            }

            const results = await response.json();
            renderSearchResults(results);
        } catch (error) {
            console.error("Asset search failed:", error);
            clearSearchResults();
        }
    }

    /*
    Renders clickable asset search results.
    Each result button calls selectSearchResult(result), which stores the selected symbol/displayName.
    */
    function renderSearchResults(results) {
        assetSearchResults.innerHTML = "";

        if (!Array.isArray(results) || results.length === 0) {
            assetSearchResults.classList.add("d-none");
            return;
        }

        results.forEach(result => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "list-group-item list-group-item-action";
            button.innerHTML = `
                <div class="fw-semibold">${escapeHtml(result.displayName ?? "-")}</div>
                <div class="small text-muted">
                    ${escapeHtml(result.symbol ?? "-")}
                    ${result.assetType ? " · " + escapeHtml(result.assetType) : ""}
                    ${result.market ? " · " + escapeHtml(result.market) : ""}
                    ${result.currency ? " · " + escapeHtml(result.currency) : ""}
                </div>
            `;

            button.addEventListener("click", () => selectSearchResult(result));
            assetSearchResults.appendChild(button);
        });

        assetSearchResults.classList.remove("d-none");
    }

    /*
    Stores the selected market asset into hidden form fields.
    This is important because the backend needs symbol and displayName, not just the text typed into the search box.
    */
    function selectSearchResult(result) {
        symbolInput.value = result.symbol ?? "";
        displayNameInput.value = result.displayName ?? "";
        assetSearchInput.value = result.displayName
            ? `${result.displayName} (${result.symbol})`
            : result.symbol ?? "";

        if (result.assetType) {
            assetTypeInput.value = result.assetType;
        }

        if (result.currency) {
            currencyInput.value = result.currency.trim().toUpperCase();
        }

        clearSearchResults();
        updateDynamicFields();
    }

    // Clears selected market asset when the user changes asset type or edits the search text.
    function clearSelectedMarketAsset() {
        symbolInput.value = "";
        displayNameInput.value = "";
    }

    // Hides and clears the search result list.
    function clearSearchResults() {
        assetSearchResults.innerHTML = "";
        assetSearchResults.classList.add("d-none");
    }

    // Market assets require symbol search and quantity-based BUY/SELL input.
    function isMarketAsset(assetType) {
        return ["EQUITY", "ETF", "BOND", "CRYPTO", "FOREX", "COMMODITY"].includes(assetType);
    }

    // Manual assets are entered by name/value rather than market symbol search.
    function isManualAsset(assetType) {
        return ["ART", "OTHER_MANUAL"].includes(assetType);
    }

    // Normalises a required string. Blank becomes null.
    function normalizeString(value) {
        const trimmed = value?.trim();
        return trimmed ? trimmed : null;
    }

    // Normalises an optional string. Blank becomes null.
    function normalizeOptionalString(value) {
        const trimmed = value?.trim();
        return trimmed ? trimmed : null;
    }

    // Converts optional numeric input to a JavaScript number. Blank or invalid input becomes null.
    function normalizeOptionalNumber(value) {
        if (value == null || value === "") {
            return null;
        }

        const number = Number(value);
        return Number.isFinite(number) ? number : null;
    }

    // Converts the datetime-local input value into an ISO timestamp for the backend.
    function normalizeDateTimeLocal(value) {
        if (!value) {
            return null;
        }

        const date = new Date(value);
        return date.toISOString();
    }

    // Provides the default current local datetime for the create modal.
    function currentDateTimeLocalValue() {
        const now = new Date();
        const offset = now.getTimezoneOffset();
        const local = new Date(now.getTime() - offset * 60 * 1000);
        return local.toISOString().slice(0, 16);
    }

    // Converts backend ISO datetime into datetime-local input format for edit mode.
    function toDateTimeLocalValue(isoString) {
        if (!isoString) {
            return "";
        }

        const date = new Date(isoString);
        const offset = date.getTimezoneOffset();
        const local = new Date(date.getTime() - offset * 60 * 1000);
        return local.toISOString().slice(0, 16);
    }

    // Display-only formatter for activity dates in the table.
    function formatDate(value) {
        if (!value) {
            return "-";
        }

        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return "-";
        }

        return new Intl.DateTimeFormat(undefined, {
            year: "numeric",
            month: "short",
            day: "2-digit",
            hour: "2-digit",
            minute: "2-digit"
        }).format(date);
    }

    // Display-only formatter for money values such as unit price and fee.
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
                currency,
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            }).format(number);
        } catch {
            return `${number.toFixed(2)} ${currency}`;
        }
    }

    // Display-only formatter for quantities.
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

    // Shows the activity list loading state.
    function showLoading() {
        loadingState.classList.remove("d-none");
        emptyState.classList.add("d-none");
        tableContainer.classList.add("d-none");
    }

    // Hides the activity list loading state.
    function hideLoading() {
        loadingState.classList.add("d-none");
    }

    // Shows or hides the table container.
    function showTable(visible) {
        if (visible) {
            tableContainer.classList.remove("d-none");
        } else {
            tableContainer.classList.add("d-none");
        }
    }

    // Shows a page-level success alert.
    function showSuccess(message) {
        alertContainer.innerHTML = `
            <div class="alert alert-success alert-dismissible fade show" role="alert">
                ${escapeHtml(message)}
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            </div>
        `;
    }

    // Shows a page-level error alert.
    function showError(message) {
        alertContainer.innerHTML = `
            <div class="alert alert-danger alert-dismissible fade show" role="alert">
                ${escapeHtml(message)}
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            </div>
        `;
    }

    // Clears page-level alerts.
    function clearAlert() {
        alertContainer.innerHTML = "";
    }

    // Shows an error inside the modal form.
    function showFormError(message) {
        formError.textContent = message;
        formError.classList.remove("d-none");
    }

    // Clears the modal form error.
    function clearFormError() {
        formError.textContent = "";
        formError.classList.add("d-none");
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
    Supports common formats:
    - detail
    - message
    - errors object
    Falls back to the provided default message.
    */
    function extractErrorMessage(errorBody, fallbackMessage) {
        if (!errorBody) {
            return fallbackMessage;
        }

        if (typeof errorBody.detail === "string" && errorBody.detail.trim() !== "") {
            return errorBody.detail;
        }

        if (typeof errorBody.message === "string" && errorBody.message.trim() !== "") {
            return errorBody.message;
        }

        if (errorBody.errors && typeof errorBody.errors === "object") {
            const firstMessage = Object.values(errorBody.errors)[0];
            if (typeof firstMessage === "string" && firstMessage.trim() !== "") {
                return firstMessage;
            }
        }

        return fallbackMessage;
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
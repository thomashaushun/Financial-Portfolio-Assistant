/*
Asset Profiles page controller.
This file controls the dynamic behaviour of asset-profiles.html.

Main responsibilities:
1. Load asset profiles from /api/asset-profiles.
2. Render asset profile rows in the table.
3. Open the modal for creating or editing asset profiles.
4. Build and submit the asset profile request payload.
5. Send POST, PUT and DELETE requests for asset profile CRUD.
6. Search market assets through /api/assets/search.
7. Enrich selected assets through /api/assets/metadata.
8. Show loading, empty, success, error and form validation states.

The frontend displays and collects metadata.
Backend services remain responsible for persistence, validation and business rules.
*/
document.addEventListener("DOMContentLoaded", () => {
    // Main CRUD endpoint for asset profile records.
    const apiBaseUrl = "/api/asset-profiles";

    /*
    CSRF token/header are rendered into meta tags by asset-profiles.html.
    They are added to POST, PUT and DELETE requests so Spring Security accepts modifying requests.
    */
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute("content");
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute("content");

    // Main page state elements.
    // These IDs must match the elements defined in asset-profiles.html.
    const alertContainer = document.getElementById("alertContainer");
    const loadingState = document.getElementById("loadingState");
    const emptyState = document.getElementById("emptyState");
    const tableContainer = document.getElementById("tableContainer");
    const tableBody = document.getElementById("assetProfilesTableBody");

    // Page action buttons.
    const refreshButton = document.getElementById("refreshButton");
    const openCreateModalButton = document.getElementById("openCreateModalButton");

    // Bootstrap modal used for both create and edit.
    const modalElement = document.getElementById("assetProfileModal");
    const modalLabel = document.getElementById("assetProfileModalLabel");
    const modal = new bootstrap.Modal(modalElement);

    // Asset profile form elements.
    const form = document.getElementById("assetProfileForm");
    const formError = document.getElementById("formError");
    const saveButton = document.getElementById("saveAssetProfileButton");

    /*
    Hidden ID decides whether submit means create or update.
    Empty ID = POST create.
    Existing ID = PUT update.
    */
    const assetProfileIdInput = document.getElementById("assetProfileId");

    // Main asset profile metadata fields.
    const symbolInput = document.getElementById("symbol");
    const displayNameInput = document.getElementById("displayName");
    const assetClassInput = document.getElementById("assetClass");
    const sectorInput = document.getElementById("sector");
    const marketInput = document.getElementById("market");
    const currencyInput = document.getElementById("currency");
    const dataSourceInput = document.getElementById("dataSource");
    const manualValuationRequiredInput = document.getElementById("manualValuationRequired");
    const priceDataSupportedInput = document.getElementById("priceDataSupported");

    // Asset search elements.
    // assetSearchInput is visible to the user; assetSearchResults contains clickable search results.
    const assetSearchInput = document.getElementById("assetSearch");
    const assetSearchResults = document.getElementById("assetSearchResults");

    // Used to debounce asset search so the search API is not called immediately on every keystroke.
    let assetSearchTimeoutId = null;

    // Page event listeners.
    refreshButton.addEventListener("click", loadAssetProfiles);
    openCreateModalButton.addEventListener("click", openCreateModal);
    form.addEventListener("submit", onSubmit);

    // Asset search is optional defensively, but exists in the current template.
    if (assetSearchInput) {
        assetSearchInput.addEventListener("input", onAssetSearchInput);
    }

    // Initial load when the page opens.
    loadAssetProfiles();

    /*
    Loads all asset profiles for the authenticated user.

    Flow:
    1. Show loading state.
    2. Call GET /api/asset-profiles.
    3. Render the returned profile list.
    4. Show an error if loading fails.
    */
    async function loadAssetProfiles() {
        showLoading();
        clearAlert();
        try {
            const response = await fetch(apiBaseUrl, {
                headers: { "Accept": "application/json" }
            });
            if (!response.ok) {
                throw new Error(`Failed to load asset profiles. HTTP ${response.status}`);
            }

            const profiles = await response.json();
            renderProfiles(profiles);
        } catch (error) {
            console.error(error);
            showError("Unable to load asset profiles.");
            hideLoading();
        }
    }

    /*
    Renders asset profiles into the table.

    The backend returns asset profile response objects.
    The frontend displays the metadata and creates Edit/Delete buttons for each row.
    */
    function renderProfiles(profiles) {
        hideLoading();
        tableBody.innerHTML = "";

        if (!Array.isArray(profiles) || profiles.length === 0) {
            emptyState.classList.remove("d-none");
            tableContainer.classList.add("d-none");
            return;
        }

        emptyState.classList.add("d-none");
        tableContainer.classList.remove("d-none");

        profiles.forEach(profile => {
            const row = document.createElement("tr");
            row.innerHTML = `
                <td>${escapeHtml(profile.symbol ?? "-")}</td>
                <td>${escapeHtml(profile.displayName ?? "-")}</td>
                <td>${escapeHtml(profile.assetClass ?? "-")}</td>
                <td>${escapeHtml(profile.sector ?? "-")}</td>
                <td>${escapeHtml(profile.market ?? "-")}</td>
                <td>${escapeHtml(profile.currency ?? "-")}</td>
                <td>${profile.manualValuationRequired ? "Yes" : "No"}</td>
                <td>${profile.priceDataSupported ? "Yes" : "No"}</td>
                <td>${escapeHtml(profile.dataSource ?? "-")}</td>
                <td class="text-end">
                    <button type="button" class="btn btn-sm btn-outline-primary me-2 edit-button" data-id="${escapeHtml(profile.id)}">Edit</button>
                    <button type="button" class="btn btn-sm btn-outline-danger delete-button" data-id="${escapeHtml(profile.id)}">Delete</button>
                </td>
            `;
            tableBody.appendChild(row);
        });

        bindButtons();
    }

    /*
    Binds event handlers for dynamically created Edit/Delete buttons.
    This must run after renderProfiles(), because the buttons do not exist before the rows are generated.
    */
    function bindButtons() {
        document.querySelectorAll(".edit-button").forEach(button => {
            button.addEventListener("click", async event => {
                await openEditModal(event.currentTarget.dataset.id);
            });
        });

        document.querySelectorAll(".delete-button").forEach(button => {
            button.addEventListener("click", async event => {
                await deleteProfile(event.currentTarget.dataset.id);
            });
        });
    }

    /*
    Opens the modal in create mode.

    Important behaviour:
    - Clears previous form values.
    - Clears assetProfileId, so submit uses POST.
    - Defaults currency to USD.
    - Defaults priceDataSupported to true.
    - Defaults dataSource to ALPHA_VANTAGE.
    */
    function openCreateModal() {
        form.reset();
        form.classList.remove("was-validated");
        clearFormError();

        assetProfileIdInput.value = "";
        modalLabel.textContent = "Add Asset Profile";
        currencyInput.value = "USD";
        priceDataSupportedInput.checked = true;
        modal.show();

        if (assetSearchInput) {
            assetSearchInput.value = "";
        }
        clearAssetSearchResults();

        dataSourceInput.value = "ALPHA_VANTAGE";
    }

    /*
    Opens the modal in edit mode.

    Flow:
    1. Call GET /api/asset-profiles/{id}.
    2. Fill the form with the returned profile.
    3. Store the profile ID in the hidden field.
    4. Show the modal.
    */
    async function openEditModal(id) {
        clearFormError();
        form.classList.remove("was-validated");

        try {
            const response = await fetch(`${apiBaseUrl}/${encodeURIComponent(id)}`, {
                headers: { "Accept": "application/json" }
            });
            if (!response.ok) {
                throw new Error(`Failed to load asset profile. HTTP ${response.status}`);
            }

            const profile = await response.json();
            assetProfileIdInput.value = profile.id ?? "";
            symbolInput.value = profile.symbol ?? "";
            displayNameInput.value = profile.displayName ?? "";
            assetClassInput.value = profile.assetClass ?? "";
            sectorInput.value = profile.sector ?? "";
            marketInput.value = profile.market ?? "";
            currencyInput.value = profile.currency ?? "";
            dataSourceInput.value = profile.dataSource ?? "OTHER";
            manualValuationRequiredInput.checked = !!profile.manualValuationRequired;
            priceDataSupportedInput.checked = !!profile.priceDataSupported;

            modalLabel.textContent = "Edit Asset Profile";
            modal.show();
        } catch (error) {
            console.error(error);
            showError("Unable to load asset profile.");
        }
    }

    /*
    Handles form submission for both create and edit.

    Decision:
    - if assetProfileId is empty, send POST /api/asset-profiles.
    - if assetProfileId exists, send PUT /api/asset-profiles/{id}.

    The request includes CSRF token when available.
    */
    async function onSubmit(event) {
        event.preventDefault();
        form.classList.add("was-validated");
        clearFormError();

        if (!form.checkValidity()) {
            return;
        }

        /*
        Build the request payload expected by the backend.
        Important normalisation:
        - symbol is uppercased.
        - currency is uppercased.
        - optional sector/market blanks become null.
        */
        const payload = {
            symbol: normalizeString(symbolInput.value)?.toUpperCase(),
            displayName: normalizeString(displayNameInput.value),
            assetClass: normalizeString(assetClassInput.value),
            sector: normalizeOptionalString(sectorInput.value),
            market: normalizeOptionalString(marketInput.value),
            currency: normalizeString(currencyInput.value)?.toUpperCase(),
            manualValuationRequired: manualValuationRequiredInput.checked,
            priceDataSupported: priceDataSupportedInput.checked,
            dataSource: normalizeString(dataSourceInput.value)
        };

        const id = assetProfileIdInput.value.trim();
        const isEdit = id.length > 0;
        const url = isEdit ? `${apiBaseUrl}/${encodeURIComponent(id)}` : apiBaseUrl;
        const method = isEdit ? "PUT" : "POST";

        const headers = {
            "Content-Type": "application/json",
            "Accept": "application/json"
        };
        if (csrfToken && csrfHeader) {
            headers[csrfHeader] = csrfToken;
        }

        saveButton.disabled = true;
        try {
            const response = await fetch(url, {
                method,
                headers,
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                const errorBody = await tryReadJson(response);
                throw new Error(extractErrorMessage(errorBody, `Failed to save asset profile. HTTP ${response.status}`));
            }

            modal.hide();
            showSuccess(isEdit ? "Asset profile updated successfully." : "Asset profile created successfully.");
            await loadAssetProfiles();
        } catch (error) {
            console.error(error);
            showFormError(error.message || "Unable to save asset profile.");
        } finally {
            saveButton.disabled = false;
        }
    }

    /*
    Deletes one asset profile after user confirmation.

    Flow:
    1. Ask for confirmation.
    2. Send DELETE /api/asset-profiles/{id}.
    3. Include CSRF token when available.
    4. Reload the table after success.
    */
    async function deleteProfile(id) {
        if (!window.confirm("Delete this asset profile?")) {
            return;
        }

        const headers = {};
        if (csrfToken && csrfHeader) {
            headers[csrfHeader] = csrfToken;
        }

        try {
            const response = await fetch(`${apiBaseUrl}/${encodeURIComponent(id)}`, {
                method: "DELETE",
                headers
            });

            if (!response.ok) {
                const errorBody = await tryReadJson(response);
                throw new Error(extractErrorMessage(errorBody, `Failed to delete asset profile. HTTP ${response.status}`));
            }

            showSuccess("Asset profile deleted successfully.");
            await loadAssetProfiles();
        } catch (error) {
            console.error(error);
            showError(error.message || "Unable to delete asset profile.");
        }
    }

    // Shows the asset-profile table loading state.
    function showLoading() {
        loadingState.classList.remove("d-none");
        emptyState.classList.add("d-none");
        tableContainer.classList.add("d-none");
    }

    // Hides the loading state.
    function hideLoading() {
        loadingState.classList.add("d-none");
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
    - errors object
    Falls back to the provided default message.
    */
    function extractErrorMessage(errorBody, fallbackMessage) {
        if (!errorBody) return fallbackMessage;
        if (typeof errorBody.detail === "string" && errorBody.detail.trim() !== "") return errorBody.detail;
        if (errorBody.errors && typeof errorBody.errors === "object") {
            const first = Object.values(errorBody.errors)[0];
            if (typeof first === "string" && first.trim() !== "") return first;
        }
        return fallbackMessage;
    }

    // Shows a page-level success alert.
    function showSuccess(message) {
        alertContainer.innerHTML = `
            <div class="alert alert-success alert-dismissible fade show" role="alert">
                ${escapeHtml(message)}
                <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
            </div>
        `;
    }

    // Shows a page-level error alert.
    function showError(message) {
        alertContainer.innerHTML = `
            <div class="alert alert-danger alert-dismissible fade show" role="alert">
                ${escapeHtml(message)}
                <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
            </div>
        `;
    }

    // Clears page-level alerts.
    function clearAlert() {
        alertContainer.innerHTML = "";
    }

    // Shows a form-level error inside the modal.
    function showFormError(message) {
        formError.textContent = message;
        formError.classList.remove("d-none");
    }

    // Clears form-level modal errors.
    function clearFormError() {
        formError.textContent = "";
        formError.classList.add("d-none");
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
    Handles typing in the asset search field.

    Important behaviour:
    - Requires at least 2 characters before searching.
    - Uses a 350ms debounce to reduce unnecessary API calls.
    */
    function onAssetSearchInput() {
        const query = assetSearchInput.value.trim();

        if (query.length < 2) {
            clearAssetSearchResults();
            return;
        }

        window.clearTimeout(assetSearchTimeoutId);
        assetSearchTimeoutId = window.setTimeout(() => {
            searchAssets(query);
        }, 350);
    }

    /*
    Calls the asset search API.
    Used so the user can search for a market asset and select a result instead of typing all metadata manually.
    */
    async function searchAssets(query) {
        try {
            const params = new URLSearchParams({ query });

            const response = await fetch(`/api/assets/search?${params.toString()}`, {
                headers: {
                    Accept: "application/json"
                }
            });

            if (!response.ok) {
                throw new Error(`Asset search failed. HTTP ${response.status}`);
            }

            const results = await response.json();
            renderAssetSearchResults(results);
        } catch (error) {
            console.error("Asset search failed:", error);
            clearAssetSearchResults();
        }
    }

    /*
    Renders clickable search results.
    Each result fills the modal form when clicked.
    */
    function renderAssetSearchResults(results) {
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

            button.addEventListener("click", () => selectAssetSearchResult(result));
            assetSearchResults.appendChild(button);
        });

        assetSearchResults.classList.remove("d-none");
    }

    /*
    Handles selection of one search result.

    It fills basic asset metadata into the form:
    - symbol
    - display name
    - asset class
    - market
    - currency
    - data source/support flags

    It then calls metadata enrichment to fill extra details such as sector.
    */
    async function selectAssetSearchResult(result) {
        symbolInput.value = result.symbol ?? "";
        displayNameInput.value = result.displayName ?? "";

        if (result.assetType) {
            assetClassInput.value = mapAssetTypeToAssetClass(result.assetType);
        }

        if (result.market) {
            marketInput.value = result.market;
        }

        if (result.currency) {
            currencyInput.value = result.currency.trim().toUpperCase();
        }

        dataSourceInput.value = "ALPHA_VANTAGE";
        manualValuationRequiredInput.checked = false;
        priceDataSupportedInput.checked = true;

        assetSearchInput.value = result.displayName
            ? `${result.displayName} (${result.symbol})`
            : result.symbol ?? "";

        clearAssetSearchResults();

        if (result.symbol) {
            await enrichAssetProfileFromMetadata(result.symbol);
        }
    }

    /*
    Enriches the selected asset using metadata endpoint.

    Flow:
    1. Call GET /api/assets/metadata?symbol=...
    2. If metadata exists, fill fields such as sector, market, currency, data source and support flags.
    3. This does not save the profile; the user still needs to submit the form.
    */
    async function enrichAssetProfileFromMetadata(symbol) {
        try {
            const params = new URLSearchParams({ symbol });

            const response = await fetch(`/api/assets/metadata?${params.toString()}`, {
                headers: {
                    Accept: "application/json"
                }
            });

            if (!response.ok) {
                return;
            }

            const metadata = await response.json();

            if (metadata.symbol) {
                symbolInput.value = metadata.symbol;
            }

            if (metadata.displayName) {
                displayNameInput.value = metadata.displayName;
            }

            if (metadata.assetClass) {
                assetClassInput.value = metadata.assetClass;
            }

            if (metadata.sector) {
                sectorInput.value = metadata.sector;
            }

            if (metadata.market) {
                marketInput.value = metadata.market;
            }

            if (metadata.currency) {
                currencyInput.value = metadata.currency.trim().toUpperCase();
            }

            if (metadata.dataSource) {
                dataSourceInput.value = metadata.dataSource;
            }

            manualValuationRequiredInput.checked = Boolean(metadata.manualValuationRequired);
            priceDataSupportedInput.checked = metadata.priceDataSupported !== false;
        } catch (error) {
            console.error("Failed to enrich asset metadata:", error);
        }
    }

    // Hides and clears the search result list.
    function clearAssetSearchResults() {
        assetSearchResults.innerHTML = "";
        assetSearchResults.classList.add("d-none");
    }

    /*
    Maps asset search result types into the internal AssetClass values used by the asset profile form.
    Unknown or unsupported search types are mapped to OTHER.
    */
    function mapAssetTypeToAssetClass(assetType) {
        switch (assetType) {
            case "EQUITY":
                return "EQUITY";
            case "ETF":
                return "ETF";
            case "BOND":
                return "BOND";
            case "CRYPTO":
                return "CRYPTO";
            case "REAL_ESTATE":
                return "REAL_ESTATE";
            default:
                return "OTHER";
        }
    }
});
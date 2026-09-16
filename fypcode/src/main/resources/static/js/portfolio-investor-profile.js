document.addEventListener("DOMContentLoaded", () => {
    /*
        This is the single backend API used by the investor profile page.
        GET loads the current user's profile.
        POST creates a profile if one does not exist.
        PUT updates the existing profile.
    */
    const apiUrl = "/api/portfolio/investor-profile";

    /*
        These are the main DOM references used by the page.
        If a matching id is changed in investor-profile.html, this JavaScript must be updated too.
    */
    const form = document.getElementById("investorProfileForm");
    const alertContainer = document.getElementById("alertContainer");
    const loadingState = document.getElementById("loadingState");
    const emptyState = document.getElementById("emptyState");
    const profileSummary = document.getElementById("profileSummary");

    /*
        These form fields are the questionnaire inputs sent to the backend.
        The frontend collects these values, but the backend calculates the final risk score
        and risk profile type.
    */
    const fields = {
        age: document.getElementById("age"),
        investmentHorizonYears: document.getElementById("investmentHorizonYears"),
        riskTolerance: document.getElementById("riskTolerance"),
        investmentExperience: document.getElementById("investmentExperience"),
        incomeStability: document.getElementById("incomeStability"),
        investmentGoal: document.getElementById("investmentGoal")
    };

    /*
        This flag decides whether Save Profile uses POST or PUT.
        false = no profile exists yet, so create it with POST.
        true = profile already exists, so update it with PUT.
    */
    let profileExists = false;

    // Load the saved profile immediately when the page opens.
    loadProfile();

    /*
        This is the Save Profile behaviour.
        The submit event is intercepted so the form is saved through AJAX rather than a full page reload.
    */
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        clearAlert();

        // Build a JSON payload from the form, then run simple frontend validation before calling the backend.
        const payload = buildPayload();
        const validationError = validatePayload(payload);
        if (validationError) {
            showError(validationError);
            return;
        }

        try {
            const response = await fetch(apiUrl, {
                // Create new profile with POST; update existing profile with PUT.
                method: profileExists ? "PUT" : "POST",
                headers: jsonHeaders(),
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                const error = await safeJson(response);
                throw new Error(error?.message || `Unable to save profile. HTTP ${response.status}`);
            }

            /*
                The saved response includes backend-calculated fields such as riskScore
                and riskProfileType. The frontend renders them, but does not calculate them itself.
            */
            const savedProfile = await response.json();
            profileExists = true;
            renderProfile(savedProfile);
            showSuccess("Investor profile saved successfully.");
        } catch (error) {
            console.error("Error saving investor profile:", error);
            showError(error.message || "Unable to save investor profile right now.");
        }
    });

    async function loadProfile() {
        /*
            loadProfile runs on page load.
            It decides whether to show an existing profile summary or the empty state.
        */
        showLoading();
        clearAlert();

        try {
            const response = await fetch(apiUrl, {
                method: "GET",
                headers: { "Accept": "application/json" }
            });

            /*
                404 is treated as a normal empty-profile case, not as a page failure.
                This supports new users who have not created an investor profile yet.
            */
            if (response.status === 404) {
                profileExists = false;
                showEmpty();
                return;
            }

            if (!response.ok) {
                throw new Error(`Unable to load profile. HTTP ${response.status}`);
            }

            const profile = await response.json();
            profileExists = true;
            renderProfile(profile);
            populateForm(profile);
        } catch (error) {
            console.error("Error loading investor profile:", error);
            showError("Unable to load investor profile right now.");
            hideLoading();
        }
    }

    function buildPayload() {
        /*
            This converts HTML form input into the JSON shape expected by the backend DTO.
            Select values such as LOW, BEGINNER and RETIREMENT must match backend enum names.
        */
        return {
            age: Number(fields.age.value),
            investmentHorizonYears: Number(fields.investmentHorizonYears.value),
            riskTolerance: fields.riskTolerance.value,
            investmentExperience: fields.investmentExperience.value,
            incomeStability: fields.incomeStability.value,
            investmentGoal: fields.investmentGoal.value
        };
    }

    function validatePayload(payload) {
        /*
            This is frontend validation for user feedback.
            Backend validation should still exist because frontend validation can be bypassed.
        */
        if (!Number.isInteger(payload.age) || payload.age < 18 || payload.age > 100) {
            return "Age must be between 18 and 100.";
        }
        if (!Number.isInteger(payload.investmentHorizonYears) || payload.investmentHorizonYears < 1 || payload.investmentHorizonYears > 60) {
            return "Investment horizon must be between 1 and 60 years.";
        }
        if (!payload.riskTolerance || !payload.investmentExperience || !payload.incomeStability || !payload.investmentGoal) {
            return "Please complete all profile fields.";
        }
        return null;
    }

    function populateForm(profile) {
        /*
            If an existing profile is loaded, this fills the questionnaire form.
            This lets the user review and update previous answers instead of starting from blank fields.
        */
        fields.age.value = profile.age ?? "";
        fields.investmentHorizonYears.value = profile.investmentHorizonYears ?? "";
        fields.riskTolerance.value = profile.riskTolerance ?? "";
        fields.investmentExperience.value = profile.investmentExperience ?? "";
        fields.incomeStability.value = profile.incomeStability ?? "";
        fields.investmentGoal.value = profile.investmentGoal ?? "";
    }

    function renderProfile(profile) {
        /*
            This renders the Current Profile card on the right side of the page.
            It displays both raw questionnaire values and backend-calculated outputs.
        */
        hideLoading();
        emptyState.classList.add("d-none");
        profileSummary.classList.remove("d-none");

        // riskProfileType and riskScore are calculated by the backend assistant/risk assessment logic.
        document.getElementById("summaryRiskProfile").textContent = formatEnum(profile.riskProfileType);
        document.getElementById("summaryRiskScore").textContent = `${profile.riskScore ?? 0}/100`;

        // These values are the saved questionnaire answers.
        document.getElementById("summaryAge").textContent = profile.age ?? "-";
        document.getElementById("summaryHorizon").textContent = `${profile.investmentHorizonYears ?? "-"} years`;
        document.getElementById("summaryRiskTolerance").textContent = formatEnum(profile.riskTolerance);
        document.getElementById("summaryExperience").textContent = formatEnum(profile.investmentExperience);
        document.getElementById("summaryIncomeStability").textContent = formatEnum(profile.incomeStability);
        document.getElementById("summaryGoal").textContent = formatEnum(profile.investmentGoal);
    }

    function jsonHeaders() {
        /*
            POST and PUT requests need JSON headers and CSRF protection.
            The CSRF token/header name are read from meta tags generated by Thymeleaf/Spring Security.
        */
        const headers = {
            "Content-Type": "application/json",
            "Accept": "application/json"
        };

        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

        if (csrfToken && csrfHeader) {
            headers[csrfHeader] = csrfToken;
        }

        return headers;
    }

    async function safeJson(response) {
        /*
            Some error responses may not contain JSON.
            This helper prevents the error-handling path from crashing while trying to parse them.
        */
        try {
            return await response.json();
        } catch (error) {
            return null;
        }
    }

    function showLoading() {
        // Show loading state and hide the other profile states.
        loadingState.classList.remove("d-none");
        emptyState.classList.add("d-none");
        profileSummary.classList.add("d-none");
    }

    function hideLoading() {
        loadingState.classList.add("d-none");
    }

    function showEmpty() {
        /*
            This state is shown for a new user with no investor profile.
            It is triggered when GET /api/portfolio/investor-profile returns 404.
        */
        hideLoading();
        emptyState.classList.remove("d-none");
        profileSummary.classList.add("d-none");
    }

    function showSuccess(message) {
        // Escape the message before inserting it into HTML to avoid rendering unsafe content.
        alertContainer.innerHTML = `<div class="alert alert-success">${escapeHtml(message)}</div>`;
    }

    function showError(message) {
        // Escape the message before inserting it into HTML to avoid rendering unsafe content.
        alertContainer.innerHTML = `<div class="alert alert-danger">${escapeHtml(message)}</div>`;
    }

    function clearAlert() {
        alertContainer.innerHTML = "";
    }

    function formatEnum(value) {
        /*
            Backend enums are stored as values like LONG_TERM_GROWTH.
            This converts them into readable text like "Long Term Growth" for display only.
            It does not change the stored/backend value.
        */
        if (!value) {
            return "-";
        }
        return String(value)
            .toLowerCase()
            .split("_")
            .map(part => part.charAt(0).toUpperCase() + part.slice(1))
            .join(" ");
    }

    function escapeHtml(value) {
        /*
            This prevents alert messages from injecting HTML into the page.
            It is a small frontend safety measure for success/error display.
        */
        return String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }
});
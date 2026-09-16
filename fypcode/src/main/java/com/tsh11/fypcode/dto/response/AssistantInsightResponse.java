package com.tsh11.fypcode.dto.response;

import com.tsh11.fypcode.domain.investor.InsightSeverity;

//Represents one assistant-created message.

//example
//Severity: WARNING
//Message: Your equity allocation is significantly higher than the target for a Balanced investor.

//Typical severity levels:
//INFO
//WARNING
//CRITICAL

public class AssistantInsightResponse {
    private InsightSeverity severity;
    private String title;
    private String message;

    public AssistantInsightResponse() {
    }

    public AssistantInsightResponse(InsightSeverity severity, String title, String message) {
        this.severity = severity;
        this.title = title;
        this.message = message;
    }

    public InsightSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(InsightSeverity severity) {
        this.severity = severity;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

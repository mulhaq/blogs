package com.mulhaq.loganalyzer;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class AnalysisResult {
    @JsonProperty("root_cause")
    private String rootCause;
    
    @JsonProperty("affected_components")
    private List<String> affectedComponents;
    
    private String severity;
    
    @JsonProperty("fix_suggestions")
    private List<String> fixSuggestions;
    
    private String summary;

    // Constructor
    public AnalysisResult() {}

    public AnalysisResult(String rootCause, List<String> affectedComponents, 
                         String severity, List<String> fixSuggestions, String summary) {
        this.rootCause = rootCause;
        this.affectedComponents = affectedComponents;
        this.severity = severity;
        this.fixSuggestions = fixSuggestions;
        this.summary = summary;
    }

    // Getters and setters
    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public List<String> getAffectedComponents() {
        return affectedComponents;
    }

    public void setAffectedComponents(List<String> affectedComponents) {
        this.affectedComponents = affectedComponents;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public List<String> getFixSuggestions() {
        return fixSuggestions;
    }

    public void setFixSuggestions(List<String> fixSuggestions) {
        this.fixSuggestions = fixSuggestions;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    @Override
    public String toString() {
        return "AnalysisResult{" +
                "rootCause='" + rootCause + '\'' +
                ", affectedComponents=" + affectedComponents +
                ", severity='" + severity + '\'' +
                ", fixSuggestions=" + fixSuggestions +
                ", summary='" + summary + '\'' +
                '}';
    }
}

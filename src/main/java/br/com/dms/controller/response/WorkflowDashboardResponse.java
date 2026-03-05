package br.com.dms.controller.response;

import java.util.List;

public class WorkflowDashboardResponse {

    private long totalDocuments;

    private List<WorkflowStatusCountResponse> statusCounts;

    private List<WorkflowCategoryStatusCountResponse> categoryStatusCounts;

    private WorkflowSlaReviewResponse slaReview;

    private Double averageProcessingTimeHours;

    public long getTotalDocuments() {
        return totalDocuments;
    }

    public void setTotalDocuments(long totalDocuments) {
        this.totalDocuments = totalDocuments;
    }

    public List<WorkflowStatusCountResponse> getStatusCounts() {
        return statusCounts;
    }

    public void setStatusCounts(List<WorkflowStatusCountResponse> statusCounts) {
        this.statusCounts = statusCounts;
    }

    public List<WorkflowCategoryStatusCountResponse> getCategoryStatusCounts() {
        return categoryStatusCounts;
    }

    public void setCategoryStatusCounts(List<WorkflowCategoryStatusCountResponse> categoryStatusCounts) {
        this.categoryStatusCounts = categoryStatusCounts;
    }

    public WorkflowSlaReviewResponse getSlaReview() {
        return slaReview;
    }

    public void setSlaReview(WorkflowSlaReviewResponse slaReview) {
        this.slaReview = slaReview;
    }

    public Double getAverageProcessingTimeHours() {
        return averageProcessingTimeHours;
    }

    public void setAverageProcessingTimeHours(Double averageProcessingTimeHours) {
        this.averageProcessingTimeHours = averageProcessingTimeHours;
    }
}

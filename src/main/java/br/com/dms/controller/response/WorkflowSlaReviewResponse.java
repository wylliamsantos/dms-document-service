package br.com.dms.controller.response;

public class WorkflowSlaReviewResponse {

    private int targetHours;

    private long withinSla;

    private long outsideSla;

    public WorkflowSlaReviewResponse() {
    }

    public WorkflowSlaReviewResponse(int targetHours, long withinSla, long outsideSla) {
        this.targetHours = targetHours;
        this.withinSla = withinSla;
        this.outsideSla = outsideSla;
    }

    public int getTargetHours() {
        return targetHours;
    }

    public void setTargetHours(int targetHours) {
        this.targetHours = targetHours;
    }

    public long getWithinSla() {
        return withinSla;
    }

    public void setWithinSla(long withinSla) {
        this.withinSla = withinSla;
    }

    public long getOutsideSla() {
        return outsideSla;
    }

    public void setOutsideSla(long outsideSla) {
        this.outsideSla = outsideSla;
    }
}

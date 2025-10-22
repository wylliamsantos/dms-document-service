package br.com.dms.service.workflow.pojo;

import org.springframework.data.domain.Page;

public class DmsVersions {

    private Page<DmsDocumentSearchResponse> list;

    public DmsVersions() {
        super();
    }

    public DmsVersions(Page<DmsDocumentSearchResponse> list) {
        this.list = list;
    }

    public Page<DmsDocumentSearchResponse> getList() {
        return list;
    }

    public void setList(Page<DmsDocumentSearchResponse> list) {
        this.list = list;
    }
}

package br.com.dms.service.workflow.pojo.mapper;

import br.com.dms.service.workflow.pojo.DmsDocumentSearchResponse;
import br.com.dms.service.workflow.pojo.DmsEntry;
import br.com.dms.service.workflow.pojo.DmsVersions;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageImpl;

public class DmsVersionsMapper {

    private DmsVersionsMapper() {
        throw new IllegalStateException("Utility class");
    }

    public static DmsVersions of(List<DmsEntry> mergedEntries) {
        DmsVersions dmsVersions = new DmsVersions();

        var dmsDocumentSearchResponses = mergedEntries.stream().map(entry -> {
            var dmsDocument = new DmsDocumentSearchResponse();
            dmsDocument.setEntry(entry);
            return dmsDocument;
        }).collect(Collectors.toList());

        dmsVersions.setList(new PageImpl<>(dmsDocumentSearchResponses));
        return dmsVersions;
    }

}

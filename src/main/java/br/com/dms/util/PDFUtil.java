package br.com.dms.util;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class PDFUtil {

    public void addText(String text, PDFont font, int fontSize, PDPageContentStream content, PDPage page) throws IOException {
        String sanitizedText =  text.replace("\n", "");
        float leading = 1.5f * fontSize;

        PDRectangle mediabox = page.getMediaBox();
        float margin = 32;
        float bottomMargin = 55;
        float width = mediabox.getWidth() - 2 * margin;
        float startX = mediabox.getLowerLeftX() + margin;

        List<String> lines = new ArrayList<>();
        int lastSpace = -1;
        while (sanitizedText.length() > 0)
        {
            int spaceIndex = sanitizedText.indexOf(' ', lastSpace + 1);
            if (spaceIndex < 0)
                spaceIndex = sanitizedText.length();
            String subString = sanitizedText.substring(0, spaceIndex);
            float size = fontSize * font.getStringWidth(subString) / 1000;
            if (size > width)
            {
                if (lastSpace < 0)
                    lastSpace = spaceIndex;
                subString = sanitizedText.substring(0, lastSpace);
                lines.add(subString);
                sanitizedText = sanitizedText.substring(lastSpace).trim();
                lastSpace = -1;
            }
            else if (spaceIndex == sanitizedText.length())
            {
                lines.add(sanitizedText);
                sanitizedText = "";
            }
            else
            {
                lastSpace = spaceIndex;
            }
        }

        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(startX, bottomMargin + (lines.size() * leading));
        for (String line: lines)
        {
            content.showText(line);
            content.newLineAtOffset(0, -leading);
        }
        content.endText();
    }

    public void addMultilineText(String text, PDFont font, int fontSize, PDPageContentStream content, float top) throws IOException {
        float leading = 1.5f * fontSize;
        String[] textRows = text.split("\n");

        content.setFont(font, fontSize);
        content.beginText();

        boolean first = true;
        for (String textRow : textRows) {
            if (first) {
                first = false;
                content.newLineAtOffset(50, top);
            } else {
                content.newLineAtOffset(0, -leading);
            }
            content.showText(textRow);
        }
        content.endText();
    }

    public void markUpdatedElements(PDDocument doc, PDPage page) throws IOException {
        page.getCOSObject().setNeedToBeUpdated(true);
        page.getCOSObject().setNeedToBeUpdated(true);
        page.getResources().getCOSObject().setNeedToBeUpdated(true);
        doc.getDocumentCatalog().getPages().getCOSObject().setNeedToBeUpdated(true);
        doc.getDocumentCatalog().getCOSObject().setNeedToBeUpdated(true);

        COSDictionary dict = page.getCOSObject();
        while (dict.containsKey(COSName.PARENT)) {
            COSBase parent = dict.getDictionaryObject(COSName.PARENT);
            if (parent instanceof COSDictionary)     {
                dict = (COSDictionary) parent;
                dict.setNeedToBeUpdated(true);
            }
        }
        if (!Objects.isNull(page.getResources().getCOSObject().getCOSDictionary(COSName.XOBJECT))) {
            page.getResources().getCOSObject().getCOSDictionary(COSName.XOBJECT).setNeedToBeUpdated(true);
        }

        PDResources pdResources = page.getResources();
        for (COSName name : pdResources.getXObjectNames()) {
            pdResources.getXObject(name).getCOSObject().setNeedToBeUpdated(true);
        }
    }
}

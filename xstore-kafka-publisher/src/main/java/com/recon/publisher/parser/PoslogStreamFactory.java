package com.recon.publisher.parser;

import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

@Component
public class PoslogStreamFactory {

    /**
     * Detects gzip magic bytes (0x1F 0x8B) and returns
     * appropriate stream. Handles both compressed and raw XML
     * transparently — critical because Xstore compresses large
     * POSLog BLOBs silently.
     */
    public XMLStreamReader createReader(byte[] poslogBytes) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        // Security: disable external entity processing
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(
                XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

        InputStream stream = isGzipped(poslogBytes)
                ? new GZIPInputStream(new ByteArrayInputStream(poslogBytes))
                : new ByteArrayInputStream(poslogBytes);

        return factory.createXMLStreamReader(stream, "UTF-8");
    }

    public boolean isGzipped(byte[] bytes) {
        return bytes != null && bytes.length > 2
                && (bytes[0] & 0xFF) == 0x1F
                && (bytes[1] & 0xFF) == 0x8B;
    }
}
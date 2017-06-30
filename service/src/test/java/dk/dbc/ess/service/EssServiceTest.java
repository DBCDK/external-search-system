/*
 * Copyright (C) 2017 DBC A/S (http://dbc.dk/)
 *
 * This is part of dbc-ess-service
 *
 * dbc-ess-service is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * dbc-ess-service is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package dk.dbc.ess.service;

import com.codahale.metrics.Timer;
import dk.dbc.ess.service.response.EssResponse;
import dk.dbc.sru.sruresponse.SearchRetrieveResponse;
import dk.dbc.xmldiff.XmlDiff;
import dk.dbc.xmldiff.XmlDiffTextWriter;
import dk.dbc.xmldiff.XmlDiffWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.xpath.XPathExpressionException;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.stubbing.OngoingStubbing;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

/**
 *
 * @author DBC {@literal <dbc.dk>}
 */
public class EssServiceTest {

    private final XmlDiff diff;
    private final Response responseOk;
    private final Response responseError;

    public EssServiceTest() {
        this.diff = XmlDiff.builder()
                .indent(2)
                .normalize(true)
                .strip(true)
                .trim(true)
                .build();

        responseOk = mock(Response.class);
        doReturn(Response.Status.OK).when(responseOk).getStatusInfo();
        responseError = mock(Response.class);
        doReturn(Response.Status.INTERNAL_SERVER_ERROR).when(responseError).getStatusInfo();

    }

    @Test
    public void testRequestSuccess() throws Exception {
        EssService essService = mockService("base", "format", "<foo/>", "<bar/>");
        doReturn(readXMLObject(SearchRetrieveResponse.class, "/sru/response.xml")).when(essService).responseSru(any(Response.class));
        doReturn(responseOk).when(essService).requestSru(anyString(), anyString(), anyInt(), anyInt());

        Response resp = essService.request("base", "", 0, 0, "format", "T");
        EssResponse entity = (EssResponse) resp.getEntity();
        boolean equivalent = compare("/sru/expected_success.xml", writeXmlObject(entity));
        assertTrue("Documents are expected to be equivalent: ", equivalent);
    }

    @Test
    public void testRequestBadBase() throws Exception {
        EssService essService = mockService("base", "format", "<foo/>", "<bar/>");
        doReturn(readXMLObject(SearchRetrieveResponse.class, "/sru/response.xml")).when(essService).responseSru(any(Response.class));
        doReturn(responseOk).when(essService).requestSru(anyString(), anyString(), anyInt(), anyInt());

        Response resp = essService.request("badbase", "", 0, 0, "format", null);
        assertNotEquals("Not success", 200, resp.getStatus());
    }

    @Test
    public void testRequestBadFormat() throws Exception {
        EssService essService = mockService("base", "format", "<foo/>", "<bar/>");
        doReturn(readXMLObject(SearchRetrieveResponse.class, "/sru/response.xml")).when(essService).responseSru(any(Response.class));
        doReturn(responseOk).when(essService).requestSru(anyString(), anyString(), anyInt(), anyInt());

        Response resp = essService.request("base", "", 0, 0, "badformat", null);
        assertNotEquals("Not success", 200, resp.getStatus());
    }

    @Test
    public void testRequestBadEscape() throws Exception {
        EssService essService = mockService("base", "format", "<foo/>", "<bar/>");
        doReturn(readXMLObject(SearchRetrieveResponse.class, "/sru/response_bad_escape.xml")).when(essService).responseSru(any(Response.class));
        doReturn(responseOk).when(essService).requestSru(anyString(), anyString(), anyInt(), anyInt());

        Response resp = essService.request("base", "", 0, 0, "format", "T");
        assertEquals("Success", 200, resp.getStatus());
        EssResponse entity = (EssResponse) resp.getEntity();
        String actual = writeXmlObject(entity);
        boolean equivalent = compare("/sru/expected_bad_escape.xml", actual);
        assertTrue("Documents are expected to be equivalent: ", equivalent);
    }

    private EssService mockService(String bases, String formats, String... docs) throws ExecutionException, InterruptedException {
        Timer timer = mockTimer();
        EssService essService = mock(EssService.class);
        essService.client = null;
        essService.timerRequest = timer;
        essService.timerSruRequest = timer;
        essService.timerSruReadResponse = timer;
        essService.executor = mockExecutorService();
        essService.formatting = makeFormatting(docs);
        essService.knownBases = Arrays.asList(bases.split(","));
        essService.knownFormats = Arrays.asList(formats.split(","));
        doCallRealMethod().when(essService).request(anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString());
        doCallRealMethod().when(essService).serverError(anyString());
        doCallRealMethod().when(essService).buildResponse(any(SearchRetrieveResponse.class), anyString(), anyString(), anyString());
        return essService;
    }

    private boolean compare(String expected, String actual) throws SAXException, IOException, XPathExpressionException {
        XmlDiffWriter writer;
        if (System.getProperty("test") == null) {
            writer = new XmlDiffTextWriter("\u001b[4m", "\u001b[0m", "\u001b[1m", "\u001b[0m", "\u001b[3m", "\u001b[0m");
        } else {
            writer = new XmlDiffTextWriter("\u00bb-", "-\u00ab", "\u00bb+", "+\u00ab", "\u00bb?", "?\u00ab");
        }
        try (InputStream left = getClass().getResourceAsStream(expected) ;
             InputStream right = new ByteArrayInputStream(actual.getBytes(StandardCharsets.UTF_8))) {
            boolean equivalent = diff.compare(left, right, writer);
            System.out.println(writer);
            return equivalent;
        }
    }

    private Formatting makeFormatting(String... xmls) {
        Formatting formatting = mock(Formatting.class);
        doCallRealMethod().when(formatting).formattingError(anyString());
        OngoingStubbing<Callable<Element>> stub = when(formatting.formattingCall(any(Element.class), anyString(), anyString(), anyString()));
        for (String xml : xmls) {
            stub = stub.then(i -> (Callable<Element>) ()-> stringToXMLObject(xml));
        }
        return formatting;
    }

    private Timer mockTimer() {
        Timer timer = mock(Timer.class);
        doReturn(mock(Timer.Context.class)).when(timer).time();
        return timer;
    }

    private ExecutorService mockExecutorService() {
        ExecutorService executor = mock(ExecutorService.class);
        when(executor.submit(any(Callable.class)))
                .thenAnswer(i -> {
                    Callable<?> callable = (Callable<?>) i.getArguments()[0];
                    Object ret = callable.call();
                    Future future = mock(Future.class);
                    doReturn(ret).when(future).get();
                    return future;
                });
        return executor;
    }

    private static Element stringToXMLObject(String xml) {
        try (InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            return XmlTools.newDocumentBuilder().parse(is).getDocumentElement();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private <T> T readXMLObject(Class<? extends T> t, String resource) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(t);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        return (T) unmarshaller.unmarshal(getClass().getResource(resource));
    }

    private static <T> String writeXmlObject(T obj) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(obj.getClass());
        Marshaller marshaller = jaxbContext.createMarshaller();
        StringWriter writer = new StringWriter();
        marshaller.marshal(obj, writer);
        return writer.toString();
    }
}

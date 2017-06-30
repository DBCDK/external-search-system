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

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import dk.dbc.sru.sruresponse.Record;
import dk.dbc.sru.sruresponse.Records;
import dk.dbc.sru.sruresponse.SearchRetrieveResponse;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import dk.dbc.ess.service.response.EssResponse;
import dk.dbc.sru.sruresponse.RecordXMLEscapingDefinition;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.validation.constraints.NotNull;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Invocation;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 *
 * @author DBC {@literal <dbc.dk>}
 */
@Path("/")
public class EssService {

    private static final Logger log = LoggerFactory.getLogger(EssService.class);

    Client client;
    Collection<String> knownFormats;
    Collection<String> knownBases;
    String sruTargetUrl;
    Formatting formatting;

    ExecutorService executor;
    Timer timerSruRequest;
    Timer timerSruReadResponse;
    Timer timerRequest;

    public EssService(Settings settings, MetricRegistry metrics, Client client) {
        this.client = client;

        this.knownFormats = settings.getFormats();
        this.knownBases = settings.getBases();
        this.sruTargetUrl = settings.getMetaProxyUrl();

        this.executor = Executors.newCachedThreadPool();
        this.formatting = new Formatting(settings, metrics, client);

        this.timerSruRequest = mkTimer(metrics, "sruRequest");
        this.timerSruReadResponse = mkTimer(metrics, "sruReadResponse");
        this.timerRequest = mkTimer(metrics, "Request");
    }

    private Timer mkTimer(MetricRegistry metrics, String name) {
        return metrics.timer(getClass().getCanonicalName() + "#" + name);
    }

    @GET
    public Response request(@QueryParam("base") @NotNull String base,
                            @QueryParam("query") @NotNull String query,
                            @QueryParam("start") Integer start,
                            @QueryParam("rows") Integer rows,
                            @QueryParam("format") @NotNull String format,
                            @QueryParam("trackingId") String trackingId) {
        if (start == null) {
            start = 1;
        }
        if (rows == null) {
            rows = 10;
        }
        if (trackingId == null || trackingId.isEmpty()) {
            trackingId = UUID.randomUUID().toString();
        }
        if (!knownBases.contains(base)) {
            return serverError("Unknown base requested");
        }
        if (!knownFormats.contains(format)) {
            return serverError("Unknown output requested");
        }
        log.info("base: " + base + "; format: " + format +
                 "; start: " + start + "; rows: " + rows +
                 "; trackingId: " + trackingId + "; query: " + query);

        try (Timer.Context timer = timerRequest.time()) {
            Response response = requestSru(base, query, start, rows);

            if (!response.getStatusInfo().equals(Response.Status.OK)) {
                log.error("Search failed with http code: " + response.getStatusInfo() + " for: " + trackingId);
                return serverError("Internal Server Error");
            }

            SearchRetrieveResponse sru = responseSru(response);

            return buildResponse(sru, format, base + ":", trackingId);
        } catch (Exception ex) {
            log.error("Error Processing Response: " + ex.getMessage() + " for: " + trackingId);
            log.debug("Error Processing Response:", ex);
        }
        return serverError("Internal Server Error");
    }

    Response buildResponse(SearchRetrieveResponse sru, String output, String idPrefix, String trackingId) throws InterruptedException, ExecutionException {
        EssResponse essResponse = new EssResponse();
        essResponse.hits = sru.getNumberOfRecords();
        essResponse.records = new ArrayList<>();
        essResponse.trackingId = trackingId;
        Long hits = sru.getNumberOfRecords();
        log.debug("hits = " + hits);
        Records records = sru.getRecords();
        if (records != null) {
            List<Record> recordList = records.getRecords();
            List<Future<Element>> futures = new ArrayList<>(recordList.size());
            for (Record record : recordList) {
                Future<Element> future;
                RecordXMLEscapingDefinition esc = record.getRecordXMLEscaping();
                log.debug("esc = " + esc);
                if (esc != RecordXMLEscapingDefinition.XML) {
                    log.error("Expected xml escaped record in response got: " + record.getRecordXMLEscaping());
                    future = executor.submit(formatting.formattingError("Internal Server Error"));
                } else {
                    List<Object> content = record.getRecordData().getContent();
                    if (content.size() == 1) {
                        Object object = content.get(0);
                        if (object instanceof Element) {
                            String remoteId = null;
                            Element e = (Element) object;
                            for (Node child = e.getFirstChild() ; child != null ; child = child.getNextSibling()) {
                                if (child.getNodeType() == Node.ELEMENT_NODE &&
                                    "controlfield".equals(child.getLocalName())) {
                                    NamedNodeMap attrs = child.getAttributes();
                                    Node tag = attrs.getNamedItem("tag");
                                    if (tag != null &&
                                        "001".equals(tag.getNodeValue())) {
                                        Node id = child.getFirstChild();
                                        if (id.getNodeType() == Node.TEXT_NODE) {
                                            remoteId = idPrefix + id.getNodeValue();
                                            log.debug("remoteId = " + remoteId);
                                        }
                                        break;
                                    }
                                }
                            }
                            if (remoteId == null) {
                                remoteId = idPrefix + UUID.randomUUID().toString();
                            }
                            future = executor.submit(formatting.formattingCall(e, output, remoteId, trackingId));
                        } else {
                            log.error("Not of type xml: " + object.getClass().getCanonicalName() + " should not happen.");
                            future = executor.submit(formatting.formattingError("Internal Server Error"));
                        }
                    } else {
                        log.error("Expected 1 record in response, got: " + content.size());
                        for (Object object : content) {
                            log.debug("Types: " + object.getClass().getCanonicalName());
                        }
                        future = executor.submit(formatting.formattingError("Internal Server Error"));
                    }
                }
                futures.add(future);
            }
            for (Future<Element> future : futures) {
                essResponse.records.add(future.get());
            }
        }
        return Response.ok(essResponse, MediaType.APPLICATION_XML_TYPE).build();
    }

    Response requestSru(String base, String query, Integer start, Integer stepvalue) throws Exception {
        Invocation invocation = client
                .target(sruTargetUrl)
                .path(base)
                .queryParam("query", query)
                .queryParam("startRecord", start)
                .queryParam("maximumRecords", stepvalue)
                .request(MediaType.APPLICATION_XML_TYPE)
                .buildGet();
        return timerSruRequest.time(() -> invocation.invoke());
    }

    SearchRetrieveResponse responseSru(Response response) throws Exception {
        return timerSruReadResponse.time(
                () -> response.readEntity(SearchRetrieveResponse.class));
    }

    Response serverError(String message) {
        return Response.serverError().entity(message).build();
    }

}

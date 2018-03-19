package dk.dbc.ess.service;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import dk.dbc.ess.service.response.EssResponse;
import dk.dbc.ess.service.response.HowRuResponse;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.w3c.dom.Element;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class EssServiceIT {
    private static final String CONFIG_PATH = ResourceHelpers.resourceFilePath("config_test.yaml");
    private EssConfiguration conf;
    private Client client;
    private EssService essService;

    private final int readTimeout = 1500;              // ms
    private final int fixedDelay  = readTimeout + 500; // ms

    @Rule
    public WireMockRule wireMockRule = ((Supplier<WireMockRule>)()-> {
        WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());
        wireMockRule.start();
        return wireMockRule;
    }).get();

    @Rule
    public final DropwizardAppRule<EssConfiguration> dropWizzardRule=new DropwizardAppRule<>(EssApplication.class, CONFIG_PATH,
                    ConfigOverride.config("settings.metaProxyUrl", "http://localhost:" + wireMockRule.port() + "/"),
                    ConfigOverride.config("settings.openFormatUrl", "http://localhost:" + wireMockRule.port() + "/"));
    
    @Before
    public void setUp() {
        conf = dropWizzardRule.getConfiguration();
        client = new JerseyClientBuilder(dropWizzardRule.getEnvironment())
                .using(conf.getJerseyClient()).build(UUID.randomUUID().toString() ).property(ClientProperties.READ_TIMEOUT,readTimeout);

        essService = new EssService(conf.getSettings(), dropWizzardRule.getEnvironment().metrics(), client );
    }

    @Test
    public void essServiceBaseFoundTest() throws Exception {
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        Response result = essService.requestSru("bibsys", "horse", 1, 1);
        assertEquals(200, result.getStatus());
    }

    @Test
    public void essServiceBaseInternalErrorTest() throws Exception {
        stubFor(get(urlMatching("/bibsys.*"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("")));

        Response result = essService.requestSru("bibsys", "horse", 1, 1);
        assertEquals(500, result.getStatus());
    }

    @Test
    public void essServiceBaseNotFoundTest() throws Exception {
        stubFor(get(urlMatching(".*dog.*"))
                .willReturn(aResponse()
                        .withStatus(404)));

        Response result = essService.requestSru("bibsys", "dog", 1, 1);
        assertEquals(404, result.getStatus());
    }

    @Test
    public void bibsysRespondingOKTest() throws Exception {
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingXPath("/fr:formatRequest")
                        .withXPathNamespace("fr","http://oss.dbc.dk/ns/openformat"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml;charset=UTF-8")
                        .withBodyFile("open_format_horse_response.xml")));
        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=", dropWizzardRule.getLocalPort()))
                .request()
                .get();
        EssResponse r = response.readEntity(EssResponse.class);
        assertEquals(200, response.getStatus());
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
    }


    @Test
    public void openFormat404Test() throws Exception {
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        // Ensures request to open format is a proper format request, and returns a 404
        stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingXPath("/fr:formatRequest")
                        .withXPathNamespace("fr","http://oss.dbc.dk/ns/openformat"))
                .willReturn(aResponse()
                        .withHeader("Content-Type","text/xml;charset=UTF-8")
                        .withBodyFile("open_format_horse_response.xml")
                        .withStatus(404)));
        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=", dropWizzardRule.getLocalPort()))
                .request()
                .get();
        EssResponse r = response.readEntity(EssResponse.class);
        assertEquals(200, response.getStatus());
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertEquals("error",e.getTagName());
        assertEquals("message",e.getFirstChild().getNodeName());
    }

    @Test
    public void openFormatConnectionFailed() {
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withHeader("Connection","Keep-Alive")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        // Ensures request to open format is a proper format request, and makes a connection reset
        stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingXPath("/fr:formatRequest")
                        .withXPathNamespace("fr","http://oss.dbc.dk/ns/openformat"))
                .willReturn(aResponse()
                        .withFault(Fault.CONNECTION_RESET_BY_PEER)));
        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=", dropWizzardRule.getLocalPort()))
                .request()
                .get();
        EssResponse r = response.readEntity(EssResponse.class);
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertEquals("error", e.getTagName());
        assertEquals("message", e.getFirstChild().getNodeName());
    }

    @Test
    public void openFormatConnectionTimeout(){
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        // Stubbing request to open format, with delay that would trigger a socket timeout response
        stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingXPath("/fr:formatRequest")
                        .withXPathNamespace("fr","http://oss.dbc.dk/ns/openformat"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml;charset=UTF-8")
                        .withBodyFile("open_format_horse_response.xml")
                        .withFixedDelay(fixedDelay)));
        // In this response, open format response is delayed by 2s, making the socket time out
        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=", dropWizzardRule.getLocalPort()))
                .request()
                .get();
        EssResponse r = response.readEntity(EssResponse.class);
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertEquals("error",e.getTagName());
        assertEquals("message",e.getFirstChild().getNodeName());
    }

    @Test
    public void openFormatEmptyResponse(){
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        // Stubbing request to open format, with empty body to ensure it does not crash the service
        stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingXPath("/fr:formatRequest")
                        .withXPathNamespace("fr","http://oss.dbc.dk/ns/openformat"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml;charset=UTF-8")
                        .withFault(Fault.EMPTY_RESPONSE)));

        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=", dropWizzardRule.getLocalPort()))
                .request()
                .get();
        EssResponse r = response.readEntity(EssResponse.class);
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertEquals("error",e.getTagName());
        assertEquals("message",e.getFirstChild().getNodeName());
    }

    @Test
    public void openFormatFormatErrorResponse(){
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        // Stubbing request to open format, with empty body to ensure it does not crash the service
        stubFor(post(urlEqualTo("/"))
                .withRequestBody(matchingXPath("/fr:formatRequest")
                        .withXPathNamespace("fr","http://oss.dbc.dk/ns/openformat"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml;charset=UTF-8")
                        .withBodyFile("open_format_error_response.xml")));

        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=", dropWizzardRule.getLocalPort()))
                .request()
                .get();

        EssResponse r = response.readEntity(EssResponse.class);
        assertEquals(5800,r.hits);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertEquals("error",e.getTagName());
        assertEquals("message",e.getFirstChild().getNodeName());
    }

    @Test
    public void externalBaseNotReturningOKTest() throws Exception {
        givenThat(get(urlMatching(".*query=horse.*"))
        .willReturn(aResponse()
                .withStatus(404)
                .withBody("")));

        // Test all configured external search systems
        Set<String> bases = conf.getSettings().getBases();
        for( String base: bases) {
            Response response = client.target(
                    String.format("http://localhost:%d/api/?base=%s&query=horse&start=&rows=1&format=netpunkt_standard",
                            dropWizzardRule.getLocalPort(), base))
                    .request()
                    .get();
            assertEquals(500, response.getStatus());
        }
    }

    @Test
    public void externalBaseTimeoutTest() {
        /* Testing Read-timeout for different bases
         */
        stubFor(get(urlMatching("/.*?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("open_format_horse_response.xml")
                        .withFixedDelay(fixedDelay)));

        // Test all configured external search systems
        Set<String> bases = conf.getSettings().getBases();
        for( String base: bases) {
            Response response = client.target(
                    String.format("http://localhost:%d/api/?base=%s&query=horse&start=&rows=1&format=netpunkt_standard",
                            dropWizzardRule.getLocalPort(), base))
                    .request()
                    .get();
            assertEquals(500, response.getStatus());
        }
    }

    /*@Test
    public void formatterNotValidTest() throws Exception {
        // Stubbing request to base
        stubFor(get(urlEqualTo("/bibsys?query=horse&startRecord=1&maximumRecords=1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type","text/xml")
                        .withBodyFile("base_bibsys_horse_response.xml")));
        // TODO open format should fail, and when we know how it errors, we should error appropriately
        // TODO needs open format stub...
        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=XYZ&trackingId=", dropWizzardRule.getLocalPort()))
                .request()
                .get();
        assertEquals(500, response.getStatus());
    }*/

    @Test
    public void howRUAllOkTest() {
        /*
               metaProxyHealth  = URL: /       Returns Status: 200
               openFormatHealth = URL: /?HowRU Returns Status: 200 Body: "Gr8"
         */
        stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)));
        stubFor(get(urlEqualTo("/?HowRU"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("Gr8")));

        Response response = client.target(
                String.format("http://localhost:%d/api/howru", dropWizzardRule.getLocalPort()))
                .request()
                .get();
        assertEquals(200, response.getStatus());
        HowRuResponse result = response.readEntity(HowRuResponse.class);

        assertTrue(result.ok);
        assertEquals(null, result.message);

    }

    @Test
    public void howRUOpensearchNotOkTest() {
        /*
               metaProxyHealth  = URL: /       Returns Status: 200
               openFormatHealth = URL: /?HowRU Returns Status: 200 Body: "Gr8"
         */
        stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)));
        stubFor(get(urlEqualTo("/?HowRU"))
                .willReturn(aResponse()
                        .withStatus(500)));

        checkHealthCheck();
    }

    @Test
    public void howRUMetaproxyNotOkTest() {
        /*
               metaProxyHealth  = URL: /       Returns Status: 200
               openFormatHealth = URL: /?HowRU Returns Status: 200 Body: "Gr8"
         */
        stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(404)));
        stubFor(get(urlEqualTo("/?HowRU"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("Gr8")));

        checkHealthCheck();
    }


    private void checkHealthCheck() {
        Response response = client.target(
                String.format("http://localhost:%d/api/howru", dropWizzardRule.getLocalPort()))
                .request()
                .get();

        HowRuResponse result = response.readEntity(HowRuResponse.class);

        assertEquals(200, response.getStatus());
        assertFalse(result.ok);
        assertEquals("downstream error - check healthchecks on admin url", result.message);
    }


}

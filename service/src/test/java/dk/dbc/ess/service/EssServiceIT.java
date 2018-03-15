package dk.dbc.ess.service;

import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import dk.dbc.ess.service.response.EssResponse;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.JerseyClient;
import org.junit.*;
import org.w3c.dom.Element;


import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static junit.framework.TestCase.assertEquals;


public class EssServiceIT {
    private static final String CONFIG_PATH = ResourceHelpers.resourceFilePath("config_test.yaml");
    private EssConfiguration conf;
    private Client client;
    private EssService essService;

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
                .using(conf.getJerseyClient()).build(UUID.randomUUID().toString() ).property(ClientProperties.READ_TIMEOUT,1000);

        essService = new EssService(conf.getSettings(), dropWizzardRule.getEnvironment().metrics(), client );
    }

    @Test
    public void essServiceBaseFoundTest() throws Exception {
        /* This test is using the content of file mappings/bibsys-78a26727-2b4e-47b9-962d-9ff1e63cd597.json
           to mock a response from bibsys
        */
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
        /* This test is using the content of file mappings/-4c15f69b-1ed5-47c3-8318-59dda08fbdf9.json
           to mock a response from bibsys
        */
        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=test200", dropWizzardRule.getLocalPort()))
                .request()
                .get();
        EssResponse r = response.readEntity(EssResponse.class);
        assertEquals(200, response.getStatus());
        assertEquals(5800,r.hits);
        assertEquals("test200",r.trackingId);
        assertEquals(1,r.records.size());
    }


    @Test
    public void openFormat404Test() throws Exception {
        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=test404", dropWizzardRule.getLocalPort()))
                .request()
                .get();
        EssResponse r = response.readEntity(EssResponse.class);
        assertEquals(200, response.getStatus());
        assertEquals(5800,r.hits);
        assertEquals("test404",r.trackingId);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertEquals("error",e.getTagName());
        assertEquals("message",e.getFirstChild().getNodeName());
    }

    @Test
    public void openFormatConnectionFailed() {
        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=connection-failed", dropWizzardRule.getLocalPort()))
                .request()
                .get();
        EssResponse r = response.readEntity(EssResponse.class);
        assertEquals(5800,r.hits);
        assertEquals("connection-failed",r.trackingId);
        assertEquals(1,r.records.size());
        Element e = (Element)r.records.get(0);
        // Testing returned XML document for correct structure
        assertEquals("error", e.getTagName());
        assertEquals("message", e.getFirstChild().getNodeName());
    }

    @Test
    public void openFormatConnectionTimeout(){
        // In this response, open format response is delayed by 2s, making the socket time out
        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=connection-timeout", dropWizzardRule.getLocalPort()))
                .request()
                .get();
        EssResponse r = response.readEntity(EssResponse.class);
        assertEquals(5800,r.hits);
        assertEquals("connection-timeout",r.trackingId);
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
                    String.format("http://localhost:%d/api/?base=%s&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=test500",
                            dropWizzardRule.getLocalPort(), base))
                    .request()
                    .get();
            assertEquals(500, response.getStatus());
        }
    }

    @Test
    public void formatterNotValidTest() throws Exception {
        /* This test is using the content of file mappings/-4c15f69b-1ed5-47c3-8318-59dda08fbdf9.json
           to mock a response from bibsys.
        */
        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=XYZ&trackingId=test200", dropWizzardRule.getLocalPort()))
                .request()
                .get();
        assertEquals(500, response.getStatus());
    }

}

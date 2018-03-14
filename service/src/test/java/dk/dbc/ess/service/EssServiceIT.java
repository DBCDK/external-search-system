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
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static junit.framework.TestCase.assertEquals;


public class EssServiceIT {
    private static final String CONFIG_PATH = ResourceHelpers.resourceFilePath("config_test.yaml");
    private EssConfiguration conf;
    private Client client;
    private EssService essService;


    private final  String essHttpPort =  System.getProperty("ess.http.port", "8010");
    private final  String essHttpsPort =  System.getProperty("ess.https.port", "8011");
    private final static String wiremockHttpPort =  System.getProperty("wiremock.test-http.port", "8020");
    private final static String wiremockHttpsPort =  System.getProperty("wiremock.test-https.port", "8021");

    @ClassRule
    public final static WireMockClassRule wireMockRule = new WireMockClassRule(wireMockConfig()
            .port(Integer.parseInt(wiremockHttpPort))
            .httpsPort(Integer.parseInt(wiremockHttpsPort))
    );

    @Rule
    public WireMockClassRule instanceRule = wireMockRule;

    @ClassRule
    public final static DropwizardAppRule<EssConfiguration> dropWizzardRule = new DropwizardAppRule<>(EssApplication.class, CONFIG_PATH,
            ConfigOverride.config("settings.metaProxyUrl", "http://localhost:" + wiremockHttpPort + "/"),
            ConfigOverride.config("settings.openFormatUrl", "http://localhost:" + wiremockHttpPort + "/"));



    @Before
    public void setUp() {
        conf = dropWizzardRule.getConfiguration();
        client = new JerseyClientBuilder(dropWizzardRule.getEnvironment()).build(UUID.randomUUID().toString() );

        // Timeouts on JerseyClients is neccesary for WireMock / Dropwizzard don't run
        // into timeouts due to startup complications.
        client.property(ClientProperties.CONNECT_TIMEOUT, 1000);
        client.property(ClientProperties.READ_TIMEOUT,    1000);

        essService = new EssService(conf.getSettings(), dropWizzardRule.getEnvironment().metrics(), client );
    }

    @Test
    public void foundTest() throws Exception {
        /*stubFor(get(urlEqualTo("/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId="))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody("<response>Some content</response>")));*/

        Response result = essService.requestSru("bibsys", "horse", 1, 1);
        assertEquals(200, result.getStatus());
    }

    @Test
    public void notfoundTest() throws Exception {
        stubFor(get(urlMatching(".*dog.*"))
                .willReturn(aResponse()
                        .withStatus(404)));

        Response result = essService.requestSru("bibsys", "dog", 1, 1);
        assertEquals(404, result.getStatus());
    }

    @Test
    public void fullTest() throws Exception {
        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=test200", dropWizzardRule.getLocalPort()))
                .request()
                .get();
        EssResponse r = response.readEntity(EssResponse.class);
        System.out.println( "fulltest: wiremockHttpPort = " + wiremockHttpPort );
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
    public void openFormatConnectionFailed(){
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
        assertEquals("error",e.getTagName());
        assertEquals("message",e.getFirstChild().getNodeName());
    }
}

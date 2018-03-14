package dk.dbc.ess.service;

import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.*;


import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;

import java.util.UUID;
import java.util.function.Supplier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static junit.framework.TestCase.assertEquals;


public class EssServiceIT {
    private static final String CONFIG_PATH = ResourceHelpers.resourceFilePath("config_test.yaml");
    private Client client;
    private EssService essService;

    @Rule
    public WireMockRule wireMockRule = ((Supplier<WireMockRule>)()-> {
        WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());
        wireMockRule.start();
        return wireMockRule;
    }).get();

    @Rule
    public final DropwizardAppRule<EssConfiguration> dropWizardRule=new DropwizardAppRule<>(EssApplication.class, CONFIG_PATH,
                    ConfigOverride.config("settings.metaProxyUrl", "http://localhost:" + wireMockRule.port() + "/"),
                    ConfigOverride.config("settings.openFormatUrl", "http://localhost:" + wireMockRule.port() + "/"));
    
    @Before
    public void setUp() {
        EssConfiguration conf = dropWizardRule.getConfiguration();
        client = new JerseyClientBuilder(dropWizardRule.getEnvironment())
                .using(conf.getJerseyClient()).build(UUID.randomUUID().toString() );

        essService = new EssService(conf.getSettings(), dropWizardRule.getEnvironment().metrics(), client );
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
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=test200", dropWizardRule.getLocalPort()))
                .request()
                .get();
        assertEquals(200, response.getStatus());
    }


    @Test
    public void fullFailTest() throws Exception {
        Response response = client.target(
                String.format("http://localhost:%d/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=test200", dropWizardRule.getLocalPort()))
                .request()
                .get();
        assertEquals(200, response.getStatus());
    }
}

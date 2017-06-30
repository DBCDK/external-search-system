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

import dk.dbc.dropwizard.HealthCheckHTTPGet;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import dk.dbc.dropwizard.DbcApplication;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import java.util.regex.Pattern;
import javax.ws.rs.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author DBC {@literal <dbc.dk>}
 */
public class EssApplication extends DbcApplication<EssConfiguration> {

    private static final Logger log = LoggerFactory.getLogger(EssApplication.class);

    public static void main(String[] args) {
        try {
            EssApplication application = new EssApplication();
            application.run(args);
        } catch (Exception e) {
            log.error("Error running docker application: " + e.getMessage());
            log.debug("Error running docker application:", e);
        }
    }

    @Override
    public String getName() {
        return "Ess";
    }

    @Override
    public void initialize(Bootstrap<EssConfiguration> bootstrap) {
        super.initialize(bootstrap);
        bootstrap.addBundle(new AssetsBundle("/docroot/", "/", "index.html", "docroot"));
    }

    @Override
    public void run(EssConfiguration config, Environment env) throws Exception {

        Settings settings = config.getSettings();

        JerseyEnvironment jersey = env.jersey();
        HealthCheckRegistry health = env.healthChecks();
        MetricRegistry metrics = env.metrics();

        Client client = new JerseyClientBuilder(env)
                .using(config.getJerseyClient())
                .build(getName());

        HealthCheck metaProxyHealth = new HealthCheckHTTPGet(client, settings.getMetaProxyUrl());
        HealthCheck openFormatHealth = new HealthCheckHTTPGet(client, settings.getOpenFormatUrl() + "?HowRU", Pattern.compile("Gr8"));

        jersey.register(new EssService(settings, metrics, client));
        jersey.register(new HowRU(metaProxyHealth, openFormatHealth));

        health.register("downstream - metaproxy", metaProxyHealth);
        health.register("downstream - openformat", openFormatHealth);
    }
}

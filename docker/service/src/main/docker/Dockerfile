FROM docker.dbc.dk/dbc-dropwizard
MAINTAINER os-team

ENV KAFKA_LOG_APPNAME ess-service
ADD res/ess-service-jar/ $DROPWIZARD_HOME

LABEL \
    META_PROXY_URL="Url of metaproxy (indexdata.com) (required)" \
    OPEN_FORMAT_URL="Url of openformat service (required)" \
    FORMATS="Comma seperated list of known formats (required)" \
    MAX_PAGE_SIZE="Maximum number of documents retrieveable with a query (required)" \
    BASES="Comma seperated list of known bases (required)" \
    JERSEY_TIMEOUT="Rest client request timeout (default: '60s')" \
    JERSEY_CONNECTION_TIMEOUT="Rest client connect timeout (default '500ms')"



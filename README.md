# external-search-system

Service som faciliterer universal search, hvor resultater kan returneres i formater understøttet i Open Format.

## Kørsel

Servicen er et Drop Wizard projekt med config filen i `service/src/main/resources/config.yaml`. Følgende environment variabler er påkrævede:
 - `BASES`: Hvilke baser som servicen tillader at efterspørge meta proxyen med til universal search.
 - `FORMATS`: Hvilke ouput formater der accepteres. Skal på sigt fjernes, så Open Format bestemmer hvilke formater der afvises.
 - `META_PROXY_URL`: Endpoint for meta proxy til universal search.
 - `OPEN_AGENCY_URL`: Url til en open agency service.
 - `OPEN_FORMAT_URL`: Url til en open format service. 
 
Et docker image kan også bygges ved at køre `mvn clean package`.

## Query parametre
 - `base`: Parameter der beskriver hvilken base der søges ned i. Exsempler inkluderer: `libris` og `bibsys`.
 - `query`: Efterspørgsel, kan formuleres i cql.
 - `format`: Ønsket output format. Gives direkte videre til Open Format.
 - `rows`: Antallet af rækker der ønskes returneret. Benyttes til paging.
 - `start`: Offset for de ønskede resultater, benyttes sammen med `rows` til paging. Defaulter til 0.
 
Eksempel URL:
`http://host:port/api/?base=bibsys&query=horse&start=&rows=1&format=netpunkt_standard&trackingId=`
 
## Noter

 - Burde sende format parameter direkte videre til open-format uden at foretage nogle checks. ie. slet format godkendelse og PATH parameter halløj
 - ~~Få deployed ess~~
 - Få openformat endpoint der kan ikke fejler, deploy ess med denne
 - ~~Lav dokumentation af ess~~
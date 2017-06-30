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
package dk.dbc.ess.service.response;

import java.util.List;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author DBC {@literal <dbc.dk>}
 */
@XmlRootElement(name = "response", namespace = EssResponse.NS)
public class EssResponse {

    public static final String NS = "info:ESSv0";

    @XmlElement(required = true, namespace = NS)
    public long hits;

    @XmlElementWrapper(name = "records", required = true, namespace = NS)
    @XmlAnyElement(lax = true)
    public List<Object> records;

    @XmlElement(name = "trackingId", required = true, namespace = NS)
    public String trackingId;

}

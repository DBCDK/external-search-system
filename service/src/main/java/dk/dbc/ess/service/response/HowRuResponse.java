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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author DBC {@literal <dbc.dk>}
 */
@XmlRootElement(name = "howru")
public class HowRuResponse {

    @XmlElement(name = "ok", required = true, nillable = false)
    public boolean ok;

    @XmlElement(name = "message", required = false, nillable = true)
    public String message;

    public HowRuResponse() {
    }

    public HowRuResponse(String error) {
        this.ok = error == null || error.isEmpty();
        this.message = this.ok ? null : error;
    }

}

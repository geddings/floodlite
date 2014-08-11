/**
*    Copyright 2011, Big Switch Networks, Inc.
*    Originally created by David Erickson, Stanford University
*
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

package net.floodlightcontroller.core.web;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import org.projectfloodlight.openflow.types.DatapathId;
import org.restlet.resource.Get;

import net.floodlightcontroller.core.internal.IOFSwitchService;

/**
 * Get counters for a particular switch
 * @author readams
 */
public class SwitchCounterResource extends CounterResourceBase {
    @Get("json")
    public Map<String, Object> retrieve() {
        /*TODO @Ryan IOFSwitchService switchService =
                (IOFSwitchService)getContext().getAttributes().
                    get(IOFSwitchService.class.getCanonicalName());
        HashMap<String,Object> model = new HashMap<String,Object>();

        String switchID = (String) getRequestAttributes().get("switchId");
        String counterName = (String) getRequestAttributes().get("counterName");

        if (switchID.equalsIgnoreCase("all")) {
            getOneSwitchCounterJson(model, ICounterStoreService.CONTROLLER_NAME, counterName);
            for (DatapathId dpid : switchService.getAllSwitchDpids()) {
                switchID = dpid.toString();

                getOneSwitchCounterJson(model, switchID, counterName);
            }
        } else {
            getOneSwitchCounterJson(model, switchID, counterName);
        }
        return model;*/ return null;
    }

    protected void getOneSwitchCounterJson(Map<String, Object> model,
                                           String switchID, String counterName) {
        /*TODO @Ryan String fullCounterName = "";

        try {
            counterName = URLDecoder.decode(counterName, "UTF-8");
            fullCounterName =
                switchID + ICounterStoreService.TitleDelimitor + counterName;
        } catch (UnsupportedEncodingException e) {
            //Just leave counterTitle undecoded if there is an issue - fail silently
        }

        ICounter counter = this.counterStore.getCounter(fullCounterName);
        Map<String, Long> sample = new HashMap<String, Long> ();
        if (counter != null) {
            sample.put(counter.getCounterDate().toString(),
                       counter.getCounterValue().getLong());
            model.put(switchID, sample);
        } */
    }

}

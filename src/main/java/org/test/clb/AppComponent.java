/*
 * Copyright 2021-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.test.clb;

import org.apache.commons.collections.MultiMap;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onosproject.mastership.MastershipInfo;
import org.onosproject.mastership.MastershipStore;
import org.onosproject.net.Device;
import org.onosproject.net.Port;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.onosproject.cpman.*;

import static java.lang.String.valueOf;

import org.onosproject.cluster.NodeId;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MastershipStore mastershipStore;

    Timer timer = new Timer();

    @Activate
    protected void activate() {
        Iterable<Device> devices = deviceService.getDevices();

        ArrayList<Controller> controllers = new ArrayList<Controller>();
        //Controller Declaration
        NodeId node1 = new NodeId("172.17.0.5");
        NodeId node2 = new NodeId("172.17.0.6");
        NodeId node3 = new NodeId("172.17.0.7");
        //Assigning nodes to Array List
        controllers.add(new Controller(node1));
        controllers.add(new Controller(node2));
        controllers.add(new Controller(node3));

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                log.info("*******************************");

                //Initially set controllers load to zero
                for(Controller controller: controllers) {
                    controller.load = 0;
                }

                MultiMap multiMap = new MultiValueMap();

                for(Device d : devices){
                    List<Port> ports = deviceService.getPorts(d.id());
                    long bytes = 0;
                    for(Port port : ports){
                        PortStatistics portstat = deviceService.getDeltaStatisticsForPort(d.id(), port.number());
                        if(portstat != null) {
                            bytes += portstat.bytesReceived();
                        }
                    }
                    multiMap.put(mastershipStore.getMaster(d.id()).toString(),d.id().toString());
                    //log.info("Device ID: "+d.id().toString() + ", Delta Received: " + bytes/1024 + " KB");
                    //log.info("# "+ d.id().toString() + ": " + mastershipStore.getMaster(d.id()).toString());

                    //Getting controller load from devices
                    for(Controller controller: controllers) {
                        //log.info(String.valueOf(mastershipStore.getMaster(d.id())));
                        if(controller.nodeId.equals(mastershipStore.getMaster(d.id()))) {
                            controller.load += bytes;
                        }
                    }
                }

                for(Controller controller: controllers) {
                    //log.info("#" + controller.nodeId + ": " + multiMap.get(controller.nodeId.toString()));
                    log.info("#"+controller.nodeId+" Load: "+controller.load);
                }
            }
        };
        timer.scheduleAtFixedRate(task, 0, 3000);


    }

    @Deactivate
    protected void deactivate() {
        timer.cancel();
        log.info("Stopped");
    }

}
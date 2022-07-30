package org.test.clb;

import org.onosproject.cluster.NodeId;
import org.onosproject.net.DeviceId;

import java.util.ArrayList;

public class Controller {
    public NodeId nodeId;
    long controllerLoad;
    ArrayList<Switch> switches = new ArrayList<Switch>();
    Controller(NodeId node){
        this.nodeId = node;
    };

    void addSwitch(DeviceId deviceId, long load) {
        Switch aSwitch = new Switch(deviceId,load);
        this.switches.add(aSwitch);
    }

    ArrayList<Switch> getSwitches(){
        return this.switches;
    }
    long getControllerLoad(){
        return this.controllerLoad;
    }
}

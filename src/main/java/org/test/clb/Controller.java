package org.test.clb;

import org.onosproject.cluster.NodeId;

import java.util.ArrayList;

public class Controller {
    NodeId controllerId;
    long controllerLoad;
    ArrayList<Switch> switches = new ArrayList<Switch>();
    Controller(NodeId node){
        this.controllerId = node;
    };

    void addSwitch(String id, long load) {
        Switch aSwitch = new Switch(id,load);
        this.switches.add(aSwitch);
    }

    ArrayList<Switch> getSwitches(){
        return switches;
    }
}

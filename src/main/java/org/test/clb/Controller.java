package org.test.clb;

import org.onosproject.cluster.NodeId;

import java.util.ArrayList;

public class Controller {
    NodeId nodeId;
    long load;
    ArrayList<Switch> switches = new ArrayList<Switch>();
    Controller(NodeId node){
        this.nodeId = node;
    };

    void addSwitch(String id) {
        Switch aSwitch = new Switch(id);
        this.switches.add(aSwitch);
    }

    ArrayList<Switch> getSwitches(){
        return switches;
    }
}

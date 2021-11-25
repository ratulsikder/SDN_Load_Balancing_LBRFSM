package org.test.clb;

import org.onosproject.cluster.NodeId;

public class Controller {
    NodeId nodeId;
    long load;
    Controller(NodeId node){
        this.nodeId = node;
    };
}

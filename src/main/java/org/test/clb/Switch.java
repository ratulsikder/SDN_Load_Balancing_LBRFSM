package org.test.clb;

import org.onosproject.cluster.NodeId;
import org.onosproject.net.DeviceId;

public class Switch {
    DeviceId deviceId;
    long switchLoad;

    NodeId homeController, assignedController;

    //Temp for storing temporary switch selection data
    long temp;
    Switch(DeviceId deviceId, long switchLoad){
        //this.id = id;
        this.deviceId = deviceId;
        this.switchLoad = switchLoad;
    }
    DeviceId getDeviceId(){
        return  this.deviceId;
    }
    long getSwitchLoad(){
        return  this.switchLoad;
    }

    long getSwitchTemp(){
        return this.temp;
    }
}

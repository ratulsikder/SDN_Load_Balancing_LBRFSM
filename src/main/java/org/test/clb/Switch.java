package org.test.clb;

import org.onosproject.net.DeviceId;

public class Switch {
    DeviceId deviceId;
    long switchLoad;
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
}

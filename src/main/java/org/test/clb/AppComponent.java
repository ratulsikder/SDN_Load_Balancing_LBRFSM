package org.test.clb;

import org.apache.felix.scr.annotations.*;
import org.onosproject.cluster.NodeId;
import org.onosproject.mastership.MastershipStore;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MastershipStore mastershipStore;

    // Timer to run Monitoring Module
    Timer timer = new Timer();

    //Counter
    int iteration = 0;

    //Number of switch migrations
    int numberOfMigrations = 0;
    //CSV Header
    String CSVHeader = null;

    @Activate
    protected void activate() {
        //App Start time
        final long startTime = System.currentTimeMillis();

        //Java socket for sending data
        Client client = new Client("192.168.0.100", 5000);


        // Controller node(IP) Declaration
        NodeId node1 = new NodeId("172.17.0.5");
        NodeId node2 = new NodeId("172.17.0.6");
        NodeId node3 = new NodeId("172.17.0.7");
        int numberOfControllers = 3;

        // ArrayList to store Controller object
        ArrayList<Controller> controllers = new ArrayList<>();

        // Creating Controller instances and assigning to Array List
        controllers.add(new Controller(node1));
        controllers.add(new Controller(node2));
        controllers.add(new Controller(node3));

        //STARTING INITIAL SWITCH CONTROLLER ASSIGNMENT
        // Initial Switch-Controller Assignment Data
        ArrayList<DeviceId> node1Devices = new ArrayList<>();
        ArrayList<DeviceId> node2Devices = new ArrayList<>();
        ArrayList<DeviceId> node3Devices = new ArrayList<>();
        // Node1 (172.17.0.5) Devices
        node1Devices.add(DeviceId.deviceId("of:0000000000000014"));
        node1Devices.add(DeviceId.deviceId("of:0000000000000013"));
        node1Devices.add(DeviceId.deviceId("of:0000000000000011"));
        node1Devices.add(DeviceId.deviceId("of:0000000000000012"));
        node1Devices.add(DeviceId.deviceId("of:0000000000000016"));
        node1Devices.add(DeviceId.deviceId("of:0000000000000017"));
        node1Devices.add(DeviceId.deviceId("of:0000000000000015"));
        node1Devices.add(DeviceId.deviceId("of:0000000000000018"));
        // Node2 (172.17.0.6) Devices
        node2Devices.add(DeviceId.deviceId("of:000000000000000f"));
        node2Devices.add(DeviceId.deviceId("of:000000000000000c"));
        node2Devices.add(DeviceId.deviceId("of:000000000000000b"));
        node2Devices.add(DeviceId.deviceId("of:000000000000000d"));
        node2Devices.add(DeviceId.deviceId("of:0000000000000010"));
        node2Devices.add(DeviceId.deviceId("of:0000000000000009"));
        node2Devices.add(DeviceId.deviceId("of:000000000000000a"));
        // Node3 (172.17.0.7) Devices
        node3Devices.add(DeviceId.deviceId("of:0000000000000002"));
        node3Devices.add(DeviceId.deviceId("of:0000000000000008"));
        node3Devices.add(DeviceId.deviceId("of:0000000000000005"));
        node3Devices.add(DeviceId.deviceId("of:000000000000000e"));
        node3Devices.add(DeviceId.deviceId("of:0000000000000003"));
        node3Devices.add(DeviceId.deviceId("of:0000000000000004"));
        node3Devices.add(DeviceId.deviceId("of:0000000000000007"));
        node3Devices.add(DeviceId.deviceId("of:0000000000000006"));
        node3Devices.add(DeviceId.deviceId("of:0000000000000019"));
        node3Devices.add(DeviceId.deviceId("of:0000000000000001"));
        for (DeviceId deviceId : node1Devices) {
            controllers.get(0).addSwitch(deviceId, 0);
            try {
                mastershipStore.setMaster(node1, deviceId);
            } catch (NullPointerException nullPointerException) {
                log.info("At switch assignment -> " + nullPointerException);
            }
        }
        for (DeviceId deviceId : node2Devices) {
            controllers.get(1).addSwitch(deviceId, 0);
            try {
                mastershipStore.setMaster(node2, deviceId);
            } catch (NullPointerException nullPointerException) {
                log.info("At switch assignment -> " + nullPointerException);
            }
        }
        for (DeviceId deviceId : node3Devices) {
            controllers.get(2).addSwitch(deviceId, 0);
            try {
                mastershipStore.setMaster(node3, deviceId);
            } catch (NullPointerException nullPointerException) {
                log.info("At switch assignment -> " + nullPointerException);
            }
        }
        // Assigning home switches to controller object
        controllers.get(0).homeSwitches = node1Devices;
        controllers.get(1).homeSwitches = node2Devices;
        controllers.get(2).homeSwitches = node3Devices;

        //END INITIAL SWITCH CONTROLLER ASSIGNMENT SECTION

        // Getting all Switches(devices) of the Network
        Iterable<Device> devices = deviceService.getDevices();


        // *******Starting Monitoring Module (within TimerTask)********
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                log.info("*** Starts Reporting ***");
                // For exporting control plane data via java socket
                String CSV;

                // Initially set Controllers load and its Switches list to zero and blank
                for (Controller controller : controllers) {
                    controller.controllerLoad = 0;
                    controller.switches.clear();
                }

                // Traversing each Switch (Device) for gathering port delta statistics
                /* Port delta statistics is the difference between previous and current
                 * traffic of a port of a switch within two reporting time to the controller.
                 * The load of a controller is proportionate to the gross traffic of its
                 * connected switches(devices)
                 */
                for (Device d : devices) {
                    List<Port> ports = deviceService.getPorts(d.id());
                    long bytes = 0;
                    for (Port port : ports) {
                        PortStatistics portstat = deviceService.getDeltaStatisticsForPort(d.id(), port.number());
                        if (portstat != null) {
                            bytes += portstat.bytesReceived();
                        }
                    }

                    /*
                     * Aggregating traffic load of switches to controller and
                     * adding switch to the corresponding controller object/
                     */
                    for (Controller controller : controllers) {
                        // log.info(String.valueOf(mastershipStore.getMaster(d.id())));
                        if (controller.nodeId.equals(mastershipStore.getMaster(d.id()))) {
                            controller.controllerLoad += bytes;
                            controller.addSwitch(d.id(), bytes);
                        }
                    }
                }


                //Calculating Average Controller Load
                long averageControllerLoad;
                long temp = 0;
                for (Controller controller : controllers) {
                    temp += controller.controllerLoad;
                }
                averageControllerLoad = (long) (temp / controllers.size());

                /*
                 * Controller Overload Check
                 * Detecting Overloaded Controller
                 * Declaring Load Balancing(controller overload) Threshold
                 * Dynamic LB Threshold
                 * On heavy load, the threshold will be 1.2 the avg. load //1.1 for testing
                 */
                final long threshold = 20000;
                long loadBalancingThreshold;
                /*
                If average controller load is higher than threshold(under heavy load situation)
                the load balancing threshold will increase and dynamicLoadBalancingThreshold will be true.
                When FALSE the regular controller selection(lower loaded controller) will take place
                Otherwise controller below x% (x<100) of average load will be selected to stop excessive and unnecessary Sw.Mig.
                 */
                boolean dynamicLoadBalancingThreshold = false;
                if (averageControllerLoad < threshold) {
                    loadBalancingThreshold = threshold;
                } else {
                    // For our algorithm, the factor is 1.30; in CAMD and ISMDA it is 1.15
                    loadBalancingThreshold = (long) Math.round(averageControllerLoad * 1.30);
                    dynamicLoadBalancingThreshold = true;
                }

                //************ Overloaded controller detection *************
                Controller overloadedController = null;
                //Storing the previous load value(before mig.) for sending to CSV as Switch Migration value to plot in graph to identify switch migration point
                long overloadedControllerLoad = 0;
                //Sort Controller Arraylist wrt Controller Load(Reverse)
                //ArrayList<Controller> sortedControllers = controllers;
                Collections.sort(controllers, Comparator.comparing(Controller::getControllerLoad).reversed());
                if (controllers.get(0).controllerLoad > loadBalancingThreshold) {
                    overloadedController = controllers.get(0);
                    overloadedControllerLoad = overloadedController.controllerLoad;
                }
                try {
                    log.info("Overloaded controller: " + overloadedController.getNodeId());
                }catch (NullPointerException nullPointerException){
                    log.info("No overload");
                }

                /*
                 ************* Beginning Controller Selection Module **************
                 */
                //For tracking Controller Selection Time
                long startControllerSelectionTime = System.nanoTime();

                Controller selectedController = null;
                if (overloadedController != null) {
                    //Sort controller ascending order wrt load
                    Collections.sort(controllers, Comparator.comparing(Controller::getControllerLoad));
					/*
					Getting the least loaded controller as selected controller for migration
					Only load factor considered. Future add: Latency factor
					 */
                    /* If the min. loaded controller don't have significantly lower load (below 90% of avg. load)
                    then no migrations to reduce unnecessary load balancing (for dynamic LB threshold only)
                     */
                    //For avoiding excessive and unnecessary sw. mig.
                    // Now setting from 80% of avg ctl load to 100%
                    if (dynamicLoadBalancingThreshold) {
                        if (controllers.get(0).controllerLoad < (long) Math.round(averageControllerLoad)) {
                            selectedController = controllers.get(0);
                        }
                    } else {
                        selectedController = controllers.get(0);
                        //It's possible to set two threshold for dynamic and static LB threshold (using else-if for this block)
                    }

                }

                long controllerSelectionTime = System.nanoTime() - startControllerSelectionTime;

                //Re-sort controller objects wrt ip order(node address)
                Collections.sort(controllers, Comparator.comparing(Controller::getNodeId));

                try {
                    log.info("Selected controller: " + selectedController.getNodeId());
                }catch (NullPointerException nullPointerException){
                    // Print nothing
                }

				/*
				************************* Beginning Switch Selection Module **********************
				
				Select the suitable switch(possible higher loaded) to make the selected controller's load closer(and less than) to LB threshold.
				* Future: Optimize to avg. ctl load
				 */
                long startSwitchSelectionTime = System.nanoTime(); //Tracking switch selection time
                Switch selectedSwitch = null;
                if ((overloadedController != null) && (selectedController != null)) {
                    //Sort switch arraylist of overloaded controller object(descending)
                    //Collections.sort(overloadedController.switches, Comparator.comparing(Switch::getSwitchLoad).reversed());
                    for (Switch sw : overloadedController.switches) {
                        sw.temp = loadBalancingThreshold - (selectedController.controllerLoad + sw.switchLoad);
                    }
                    // Our algorithm selects the high loaded switch; sorting descending order
                    Collections.sort(overloadedController.switches, Comparator.comparing(Switch::getSwitchLoad).reversed());
                    for (Switch sw : overloadedController.switches) {
                        if (sw.temp > 0) {
                            selectedSwitch = sw;
                            break;
                        }
                    }
                }

                long switchSelectionTime = System.nanoTime() - startSwitchSelectionTime;

                try {
                    log.info("Selected switch: " + selectedSwitch.getDeviceId().toString());
                }catch (NullPointerException nullPointerException){
                    // Print nothing
                }


                //Print the overloaded and selected controller and selected switch
                try {
                    log.info("Overloaded Controller: " + overloadedController.nodeId.toString() + " Selected Controller: " + selectedController.nodeId.toString() +
                            " Selected Switch: " + selectedSwitch.getDeviceId().toString());
                } catch (NullPointerException exception) {
                    log.info("No overloaded controllers.");
                }

                // For testing...
                // Retrieving info of each controller and display in ONOS log
                for (Controller controller : controllers) {
                    ArrayList<Switch> switches = controller.getSwitches();
                    int numberOfSwitch = switches.size();
                    String switchId[] = new String[numberOfSwitch];
                    long switchLoad[] = new long[numberOfSwitch];
                    int i = 0;
                    for (Switch aSwitch : switches) {
                        switchId[i] = aSwitch.deviceId.toString();
                        switchLoad[i] = aSwitch.switchLoad;
                        i++;
                    }
                    log.info("* " + controller.nodeId + " Load: " + controller.controllerLoad + " Switches: "
                            + switches.size() + " -> " + Arrays.toString(switchId) + " Switch Load: " + Arrays.toString(switchLoad));
                }

                // CSV data add for sending
                CSV = Math.round(Math.subtractExact(System.currentTimeMillis(), startTime) / 1000) + "," + iteration + "," + averageControllerLoad * 0.0000076294 + ",";
                for (Controller controller : controllers) {
                    CSV += controller.controllerLoad * 0.0000076294 + ",";
                }
                //CSV Header
                CSVHeader = "Time(s),Iteration,Avg. Cont. Load,Controller 1 Load,Controller 2 Load,Controller 3 Load,";

				/*
				Starting Migration Module
				 */

                //*************************** Migration Module **************************
                //with necessary calculation

                boolean switchMigration = false;
                if ((overloadedController != null) && (selectedController != null) && (selectedSwitch != null)) {
                    //Reassigning switch
                    mastershipStore.setMaster(selectedController.nodeId, selectedSwitch.deviceId);
                    //Updating load info of controllers
                    overloadedController.controllerLoad -= selectedSwitch.switchLoad;
                    selectedController.controllerLoad += selectedSwitch.switchLoad;
                    //Removing the first element of the switch list. The first element is the switch selected and the list is sorted descending order.
                    overloadedController.switches.remove(0);
                    //Adding switch to the new controller(optional)
                    selectedController.switches.add(selectedSwitch);
                    log.info("Switch Reassigned: " + selectedSwitch.deviceId.toString() + " -> " + selectedController.nodeId.toString());
                    switchMigration = true;
                    numberOfMigrations++;
                }
                // CSV data add for sending to logging java program
                if (switchMigration) {
                    CSV += overloadedControllerLoad * 0.0000076294 + ",";
                } else {
                    CSV += "-9999,";
                }
                for (Controller controller : controllers) {
                    CSV += controller.controllerLoad * 0.0000076294 + ",";
                }
                if (selectedController != null) {
                    CSV += controllerSelectionTime + ",";
                } else
                    CSV += ",";
                if (selectedSwitch != null) {
                    CSV += switchSelectionTime + ",";
                } else
                    CSV += ",";
                CSV += numberOfMigrations + ",";
                CSV += loadBalancingThreshold * 0.0000076294 + ",";
                //Trimming the last comma of CSV
                CSV = CSV.substring(0, CSV.length() - 1);
                //CSV Header
                CSVHeader += "Sw. Migration,Controller 1 Load after Sw. Mig.,Controller 2 Load after Sw. Mig.,Controller 3 Load after Sw. Mig.,Controller Selection Time(ns)," +
                        "Switch Selection Time(ns),Number of Migrations,Load Balancing Threshold";
                //Send CSV Header once(first time only)
                if (iteration == 0) {
                    client.sendData(CSVHeader);
                }
                client.sendData(CSV);


                // For testing...(After Sw. Migration)
                // Retrieving info of each controller and display in ONOS log
                for (Controller controller : controllers) {
                    ArrayList<Switch> switches = controller.getSwitches();
                    int numberOfSwitch = switches.size();
                    String switchId[] = new String[numberOfSwitch];
                    long switchLoad[] = new long[numberOfSwitch];
                    int i = 0;
                    for (Switch aSwitch : switches) {
                        switchId[i] = aSwitch.deviceId.toString();
                        switchLoad[i] = aSwitch.switchLoad;
                        i++;
                    }
                    log.info("# " + controller.nodeId + " Load: " + controller.controllerLoad + " Switches: "
                            + switches.size() + " -> " + Arrays.toString(switchId) + " Switch Load: " + Arrays.toString(switchLoad));
                }
                iteration++;



                // ******************** Restoration Module *********************
                if (switchMigration == false) {
                    // Sorting controllers from min load to max
                    Collections.sort(controllers, Comparator.comparing(Controller::getControllerLoad));
                    ArrayList<Switch> allSwitches = new ArrayList<>();
                    for (Controller controller : controllers) {
                        allSwitches.addAll(controller.switches);
                    }

                    boolean switchReassignment = false;
                    for (Controller controller : controllers) {
                        if (controller.controllerLoad < averageControllerLoad) {
                            ArrayList<Switch> homeSwitches = new ArrayList<>();
                            // Adding home switch object to home switch from device id
                            for (DeviceId deviceId : controller.homeSwitches) {
                                for (Switch aSwitch : allSwitches) {
                                    if (deviceId.equals(aSwitch.deviceId)) {
                                        homeSwitches.add(aSwitch);
                                    }
                                }
                            }
                            //Sort home switches from low to high load
                            Collections.sort(homeSwitches, Comparator.comparing(Switch::getSwitchLoad));

                            for (Switch aSwitch : homeSwitches) {
                                // Checking whether a home switch is in the mother controller or not and then reassign if all conditions met
                                if (!mastershipStore.getMaster(aSwitch.deviceId).equals(controller.nodeId)) {
                                    // If the immigrant switch load does not exceed the parent load above average load then immigration will take place
                                    if (controller.controllerLoad + aSwitch.switchLoad < averageControllerLoad) {
                                        mastershipStore.setMaster(controller.nodeId, aSwitch.deviceId);
                                        switchReassignment = true;
                                        log.info("Switch Reassigned to Home Controller");
                                        break;
                                    }
                                }
                            }

                        }
                        if (switchReassignment) {
                            break;
                        }
                    }

                }
                //******************** END RESTORATION MODULE **********************
            }


        };
        // Timer delay 3 second; recommended 3 seconds(from ONOS documentation)
        timer.scheduleAtFixedRate(task, 0, 3000);

    }

    @Deactivate
    protected void deactivate() {
        timer.cancel();
        log.info("Stopped");
    }

}
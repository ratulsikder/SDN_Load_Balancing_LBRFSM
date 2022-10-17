package org.test.clb;

import java.util.*;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onosproject.cluster.NodeId;
import org.onosproject.mastership.MastershipStore;
import org.onosproject.net.Device;
import org.onosproject.net.Port;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        Client client = new Client("192.168.1.100", 5000);


        // Controller node(IP) Declaration
        NodeId node1 = new NodeId("172.17.0.5");
        NodeId node2 = new NodeId("172.17.0.6");
        NodeId node3 = new NodeId("172.17.0.7");

        // ArrayList to store Controller object
        ArrayList<Controller> controllers = new ArrayList<>();

        // Creating Controller instances and assigning to Array List
        controllers.add(new Controller(node1));
        controllers.add(new Controller(node2));
        controllers.add(new Controller(node3));

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
                final long threshold = 16000;
                long loadBalancingThreshold;
                /*
                If average controller load is higher than threshold(under heavy load situation)
                the load balancing threshold will increase and dynamicLoadBalancingThreshold will be true.
                When FALSE the regular controller selection(lower loaded controller) will take place
                Otherwise controller below x% (x<100) of average load will be selected to stop excessive and unnecessary Sw.Mig.
                 */
                boolean dynamicLoadBalancingThreshold = false;
                if(averageControllerLoad < threshold){
                    loadBalancingThreshold = threshold;
                }else{
                    loadBalancingThreshold = (long)Math.round(averageControllerLoad*1.20);
                    dynamicLoadBalancingThreshold = true;
                }

                //************ Overloaded controller detection *************
                Controller overloadedController = null;
                //For storing the previous load value(before mig.) for sending to CSV as Switch Migration value to plot graph to identify switch migration point
                long overloadedControllerLoad=0;
                //Sort Controller Arraylist wrt Controller Load(Reverse)
                //ArrayList<Controller> sortedControllers = controllers;
                Collections.sort(controllers, Comparator.comparing(Controller::getControllerLoad).reversed());
                if (controllers.get(0).controllerLoad > loadBalancingThreshold) {
                    overloadedController = controllers.get(0);
                    overloadedControllerLoad = overloadedController.controllerLoad;
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

                    if(dynamicLoadBalancingThreshold){
                        if(controllers.get(0).controllerLoad < (long)Math.round(averageControllerLoad*0.80)){
                            selectedController = controllers.get(0);
                        }
                    }else{
                        selectedController = controllers.get(0);
                        //It's possible to set two threshold for dynamic and static LB threshold (using else-if for this block)
                    }

                }

                long controllerSelectionTime = System.nanoTime() - startControllerSelectionTime;

                //Re-sort controller objects wrt ip order(node address)
                Collections.sort(controllers, Comparator.comparing(Controller::getNodeId));

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
                    for (Switch sw: overloadedController.switches) {
                        sw.temp = loadBalancingThreshold - (selectedController.controllerLoad + sw.switchLoad);
                    }
                    Collections.sort(overloadedController.switches, Comparator.comparing(Switch::getSwitchTemp));
                    for(Switch sw: overloadedController.switches){
                        if(sw.temp > 0){
                            selectedSwitch = sw;
                            break;
                        }
                    }
                }

                long switchSelectionTime = System.nanoTime() - startSwitchSelectionTime;


                //Print the overloaded and selected controller and selected switch
                try {
                    log.info("Overloaded Controller: " + overloadedController.nodeId.toString() + " Selected Controller: " + selectedController.nodeId.toString() +
                            " Selected Switch: " + selectedSwitch.getDeviceId().toString());
                } catch (NullPointerException exception) {
                    log.info("No overloaded controllers.");
                }

				/*
				//Test print
				for(Controller controller: controllers){
					log.info("* " + controller.nodeId + " Load: " + controller.controllerLoad + " Switch: " + controller.switches.size());
				}

				 */

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
                CSV = Math.round(Math.subtractExact(System.currentTimeMillis(), startTime)/1000) + "," + iteration +","+ averageControllerLoad + ",";
                for (Controller controller : controllers) {
                    CSV += controller.controllerLoad + ",";
                }
                //CSV Header
                CSVHeader = "Time(s),Iteration,Avg .Cont. Load,Controller 1 Load,Controller 2 Load,Controller 3 Load,";

				/*
				Starting Migration Module
				Future: Migration failure will be tracked and avoided by controller selection and switch selection module
				 */
                //For storing the previous load value(before mig.) for sending to CSV as Switch Migration value to plot graph to identify switch migration point
                /*long selectedControllerLoad=0;
                try{
                    selectedControllerLoad = selectedController.controllerLoad;
                }catch (NullPointerException nullPointerException){
                    log.info(nullPointerException.toString());
                }

                 */


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
                    numberOfMigrations ++;
                }
                // CSV data add for sending
                if(switchMigration){
                    CSV += overloadedControllerLoad + ",";
                }else{
                    CSV += "-9999,";
                }
                for (Controller controller : controllers) {
                    CSV += controller.controllerLoad + ",";
                }
                CSV += controllerSelectionTime + ",";
                CSV += switchSelectionTime + ",";
                CSV += numberOfMigrations + ",";
                CSV += loadBalancingThreshold + ",";
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


				/*
				for(Controller controller: controllers){
					log.info("# " + controller.nodeId + " Load: " + controller.controllerLoad + " Switch: " + controller.switches.size());
				}

				 */


                //Checking Controller Overload


                // Testing switch reassignment...
				/*
				boolean test = true;
				for (Controller controller : controllers) {
					ArrayList<Switch> switches = controller.getSwitches();
					if ((switches.size() > 2) && (test) && (controller.nodeId != node3)) {
						// run once per schedule
						mastershipStore.setMaster(node3, switches.get(0).deviceId);
						log.info("# Switch reassigned.");
						test = false;
					}
				}

				 */

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